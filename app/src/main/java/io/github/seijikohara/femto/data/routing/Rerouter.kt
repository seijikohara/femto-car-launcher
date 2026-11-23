package io.github.seijikohara.femto.data.routing

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * The least time between the starts of two reroutes of one session. A vehicle that drives off the road network, or
 * where no routing data lies, keeps leaving every new line; this keeps it from rerouting without end.
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
 * One reroute runs at a time, and the next in the same session starts no sooner than [REROUTE_MIN_INTERVAL_MS] after
 * the last; a new session, a route the driver just set, may reroute at once. A reroute
 * belongs to its session of [store]: it replaces the route only while that session is current, and a session that
 * ends (a clear, or a new route) cancels it, which frees the planner for the route that ended it. A found route
 * replaces the old one; one that needs data the device does not hold leaves the old line with
 * [RerouteStatus.NO_DATA], and any other failure with [RerouteStatus.FAILED].
 *
 * A reroute writes to [store] only under [lock] and only while it has not been cancelled, and whoever cancels it
 * ([onBackOnRoute], [cancel]) settles the status under the same lock. So a result that arrives after a cancel never
 * lands. The constructor stays injectable for JVM tests; production wiring goes through [get].
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

    // The last reroute started, running or not; guarded by [lock].
    private var running: RunningReroute? = null

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
            val held =
                running?.let { last ->
                    last.job.isActive || (last.session == session && now - last.startedMs < REROUTE_MIN_INTERVAL_MS)
                } == true
            if (held) return
            running =
                RunningReroute(session, now, scope.launch { whileSessionCurrent(session) { reroute(active, fix) } })
        }
    }

    /**
     * The vehicle is back on the route of [session]: a reroute for it is moot, so it stops, and whatever the row said
     * about rerouting goes.
     */
    fun onBackOnRoute(session: Long) {
        synchronized(lock) {
            running?.takeIf { it.session == session }?.job?.cancel()
            store.setStatus(session, RerouteStatus.NONE)
        }
    }

    /**
     * Stop a running reroute at once, so the planner is free for the route the driver asked for; the old line stays,
     * with nothing to say about it.
     */
    fun cancel() {
        synchronized(lock) {
            running?.takeIf { it.job.isActive }?.let { stopped ->
                stopped.job.cancel()
                store.setStatus(stopped.session, RerouteStatus.NONE)
            }
        }
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
        val job = currentCoroutineContext().job
        if (!synchronized(lock) { job.isActive && store.setStatus(session, RerouteStatus.REROUTING) }) return
        val startedMs = nowMs()
        val result =
            try {
                route(fix.point, active.destination, fix.headingDegreesOrNull(), active.reference)
            } catch (e: CancellationException) {
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
        // BRouter writes no reference for a route it merged onto the reference it was given. That reference still leads
        // to the same destination under the same profile, so it stays for the next reroute, which would otherwise need
        // a full search, one that can fail where the reference lets the search succeed.
        val keptReference = (result as? RouteResult.Found)?.let { it.reference ?: active.reference }
        // The engine can return just as the reroute is cancelled; the check under the lock drops such a result.
        val replaced =
            synchronized(lock) {
                job.isActive &&
                    when (result) {
                        is RouteResult.Found -> store.replace(session, result.route, keptReference)
                        else -> false.also { store.setStatus(session, result.failureStatus()) }
                    }
            }
        if (result is RouteResult.Found) {
            if (replaced) pruneReferences(setOfNotNull(keptReference)) else result.reference?.delete()
        }
    }

    // A reroute, the session it belongs to, and when it started on [nowMs]'s clock.
    private class RunningReroute(
        val session: Long,
        val startedMs: Long,
        val job: Job,
    )

    companion object {
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

// The status a reroute that found no route leaves: only missing data could be helped by a download, which a reroute
// never starts.
private fun RouteResult.failureStatus(): RerouteStatus =
    when (this) {
        is RouteResult.MissingTiles, RouteResult.NoRoadData -> RerouteStatus.NO_DATA
        else -> RerouteStatus.FAILED
    }

// The heading only from a fix that moves: a standing receiver's bearing is noise, and a wrong heading would turn the
// new route around.
private fun RouteFix.headingDegreesOrNull(): Int? = bearingDegrees?.takeIf { moving }?.roundToInt()
