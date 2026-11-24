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

/**
 * The routes a session can offer: the first one and BRouter's alternatives after it. A later alternative recomputes
 * every one before it, so each one more costs most; the choice card holds this many side by side.
 */
internal const val CANDIDATE_COUNT = 3

/** How the last search for more routes ended, for the diagnostics report. */
internal enum class CandidateSearchEnding {
    /** It found every route it looks for. */
    ALL_FOUND,

    /** It ran to its end with fewer: an alternative failed, or repeated an earlier route's roads. */
    NO_MORE_ROUTES,

    /** It was stopped: the choice closed, the session ended, or the planner was needed for something else. */
    STOPPED,
}

/**
 * The last search for more routes: each candidate's computing time, the first route's first, and how the search ended,
 * null while it runs.
 */
internal data class CandidateSearch(
    val timesMs: List<Long>,
    val ending: CandidateSearchEnding?,
)

/**
 * Finds up to two more routes for a session once the destination panel has set its first one, under the session's
 * conditions, and adds each to the session's choice as it comes (#389 phase 3 additions). It lives in the routing data
 * layer, in a process [scope] like the rerouter, so the search goes on with the panel closed.
 *
 * Alternatives 1 and 2 are computed one after the other. One that runs mostly along an earlier candidate's roads
 * ([NEAR_DUPLICATE_SHARE]) is dropped; any result other than a route ends the search. A search that runs to its end
 * says so in the session ([ActiveRouteStore.completeSearch]), so the card can tell "no other route" from a search cut
 * short. The search stops when the choice closes, when the session ends, or on [cancel] (a reroute, or the panel's
 * next route, needs the planner). Once the choice closes, the reference files of the candidates not chosen are
 * deleted, whether the search was stopped or not. The constructor stays injectable for JVM tests; production wiring
 * goes through [get].
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

    private val mutableLastSearch = MutableStateFlow<CandidateSearch?>(null)

    /** The last search of this process, for the diagnostics report; null before the first one. */
    val lastSearch: StateFlow<CandidateSearch?> = mutableLastSearch.asStateFlow()

    /** Look for more routes for [session], from [origin], the start its first route was found from. */
    fun start(
        session: Long,
        origin: RoutePoint,
    ) {
        val active = store.active.value?.takeIf { it.session == session && it.choosing } ?: return
        synchronized(lock) {
            running?.whole?.cancel()
            mutableLastSearch.value = CandidateSearch(active.candidates.map { it.computingMs }, ending = null)
            // Every reference this search knows of, so the ones not chosen can go when the choice closes. Written by
            // the search, read by the clean-up only once the search has ended.
            val references = active.candidates.mapNotNull { it.reference }.toMutableList()
            // The search and the clean-up after it are two children of one job: [cancel] stops the search alone, and
            // the clean-up still deletes the references not chosen when the choice closes. A new session's search
            // cancels both, and the old session's files wait for the next prune.
            val whole = Job(scope.coroutineContext[Job])
            val search = scope.launch(whole) { search(active, origin, references) }
            scope.launch(whole) {
                search.join()
                deleteUnchosen(session, references)
            }
            whole.complete()
            running = RunningSearch(session, whole, search)
        }
    }

    /** Stop the search at once, so the planner is free for a reroute or a new route; the choice stays open. */
    fun cancel() {
        synchronized(lock) {
            running?.takeIf { it.search.isActive }?.let { stopped ->
                stopped.search.cancel()
                store.setFinding(stopped.session, false)
                mutableLastSearch.update { it?.copy(ending = CandidateSearchEnding.STOPPED) }
            }
        }
    }

    private suspend fun search(
        active: ActiveRoute,
        origin: RoutePoint,
        references: MutableList<File>,
    ) {
        val session = active.session
        val ending =
            if (store.setFinding(session, true)) {
                whileChoosing(session) { findAlternatives(active, origin, references) } ?: CandidateSearchEnding.STOPPED
            } else {
                CandidateSearchEnding.STOPPED
            }
        if (ending == CandidateSearchEnding.STOPPED) store.setFinding(session, false) else store.completeSearch(session)
        mutableLastSearch.update { it?.copy(ending = ending) }
    }

    // The other candidates stay on the card until it closes. Their files are deleted here rather than left to the next
    // prune, whose grace for new files would keep them.
    private suspend fun deleteUnchosen(
        session: Long,
        references: List<File>,
    ) {
        store.active
            .first { it?.session != session || !it.choosing }
            ?.takeIf { it.session == session }
            ?.let { closed ->
                references.filter { it != closed.reference }.forEach { it.delete() }
                pruneReferences(setOfNotNull(closed.reference))
            }
    }

    private suspend fun findAlternatives(
        active: ActiveRoute,
        origin: RoutePoint,
        references: MutableList<File>,
    ): CandidateSearchEnding {
        var added = 0
        for (index in 1 until CANDIDATE_COUNT) {
            val startedMs = nowMs()
            val found =
                findAlternative(active, origin, index) as? RouteResult.Found
                    ?: return CandidateSearchEnding.NO_MORE_ROUTES
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
                return CandidateSearchEnding.STOPPED
            }
            found.reference?.let { references += it }
            mutableLastSearch.update { it?.copy(timesMs = it.timesMs + candidate.computingMs) }
            added++
        }
        return if (added ==
            CANDIDATE_COUNT - 1
        ) {
            CandidateSearchEnding.ALL_FOUND
        } else {
            CandidateSearchEnding.NO_MORE_ROUTES
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
    // the planner, so it stops at once; null then, else what [block] returned.
    private suspend fun <T : Any> whileChoosing(
        session: Long,
        block: suspend () -> T,
    ): T? =
        coroutineScope {
            var result: T? = null
            val work = launch { result = block() }
            val watch =
                launch {
                    store.active.first { it?.session != session || !it.choosing }
                    work.cancel()
                }
            work.join()
            watch.cancel()
            result
        }

    private class RunningSearch(
        val session: Long,
        val whole: Job,
        val search: Job,
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
