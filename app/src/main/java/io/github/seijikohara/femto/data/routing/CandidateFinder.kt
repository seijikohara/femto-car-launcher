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

/**
 * The share of a route's points on an earlier candidate's line from which it counts as nearly the same road. BRouter's
 * alternatives steer away from the roads before them, yet a short trip or a single road can leave them little choice;
 * such a route would only repeat a row of the card.
 */
internal const val NEAR_DUPLICATE_SHARE = 0.8

// The first route and two alternatives: a later alternative recomputes every one before it, so a third costs most.
private const val CANDIDATE_COUNT = 3

/**
 * Finds up to two more routes for a session once the destination panel has set its first one, under the session's
 * conditions, and adds each to the session's choice as it comes (#389 phase 3 additions). It lives in the routing data
 * layer, in a process [scope] like the rerouter, so the search goes on with the panel closed.
 *
 * Alternatives 1 and 2 are computed one after the other. One that runs mostly along an earlier candidate's roads
 * ([NEAR_DUPLICATE_SHARE]) is dropped; any result other than a route ends the search. The search stops when the choice
 * closes, when the session ends, or on [cancel] (a reroute, or the panel's next route, needs the planner). Once the
 * choice closes, the reference files of the candidates not chosen are deleted. The constructor stays injectable for
 * JVM tests; production wiring goes through [get].
 */
internal class CandidateFinder(
    private val route: suspend (
        origin: RoutePoint,
        destination: RoutePoint,
        conditions: RouteConditions,
        alternativeIndex: Int,
    ) -> RouteResult,
    private val store: ActiveRouteStore,
    private val scope: CoroutineScope,
    private val pruneReferences: (Collection<File>) -> Unit,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val lock = Any()

    // The last search started, running or not, and its session; guarded by [lock].
    private var running: RunningSearch? = null

    private val mutableTimes = MutableStateFlow<List<Long>>(emptyList())

    /** The computing time of each candidate of the last search, the first route's first, for the diagnostics report. */
    val candidateTimes: StateFlow<List<Long>> = mutableTimes.asStateFlow()

    /** Look for more routes for [session], from [origin], the start its first route was found from. */
    fun start(
        session: Long,
        origin: RoutePoint,
    ) {
        val active = store.active.value?.takeIf { it.session == session && it.choosing } ?: return
        synchronized(lock) {
            running?.job?.cancel()
            mutableTimes.value = active.candidates.map { it.computingMs }
            running = RunningSearch(session, scope.launch { find(active, origin) })
        }
    }

    /** Stop the search at once, so the planner is free for a reroute or a new route; the choice stays open. */
    fun cancel() {
        synchronized(lock) {
            running?.takeIf { it.job.isActive }?.let { stopped ->
                stopped.job.cancel()
                store.setFinding(stopped.session, false)
            }
        }
    }

    private suspend fun find(
        active: ActiveRoute,
        origin: RoutePoint,
    ) {
        val session = active.session
        // Every reference this search knows of, so the ones not chosen can go when the choice closes.
        val references = active.candidates.mapNotNull { it.reference }.toMutableList()
        if (store.setFinding(session, true)) {
            whileChoosing(session) { search(active, origin, references) }
            store.setFinding(session, false)
        }
        // The other candidates stay on the card until it closes. Their files are deleted here rather than left to the
        // next prune, whose grace for new files would keep them.
        store.active
            .first { it?.session != session || !it.choosing }
            ?.takeIf { it.session == session }
            ?.let { closed ->
                references.filter { it != closed.reference }.forEach { it.delete() }
                pruneReferences(setOfNotNull(closed.reference))
            }
    }

    private suspend fun search(
        active: ActiveRoute,
        origin: RoutePoint,
        references: MutableList<File>,
    ) {
        for (index in 1 until CANDIDATE_COUNT) {
            val startedMs = nowMs()
            val found = findAlternative(active, origin, index) as? RouteResult.Found ?: return
            val earlier = store.active.value
                ?.takeIf { it.session == active.session }
                ?.candidates
                .orEmpty()
            if (earlier.any { found.route.overlapShare(it.route) >= NEAR_DUPLICATE_SHARE }) {
                found.reference?.delete()
                continue
            }
            val candidate = RouteCandidate(found.route, found.reference, computingMs = nowMs() - startedMs)
            if (!store.addCandidate(active.session, candidate)) {
                found.reference?.delete()
                return
            }
            found.reference?.let { references += it }
            mutableTimes.update { it + candidate.computingMs }
        }
    }

    private suspend fun findAlternative(
        active: ActiveRoute,
        origin: RoutePoint,
        index: Int,
    ): RouteResult =
        try {
            route(origin, active.destination, active.conditions, index)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An uncaught failure here would end the launcher's process, which runs the home screen.
            RouteResult.Failed(e.message ?: e.toString())
        }

    // A search for a choice that has closed, or a session that has ended, finds nothing anyone will see, and it holds
    // the planner, so it stops at once.
    private suspend fun whileChoosing(
        session: Long,
        block: suspend () -> Unit,
    ) = coroutineScope {
        val work = launch { block() }
        val watch =
            launch {
                store.active.first { it?.session != session || !it.choosing }
                work.cancel()
            }
        work.join()
        watch.cancel()
    }

    private class RunningSearch(
        val session: Long,
        val job: Job,
    )

    companion object {
        @Volatile
        private var instance: CandidateFinder? = null

        fun get(context: Context): CandidateFinder =
            instance ?: synchronized(this) {
                instance ?: RoutePlanner
                    .get(context)
                    .let { planner ->
                        CandidateFinder(
                            route = { origin, destination, conditions, alternativeIndex ->
                                planner.route(
                                    origin = origin,
                                    destination = destination,
                                    conditions = conditions,
                                    alternativeIndex = alternativeIndex,
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

// The share of this route's points that lie on [other]'s line. BRouter places both on the same road nodes, so a
// shared stretch of road gives the same points exactly.
private fun Route.overlapShare(other: Route): Double =
    other.points.toHashSet().let { shared ->
        if (points.isEmpty()) 0.0 else points.count { it in shared }.toDouble() / points.size
    }
