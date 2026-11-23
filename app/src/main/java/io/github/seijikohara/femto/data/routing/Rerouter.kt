package io.github.seijikohara.femto.data.routing

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * The least time between the starts of two reroutes. A vehicle that drives off the road network, or where no routing
 * data lies, keeps leaving every new line; this keeps it from rerouting without end.
 */
internal const val REROUTE_MIN_INTERVAL_MS = 30_000L

/**
 * A reroute's own time limit, half the planner's: the vehicle moves on while it runs, so a late answer starts from
 * where the vehicle no longer is. With a reference, a reroute takes a fraction of a full search.
 */
internal const val REROUTE_TIME_LIMIT_MS = 30_000L

/** The reroutes of this process, for the diagnostics report. */
internal data class RerouteStats(
    val count: Int,
    /** How the last reroute ended and how long it took; null before the first. */
    val last: RouteRun?,
    /** Whether the last reroute handed the engine a reference; null before the first. */
    val usedReference: Boolean?,
)

/**
 * Routes again when the vehicle leaves the active route: from the fix where it left to the same destination, with the
 * route on screen as the reference and the vehicle's heading. It lives in the routing data layer, in a process
 * [scope], so a reroute runs with the destination panel closed and outlives a route change on the dashboard. Its
 * [route] reads only the routing data on the device and never the network.
 *
 * One reroute runs at a time, and the next starts no sooner than [REROUTE_MIN_INTERVAL_MS] after the last. A reroute
 * belongs to its session of [store]: it replaces the route only while that session is current, and a session that
 * ends (a clear, or a new route) cancels it, which frees the planner for the route that ended it. A found route
 * replaces the old one; one that needs data the device does not hold leaves the old line with
 * [RerouteStatus.NO_DATA], and any other failure with [RerouteStatus.FAILED]. The constructor stays injectable for JVM
 * tests; production wiring goes through [get].
 */
internal class Rerouter(
    private val route: suspend (
        origin: RoutePoint,
        destination: RoutePoint,
        headingDegrees: Int?,
        reference: File?,
    ) -> RouteResult,
    private val store: ActiveRouteStore,
    private val scope: CoroutineScope,
    private val pruneReferences: (Collection<File>) -> Unit,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val lock = Any()

    // Guarded by [lock].
    private var running: Job? = null
    private var lastStartMs: Long? = null

    private val mutableStats = MutableStateFlow(RerouteStats(count = 0, last = null, usedReference = null))

    /** The reroutes of this process so far. */
    val stats: StateFlow<RerouteStats> = mutableStats.asStateFlow()

    /** Route again from [fix], where the vehicle left the route of [session]. */
    fun onLeftRoute(
        session: Long,
        fix: RouteFix,
    ) {
        val active = store.active.value?.takeIf { it.session == session } ?: return
        synchronized(lock) {
            val now = nowMs()
            if (running?.isActive == true || lastStartMs?.let { now - it < REROUTE_MIN_INTERVAL_MS } == true) return
            lastStartMs = now
            running = scope.launch { whileSessionCurrent(session) { reroute(active, fix) } }
        }
    }

    /** The vehicle is back on the route of [session]: a reroute that found nothing no longer needs saying. */
    fun onBackOnRoute(session: Long) {
        store.active.value
            ?.takeIf { it.session == session && it.reroute in SETTLED_FAILURES }
            ?.let { store.setStatus(session, RerouteStatus.NONE) }
    }

    /** Stop a running reroute at once, so the planner is free for the route the driver asked for. */
    fun cancel() {
        synchronized(lock) { running?.cancel() }
    }

    // A reroute for a session that has ended has nothing left to replace, and it would hold the planner that the next
    // route needs, so it stops as soon as its session does.
    private suspend fun whileSessionCurrent(
        session: Long,
        block: suspend () -> Unit,
    ) = coroutineScope {
        val work = launch { block() }
        val watch =
            launch {
                store.active.first { it?.session != session }
                work.cancel()
            }
        work.join()
        watch.cancel()
    }

    private suspend fun reroute(
        active: ActiveRoute,
        fix: RouteFix,
    ) {
        val session = active.session
        if (!store.setStatus(session, RerouteStatus.REROUTING)) return
        val startedMs = nowMs()
        val result =
            try {
                route(fix.point, active.destination, fix.headingDegreesOrNull(), active.reference)
            } catch (e: CancellationException) {
                // The old line stays, and nothing about it is wrong.
                store.setStatus(session, RerouteStatus.NONE)
                throw e
            } catch (e: Exception) {
                // An uncaught failure here would end the launcher's process, which runs the home screen.
                RouteResult.Failed(e.message ?: e.toString())
            }
        mutableStats.update { stats ->
            RerouteStats(
                count = stats.count + 1,
                last = RouteRun(result.runOutcome(), computingMs = nowMs() - startedMs),
                usedReference = active.reference != null,
            )
        }
        when (result) {
            is RouteResult.Found -> {
                if (store.replace(session, result.route, result.reference)) {
                    pruneReferences(setOfNotNull(result.reference))
                } else {
                    result.reference?.delete()
                }
            }

            is RouteResult.MissingTiles, RouteResult.NoRoadData -> {
                store.setStatus(session, RerouteStatus.NO_DATA)
            }

            RouteResult.DataVersionMismatch, RouteResult.TimedOut, is RouteResult.Failed -> {
                store.setStatus(session, RerouteStatus.FAILED)
            }
        }
    }

    companion object {
        // The statuses a finished reroute leaves; REROUTING belongs to the reroute still running.
        private val SETTLED_FAILURES = setOf(RerouteStatus.NO_DATA, RerouteStatus.FAILED)

        @Volatile
        private var instance: Rerouter? = null

        fun get(context: Context): Rerouter =
            instance ?: synchronized(this) {
                instance ?: RoutePlanner
                    .get(context)
                    .let { planner ->
                        Rerouter(
                            route = { origin, destination, heading, reference ->
                                planner.route(
                                    origin = origin,
                                    destination = destination,
                                    startHeadingDegrees = heading,
                                    reference = reference,
                                    timeLimitMs = REROUTE_TIME_LIMIT_MS,
                                )
                            },
                            store = ActiveRouteStore.shared,
                            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                            pruneReferences = planner::pruneReferences,
                        )
                    }.also { instance = it }
            }
    }
}

// The heading only from a fix that moves: a standing receiver's bearing is noise, and a wrong heading would turn the
// new route around.
private fun RouteFix.headingDegreesOrNull(): Int? = bearingDegrees?.takeIf { moving }?.roundToInt()
