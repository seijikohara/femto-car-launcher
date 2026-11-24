package io.github.seijikohara.femto.data.routing

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.seijikohara.femto.data.location.LocationPreferences
import io.github.seijikohara.femto.data.location.TripAutoResetSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "RoutePersistence"

/**
 * The least time, on the boot clock, between two writes of the last-driven stamp from GPS fixes. The stamp only needs
 * to place the last drive against a park of 30 minutes or more, and a fix comes every second.
 */
internal const val LAST_DRIVEN_STAMP_INTERVAL_MS = 60_000L

/** What this process did with the route the last one kept, for the diagnostics report. */
internal enum class RouteRestoreDecision {
    /** The route is back on the map. */
    RESTORED,

    /** The vehicle was parked longer than the setting allows, by the wall clock or the first GPS fix; the files went. */
    PARKED_TOO_LONG,

    /** No route was kept, or its file could not be read. */
    NO_SNAPSHOT,

    /** The wall clock reads earlier than the last drive, or no time was kept: the first GPS fix decides. */
    WAITING_FOR_FIX,

    /** The driver set a route before the kept one could come back; the kept one went. */
    REPLACED,

    /** No time of the last drive was kept, so the first GPS fix could not tell how long the vehicle was parked. */
    UNDATED,
}

/**
 * Keeps the active route of [store] across a restart of the launcher's process: a route on the map when the process
 * ends comes back when it starts again, unless the vehicle was parked longer than the "Start a new trip automatically"
 * time ([TripAutoResetSetting], read through [parkedGapMs]; null is "Never", which keeps the route until the driver
 * clears it). That time is the only link to the trip meter: a trip reset, a trip boundary and the trip id do not matter.
 *
 * Two files hold the route ([RouteSnapshotFiles]): the snapshot of the session, and the last-driven stamp, the time of
 * the last GPS fix the dashboard received while the route was on ([onGpsFix], written at most once per
 * [LAST_DRIVEN_STAMP_INTERVAL_MS]), or the wall clock when the session's snapshot was first written.
 *
 * [start] runs one sequence: decide, restore or delete, and only then mirror the store into the files. A mirror that
 * ran from the start would see the empty store of a new process and delete the snapshot before the decision. The
 * decision measures the park from the stamp to now on the wall clock:
 * - with "Never", the route comes back;
 * - a park shorter than the setting brings the route back, and the first GPS fix of the process checks it again on GPS
 *   time: a unit whose clock starts at its last saved time would otherwise show a route days old;
 * - a longer park deletes the files;
 * - a wall clock behind the stamp (a unit that boots with an unset clock), or a snapshot without a stamp, shows nothing
 *   and keeps the files until the first GPS fix decides on GPS time.
 *
 * A restore never replaces a session the driver has started since, and a restored session is the store's own entry
 * ([ActiveRouteStore.restore]): one candidate, no choice, no reference.
 *
 * Everything after the start runs in one coroutine, one event at a time: each state of the store and each fix. So a
 * write never lands after the delete that follows it, and a write first checks that its session is still the store's,
 * which leaves the files holding the store's session or nothing. Only a change of the session, its route, its
 * destination or its conditions rewrites the snapshot; a reroute status, the candidate search and the choice card do
 * not. The constructor stays injectable for JVM tests; production wiring goes through [get].
 */
