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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

/**
 * How far a clock may read behind the last-driven stamp and still measure the park: a span down to minus this counts as
 * no park at all. The stamp is GPS time, and a device clock that runs a little behind it (one that drifts between
 * network time syncs, at a few seconds a day) would otherwise make every quick restart wait for a fix, and in a car
 * park none may come. Five minutes covers weeks of such drift and stays well under the shortest "Start a new trip
 * automatically" time of 30 minutes, while an unset clock, off by days or years, still waits for the first fix. A span
 * in it takes the same path as a small positive span from a clock stuck at the shutdown time: the route comes back,
 * and the first fix checks the park again.
 */
internal const val CLOCK_ALLOWANCE_MS = 5L * 60 * 1_000

/** What this process did with the route the last one kept, for the diagnostics report. */
internal enum class RouteRestoreDecision {
    /** The route is back on the map. */
    RESTORED,

    /** The vehicle was parked longer than the setting allows, by the wall clock or the first GPS fix; the files went. */
    PARKED_TOO_LONG,

    /** No route was kept, or its file could not be read. */
    NO_SNAPSHOT,

    /**
     * The wall clock reads more than [CLOCK_ALLOWANCE_MS] earlier than the last drive, or no time was kept: the first
     * GPS fix decides.
     */
    WAITING_FOR_FIX,

    /** The driver set a route before the kept one could come back; the kept one went. */
    REPLACED,

    /** No time of the last drive was kept, so the first GPS fix could not tell how long the vehicle was parked. */
    UNDATED,

    /**
     * The first GPS fix read more than [CLOCK_ALLOWANCE_MS] earlier than the last drive, so its clock could not tell
     * how long the vehicle was parked either; the files went.
     */
    FIX_BEFORE_LAST_DRIVE,

    /** The setting that keeps a route across a restart is off; any files went. */
    SWITCHED_OFF,
}

/**
 * Keeps the active route of [store] across a restart of the launcher's process: a route on the map when the process
 * ends comes back when it starts again, while the "Bring the route back after a restart" setting ([keep]) is on and
 * unless the vehicle was parked longer than the "Start a new trip automatically" time ([TripAutoResetSetting], read
 * through [parkedGapMs]; null is "Never", which keeps the route until the driver clears it). That time is the only link
 * to the trip meter: a trip reset, a trip boundary and the trip id do not matter.
 *
 * The setting is read at the start, before the decision; off, the files go and nothing comes back
 * ([RouteRestoreDecision.SWITCHED_OFF]). Switched off later, the files go at once, and a kept route still waiting for
 * its first fix goes with them; nothing writes them until the setting is on again, which writes the route on the map at
 * once. The route on the map stays either way, and the check of a restored route at the first GPS fix still runs: it
 * clears the route from the map and does not depend on the files.
 *
 * Two files hold the route ([RouteSnapshotFiles]): the snapshot of the session, and the last-driven stamp, the time of
 * the last GPS fix the dashboard received while the route was on ([onGpsFix], written at most once per
 * [LAST_DRIVEN_STAMP_INTERVAL_MS]), or the wall clock when a session the keeper has no time for was first written. A
 * session keeps its stamp through every rewrite of its snapshot, a reroute or a choice of candidate included.
 *
 * [start] runs one sequence: decide, restore or delete, and only then mirror the store into the files. A mirror that
 * ran from the start would see the empty store of a new process and delete the snapshot before the decision. The
 * decision measures the park from the stamp to now on the wall clock:
 * - with "Never", the route comes back;
 * - a park shorter than the setting brings the route back, and the first GPS fix of the process checks it again on GPS
 *   time: a unit whose clock starts at its last saved time would otherwise show a route days old; a wall clock up to
 *   [CLOCK_ALLOWANCE_MS] behind the stamp counts as no park and takes this path;
 * - a longer park deletes the files;
 * - a wall clock further behind the stamp (a unit that boots with an unset clock), or a snapshot without a stamp, shows
 *   nothing and keeps the files until the first GPS fix decides on GPS time; a fix that reads more than the allowance
 *   before the stamp cannot measure the park either, and the files go.
 *
 * A restore never replaces a session the driver has started since, and a restored session is the store's own entry
 * ([ActiveRouteStore.restore]): one candidate, no choice, no reference. A route the driver sets before the kept one
 * comes back deletes the kept one's files at once, even if it is cleared again before its own write.
 *
 * Everything after the start runs in one coroutine, one event at a time: each state of the store, each fix and each
 * change of the setting. So a write never lands after the delete that follows it, and a write first checks that its
 * session is still the store's, which leaves the files holding the store's session or nothing. Only a change of the
 * session, its route, its destination or its conditions rewrites the snapshot; a reroute status, the candidate search
 * and the choice card do not. A write that fails deletes the files, and the next GPS fixes write the session again, at
 * most once per [LAST_DRIVEN_STAMP_INTERVAL_MS]. The constructor stays injectable for JVM tests; production wiring goes
 * through [get].
 */