internal class RoutePersistence(
    private val store: ActiveRouteStore,
    private val files: RouteSnapshotFiles,
    private val parkedGapMs: suspend () -> Long?,
    private val scope: CoroutineScope,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val nowElapsedMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val started = AtomicBoolean()

    // Only the time of the latest fix waits: an earlier one says less about either the park or the last drive.
    private val fixes = Channel<Long>(Channel.CONFLATED)

    private val mutableDecision = MutableStateFlow<RouteRestoreDecision?>(null)

    /** What the start of this process did with the kept route; null until it has decided anything. */
    val decision: StateFlow<RouteRestoreDecision?> = mutableDecision.asStateFlow()

    // Read and written only by the one coroutine [start] launches.
    private var phase = Phase.SETTLED
    private var pending: KeptRoute? = null
    private var stampMs: Long? = null
    private var restoredSession: Long? = null
    private var mirrored: MirroredRoute? = null
    private var stampedSession: Long? = null
    private var lastFixStampElapsedMs: Long? = null

    /**
     * Run the start once per process: with [enabled] (the build offers routing), decide, restore or delete, then
     * mirror the store; without it, delete what an earlier build left and write nothing. Later calls do nothing.
     */
    fun start(enabled: Boolean) {
        if (!started.compareAndSet(false, true)) return
        scope.launch { if (enabled) run() else files.delete() }
    }

    /** A live GPS fix the dashboard received, taken at [timeEpochMs]; never blocks. */
    fun onGpsFix(timeEpochMs: Long) {
        fixes.trySend(timeEpochMs)
    }

    private suspend fun run() {
        try {
            decideAtStart()
            merge(
                store.active.map { Event.Session(it?.mirrored()) },
                fixes.receiveAsFlow().map(Event::Fix),
            ).collect { event ->
                when (event) {
                    is Event.Session -> onSession(event.route)
                    is Event.Fix -> onFix(event.timeEpochMs)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An uncaught failure would end the launcher's process, which runs the home screen. Files the store no
            // longer reaches could bring back a route the driver has cleared since, so they go.
            Log.w(TAG, "keeping the route stopped: ${e.javaClass.simpleName}")
            files.delete()
        }
    }

    private suspend fun decideAtStart() {
        val kept = files.readOrNull() ?: return discard(RouteRestoreDecision.NO_SNAPSHOT)
        stampMs = files.readStampOrNull()
        val limitMs = parkedGapMs()
        val parkedMs = stampMs?.let { nowEpochMs() - it }
        when {
            limitMs == null -> restore(kept, checkAtFirstFix = false)
            parkedMs == null || parkedMs < 0 -> waitForFix(kept)
            parkedMs < limitMs -> restore(kept, checkAtFirstFix = true)
            else -> discard(RouteRestoreDecision.PARKED_TOO_LONG)
        }
    }

    private fun restore(
        kept: KeptRoute,
        checkAtFirstFix: Boolean,
    ) {
        pending = null
        val session = store.restore(kept.route, kept.destination, kept.conditions)
        if (session == null) {
            // The driver's route stands; the mirror writes it over the kept one.
            phase = Phase.SETTLED
            mutableDecision.value = RouteRestoreDecision.REPLACED
        } else {
            restoredSession = session
            mirrored = MirroredRoute(session, kept.route, kept.destination, kept.conditions)
            stampedSession = session
            phase = if (checkAtFirstFix) Phase.CHECK_AT_FIRST_FIX else Phase.SETTLED
            mutableDecision.value = RouteRestoreDecision.RESTORED
        }
    }

    private fun waitForFix(kept: KeptRoute) {
        pending = kept
        phase = Phase.WAITING_FOR_FIX
        mutableDecision.value = RouteRestoreDecision.WAITING_FOR_FIX
    }

    private fun discard(decision: RouteRestoreDecision) {
        pending = null
        phase = Phase.SETTLED
        deleteFiles()
        mutableDecision.value = decision
    }

    private fun onSession(route: MirroredRoute?) {
        // The check at the first fix concerns the restored session only; once the driver clears it or sets another
        // route, there is nothing left to check.
        if (phase == Phase.CHECK_AT_FIRST_FIX && route?.session != restoredSession) phase = Phase.SETTLED
        // An unchanged route rewrites nothing. That covers the empty store while the kept route waits for the first fix
        // too: nothing is mirrored until a route is restored or set, so the waiting files stay.
        when {
            route == mirrored -> Unit
            route == null -> deleteFiles()
            else -> mirror(route)
        }
    }

    private fun mirror(route: MirroredRoute) {
        // A later state of the store follows this one; writing this one would only put an older session on the disk.
        if (store.active.value?.mirrored() != route) return
        // A route the driver set replaces a kept one still waiting for its fix, whether or not the write below works.
        if (phase == Phase.WAITING_FOR_FIX) {
            pending = null
            phase = Phase.SETTLED
            mutableDecision.value = RouteRestoreDecision.REPLACED
        }
        if (!files.write(KeptRoute(route.route, route.destination, route.conditions))) {
            // The file left is another session's or an older line of this one: better none than one the driver left.
            Log.w(TAG, "route snapshot not written; files deleted")
            return deleteFiles()
        }
        mirrored = route
        if (stampedSession != route.session) {
            // A new session has not been driven yet: the wall clock stands in, and the next fix replaces it at once.
            val nowMs = nowEpochMs()
            if (!files.writeStamp(nowMs)) {
                Log.w(TAG, "last-driven stamp not written; files deleted")
                return deleteFiles()
            }
            stampMs = nowMs
            stampedSession = route.session
            lastFixStampElapsedMs = null
        }
    }

    private suspend fun onFix(timeEpochMs: Long) {
        when (phase) {
            Phase.WAITING_FOR_FIX -> decideAtFirstFix(timeEpochMs)
            Phase.CHECK_AT_FIRST_FIX -> checkAtFirstFix(timeEpochMs)
            Phase.SETTLED -> Unit
        }
        stampFix(timeEpochMs)
    }

    // GPS time on both sides for a route that was driven, whose stamp came from fixes too: the wall clock that read
    // earlier than the stamp plays no part.
    private suspend fun decideAtFirstFix(timeEpochMs: Long) {
        val kept = pending ?: return
        val stamp = stampMs ?: return discard(RouteRestoreDecision.UNDATED)
        val limitMs = parkedGapMs()
        if (limitMs == null || timeEpochMs - stamp < limitMs) {
            restore(kept, checkAtFirstFix = false)
        } else {
            discard(RouteRestoreDecision.PARKED_TOO_LONG)
        }
    }

    private suspend fun checkAtFirstFix(timeEpochMs: Long) {
        phase = Phase.SETTLED
        val limitMs = parkedGapMs() ?: return
        val stamp = stampMs ?: return
        val session = restoredSession ?: return
        // Cleared only while it is still the session restored here: a route the driver set since stays.
        if (timeEpochMs - stamp >= limitMs && store.clear(session)) {
            deleteFiles()
            mutableDecision.value = RouteRestoreDecision.PARKED_TOO_LONG
        }
    }

    // Only while the route on the disk is the one on the map; a fix with no route there, or before the decision,
    // says nothing about when a route was last driven.
    private fun stampFix(timeEpochMs: Long) {
        val route = mirrored ?: return
        if (store.active.value?.session != route.session) return
        val nowMs = nowElapsedMs()
        if (lastFixStampElapsedMs?.let { nowMs - it < LAST_DRIVEN_STAMP_INTERVAL_MS } == true) return
        // A failed write leaves the older stamp, which can only make the next start judge the park longer.
        if (files.writeStamp(timeEpochMs)) stampMs = timeEpochMs
        lastFixStampElapsedMs = nowMs
    }

    private fun deleteFiles() {
        files.delete()
        mirrored = null
        stampedSession = null
        stampMs = null
    }

    private enum class Phase {
        /** The kept route waits for the first GPS fix, with nothing on the map. */
        WAITING_FOR_FIX,

        /** The kept route is back on the wall clock's word; the first GPS fix checks it again. */
        CHECK_AT_FIRST_FIX,

        /** Decided: the files follow the store. */
        SETTLED,
    }

    private sealed interface Event {
        data class Session(
            val route: MirroredRoute?,
        ) : Event

        data class Fix(
            val timeEpochMs: Long,
        ) : Event
    }

    // What of a session the snapshot keeps, so a change of anything else rewrites nothing.
    private data class MirroredRoute(
        val session: Long,
        val route: Route,
        val destination: RoutePoint,
        val conditions: RouteConditions,
    )

    private fun ActiveRoute.mirrored(): MirroredRoute = MirroredRoute(session, route, destination, conditions)

    companion object {
        @Volatile
        private var instance: RoutePersistence? = null

        fun get(context: Context): RoutePersistence =
            instance ?: synchronized(this) {
                instance ?: context.applicationContext
                    .let { app ->
                        RoutePersistence(
                            store = ActiveRouteStore.shared,
                            files = RouteSnapshotFiles(File(app.filesDir, ACTIVE_ROUTE_DIR)),
                            parkedGapMs = {
                                LocationPreferences(app)
                                    .settings
                                    .first()
                                    .tripAutoReset.parkedGapMs
                            },
                            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                        )
                    }.also { instance = it }
            }
    }
}