internal class RoutePersistence(
    private val store: ActiveRouteStore,
    private val files: RouteSnapshotFiles,
    private val parkedGapMs: suspend () -> Long?,
    private val scope: CoroutineScope,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val nowElapsedMs: () -> Long = SystemClock::elapsedRealtime,
    // The "Bring the route back after a restart" setting.
    private val keep: Flow<Boolean> = flowOf(true),
) {
    private val started = AtomicBoolean()

    // Only the time of the latest fix waits: an earlier one says less about either the park or the last drive.
    private val fixes = Channel<Long>(Channel.CONFLATED)

    private val mutableDecision = MutableStateFlow<RouteRestoreDecision?>(null)

    /** What the start of this process did with the kept route; null until it has decided anything. */
    val decision: StateFlow<RouteRestoreDecision?> = mutableDecision.asStateFlow()

    // Read and written only by the one coroutine [start] launches. [keeping] takes the stored setting before its first
    // use.
    private var keeping = true
    private var phase = Phase.SETTLED
    private var pending: KeptRoute? = null
    private var restoredSession: Long? = null
    private var mirrored: MirroredRoute? = null

    // The last-driven time of [stampedSession] (the kept route's while it waits), and whether the stamp file holds it.
    private var stampMs: Long? = null
    private var stampedSession: Long? = null
    private var stampOnDisk = false
    private var lastFixStampElapsedMs: Long? = null

    // When, on the boot clock, the last write failed, which sets the pace of its retries.
    private var lastFailedWriteElapsedMs: Long? = null

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
            // Read before the decision, which runs before any event below: a decision on the default would bring back
            // a route the driver asked not to keep.
            keeping = keep.first()
            if (keeping) decideAtStart() else discard(RouteRestoreDecision.SWITCHED_OFF)
            merge(
                store.active.map { Event.Session(it?.mirrored()) },
                fixes.receiveAsFlow().map(Event::Fix),
                keep.distinctUntilChanged().map(Event::Keep),
            ).collect { event ->
                when (event) {
                    is Event.Session -> onSession(event.route)
                    is Event.Fix -> onFix(event.timeEpochMs)
                    is Event.Keep -> onKeep(event.on)
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

    // A span down to minus the allowance counts as no park: it takes the path of a small positive span.
    private suspend fun decideAtStart() {
        val kept = files.readOrNull() ?: return discard(RouteRestoreDecision.NO_SNAPSHOT)
        stampMs = files.readStampOrNull()
        stampOnDisk = stampMs != null
        val limitMs = parkedGapMs()
        val parkedMs = stampMs?.let { nowEpochMs() - it }
        when {
            limitMs == null -> restore(kept, checkAtFirstFix = false)
            parkedMs == null || parkedMs < -CLOCK_ALLOWANCE_MS -> waitForFix(kept)
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
            replaceKept()
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
        stampMs = null
        mutableDecision.value = decision
    }

    // The driver set a route before the kept one came back. The kept route's files go at once rather than with the
    // driver route's write: that route may be cleared again before its write, which then never comes.
    private fun replaceKept() = discard(RouteRestoreDecision.REPLACED)

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

    // The first value repeats the one read at the start, and the settings store emits again for a change of any other
    // setting: only a change of this one acts. Off, the files go at once, and a kept route still waiting for its fix
    // goes with them; on again, the route on the map is written without waiting for it to change.
    private fun onKeep(on: Boolean) {
        if (on == keeping) return
        keeping = on
        when {
            on -> store.active.value?.let { mirror(it.mirrored()) }
            phase == Phase.WAITING_FOR_FIX -> discard(RouteRestoreDecision.SWITCHED_OFF)
            else -> deleteFiles()
        }
    }

    private fun mirror(route: MirroredRoute) {
        // Off, the files went when the setting was switched off, and nothing writes them until it is on again. Every
        // write passes here, a route change's and a fix's retry alike, so this one check covers them all.
        if (!keeping) return
        // A route the driver set replaces a kept one still waiting for its fix, even one that is gone again by now.
        if (phase == Phase.WAITING_FOR_FIX) replaceKept()
        // A later state of the store follows this one; writing this one would only put an older session on the disk.
        if (store.active.value?.mirrored() != route) return
        // The file a failed write leaves is another session's or an older line of this one: better none than one the
        // driver left. A later fix writes the route again (retryMirror).
        val written = files.write(KeptRoute(route.route, route.destination, route.conditions))
        if (!written) return writeFailed("route snapshot")
        mirrored = route
        if (!stampSession(route.session)) writeFailed("last-driven stamp")
    }

    private fun writeFailed(what: String) {
        Log.w(TAG, "$what not written; files deleted")
        deleteFiles()
        lastFailedWriteElapsedMs = nowElapsedMs()
    }

    // A write that failed, on a disk full for a moment while a download runs, leaves the route on the map without files,
    // and the store may not change again for an hour: a fix writes it again, at most once per
    // [LAST_DRIVEN_STAMP_INTERVAL_MS], and the session's own time goes back with it (stampSession). Whether it did.
    private fun retryMirror(): Boolean {
        if (mirrored != null) return false
        val route = store.active.value?.mirrored() ?: return false
        val nowMs = nowElapsedMs()
        if (lastFailedWriteElapsedMs?.let { nowMs - it < LAST_DRIVEN_STAMP_INTERVAL_MS } == true) return false
        mirror(route)
        return true
    }

    // Leave the stamp of [session] on the disk; whether it is there. A session keeps its time through every rewrite of
    // its snapshot: the time a restored route was last driven, or the time of the session's last fix, is what the next
    // check of a park measures from, and the clock's time would hide the park. Only a session the keeper has no time
    // for yet, one not driven since it was set, takes the wall clock, and its next fix replaces that at once.
    private fun stampSession(session: Long): Boolean {
        if (stampedSession != session) {
            stampedSession = session
            stampMs = nowEpochMs()
            stampOnDisk = false
            lastFixStampElapsedMs = null
        }
        // A restored session with no time kept ("Never" and no stamp) stays without one.
        val stamp = stampMs ?: return true
        if (!stampOnDisk) stampOnDisk = files.writeStamp(stamp)
        return stampOnDisk
    }

    // A fix that retried a write has written the stamp already. While the kept route waits, a fix only decides.
    private suspend fun onFix(timeEpochMs: Long) {
        val retried = phase != Phase.WAITING_FOR_FIX && retryMirror()
        when (phase) {
            Phase.WAITING_FOR_FIX -> decideAtFirstFix(timeEpochMs)
            Phase.CHECK_AT_FIRST_FIX -> checkAtFirstFix(timeEpochMs)
            Phase.SETTLED -> Unit
        }
        if (!retried) stampFix(timeEpochMs)
    }

    // GPS time on both sides for a route that was driven, whose stamp came from fixes too: the wall clock that read
    // earlier than the stamp plays no part. A fix that reads earlier than the stamp by more than the allowance says its
    // clock is no better (a bridged or mock provider can stamp fixes with the device's clock), so it cannot measure the
    // park either, and the route goes rather than come back whatever the park was. With "Never", no time rule applies.
    private suspend fun decideAtFirstFix(timeEpochMs: Long) {
        val kept = pending ?: return
        val limitMs = parkedGapMs() ?: return restore(kept, checkAtFirstFix = false)
        val stamp = stampMs ?: return discard(RouteRestoreDecision.UNDATED)
        val parkedMs = timeEpochMs - stamp
        when {
            parkedMs < -CLOCK_ALLOWANCE_MS -> discard(RouteRestoreDecision.FIX_BEFORE_LAST_DRIVE)
            parkedMs < limitMs -> restore(kept, checkAtFirstFix = false)
            else -> discard(RouteRestoreDecision.PARKED_TOO_LONG)
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
        if (files.writeStamp(timeEpochMs)) {
            stampMs = timeEpochMs
            stampOnDisk = true
        }
        lastFixStampElapsedMs = nowMs
    }

    // The session's time stays in memory, so a later write of the same session puts it back (stampSession).
    private fun deleteFiles() {
        files.delete()
        mirrored = null
        stampOnDisk = false
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

        data class Keep(
            val on: Boolean,
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
                            keep = NavigationPreferences(app).settings.map { it.restoreRouteAfterRestart },
                        )
                    }.also { instance = it }
            }
    }
}
