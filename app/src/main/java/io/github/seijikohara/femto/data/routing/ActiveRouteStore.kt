package io.github.seijikohara.femto.data.routing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Where an automatic reroute of the active route stands, for the speed readout's route row. */
internal enum class RerouteStatus {
    /** No reroute runs, and the last one, if any, replaced the route. */
    NONE,

    /** A reroute runs; the old line stays until it ends. */
    REROUTING,

    /** The last reroute needed routing data the device does not hold, and a reroute never downloads. */
    NO_DATA,

    /** The last reroute found no route; the old line stays. */
    FAILED,
}

/** One of the routes found for a session: the route, the engine's reference for it, and how long it took to find. */
internal data class RouteCandidate(
    val route: Route,
    val reference: File?,
    val computingMs: Long,
)

/**
 * The route the dashboard follows, in one session: from the destination panel's route to the next one or a clear.
 * A reroute replaces the [route] and its [reference] within the session; [destination] is the point the driver chose,
 * kept so that a reroute never asks the geocoder again, and [conditions] are those the route was found under, kept so
 * that a reroute avoids the same roads whatever the driver has picked for the next route since.
 *
 * While the driver may still choose between routes ([choosing], the choice card), [candidates] holds the routes found
 * so far, the first one first, and [selected] the one [route] and [reference] are. [finding] says whether a search for
 * another one runs, and [searchCompleted] whether the search ran to its end rather than being stopped: only then do
 * the candidates found so far stand for all there are.
 */
internal data class ActiveRoute(
    val session: Long,
    val route: Route,
    val destination: RoutePoint,
    /** The engine's record of [route], which a reroute hands back to stop where it rejoins the route; or none. */
    val reference: File?,
    val reroute: RerouteStatus = RerouteStatus.NONE,
    val conditions: RouteConditions = RouteConditions(),
    val candidates: List<RouteCandidate> = emptyList(),
    val selected: Int = 0,
    val choosing: Boolean = false,
    val finding: Boolean = false,
    val searchCompleted: Boolean = false,
)

/**
 * The route the dashboard follows: the destination panel starts a session with it, a reroute replaces it within that
 * session, and the map line and the speed readout's route row read it.
 *
 * Every change but [start], [restore] and [clear] names its session and is dropped once that session has ended, so a
 * reroute that finishes after the driver cleared the route or set a new one changes nothing. Each change is one atomic
 * update of the state, so a replace racing a clear cannot bring the old session back.
 *
 * The store holds the route in memory; [RoutePersistence] keeps it in files across a restart of the process and brings
 * it back through [restore]. The constructor stays open for JVM tests; production code shares [shared].
 */
internal class ActiveRouteStore {
    private val mutableActive = MutableStateFlow<ActiveRoute?>(null)
    private val sessions = AtomicLong()

    /** The active route, or null when there is none. */
    val active: StateFlow<ActiveRoute?> = mutableActive.asStateFlow()

    /**
     * Make [route] to [destination], found under [conditions] in [computingMs], the active route in a new session,
     * and return the session. The session opens with the choice between routes, [route] its first candidate.
     */
    fun start(
        route: Route,
        destination: RoutePoint,
        conditions: RouteConditions,
        reference: File?,
        computingMs: Long = 0L,
    ): Long =
        sessions.incrementAndGet().also { session ->
            mutableActive.value =
                ActiveRoute(
                    session = session,
                    route = route,
                    destination = destination,
                    reference = reference,
                    conditions = conditions,
                    candidates = listOf(RouteCandidate(route, reference, computingMs)),
                    choosing = true,
                )
        }

    /**
     * Make [route] to [destination], found under [conditions], the active route in a new session, as a route kept by
     * an earlier process comes back, and return the session; null, changing nothing, while a session is active: a
     * route the driver has set since the start stands. The route comes back alone, with no choice open and no search
     * for more: the other candidates were not kept. It has no reference either, which lived in the old process's
     * cache, so its first reroute is a full search.
     */
    fun restore(
        route: Route,
        destination: RoutePoint,
        conditions: RouteConditions,
    ): Long? =
        sessions.incrementAndGet().takeIf { session ->
            mutableActive.compareAndSet(
                expect = null,
                update =
                    ActiveRoute(
                        session = session,
                        route = route,
                        destination = destination,
                        reference = null,
                        conditions = conditions,
                        candidates = listOf(RouteCandidate(route, reference = null, computingMs = 0L)),
                    ),
            )
        }

    /**
     * Replace the route of [session] and clear its reroute status; false, changing nothing, once it has ended. A
     * reroute starts from where the vehicle is now, so the candidates found from the old start no longer apply and the
     * choice closes.
     */
    fun replace(
        session: Long,
        route: Route,
        reference: File?,
    ): Boolean =
        updateSession(session) {
            it.copy(
                route = route,
                reference = reference,
                reroute = RerouteStatus.NONE,
                candidates = emptyList(),
                selected = 0,
                choosing = false,
                finding = false,
                searchCompleted = false,
            )
        }

    /** Set the reroute status of [session]; false, changing nothing, once it has ended. */
    fun setStatus(
        session: Long,
        status: RerouteStatus,
    ): Boolean = updateSession(session) { it.copy(reroute = status) }

    /** Add [candidate] to the choice of [session]; false, changing nothing, once the session or its choice has ended. */
    fun addCandidate(
        session: Long,
        candidate: RouteCandidate,
    ): Boolean = updateSession(session, applies = { it.choosing }) { it.copy(candidates = it.candidates + candidate) }

    /**
     * Make candidate [index] of [session] the active route; false, changing nothing, once the session or its choice has
     * ended, or for an index it does not hold. A reroute status belonged to the route left behind.
     */
    fun select(
        session: Long,
        index: Int,
    ): Boolean =
        updateSession(session, applies = { it.choosing && index in it.candidates.indices }) {
            it.copy(
                route = it.candidates[index].route,
                reference = it.candidates[index].reference,
                selected = index,
                reroute = RerouteStatus.NONE,
            )
        }

    /** Say whether a search for another candidate of [session] runs; one only starts while the choice is open. */
    fun setFinding(
        session: Long,
        finding: Boolean,
    ): Boolean = updateSession(session, applies = { it.choosing || !finding }) { it.copy(finding = finding) }

    /**
     * Say that the search for more candidates of [session] ran to its end, so the candidates found are all there are;
     * false, changing nothing, once the session or its choice has ended. A search that was stopped says nothing of
     * the kind and only lowers [ActiveRoute.finding] ([setFinding]).
     */
    fun completeSearch(session: Long): Boolean =
        updateSession(session, applies = { it.choosing }) { it.copy(finding = false, searchCompleted = true) }

    /**
     * Close the choice of [session], keeping the selected route: the other candidates go from the session. Their
     * reference files are the candidate finder's to delete; the store deletes nothing.
     */
    fun closeChoice(session: Long): Boolean =
        updateSession(session, applies = { it.choosing }) {
            it.copy(
                candidates = listOfNotNull(it.candidates.getOrNull(it.selected)),
                selected = 0,
                choosing = false,
                finding = false,
                searchCompleted = false,
            )
        }

    /** End the session: no route is active until the next [start]. */
    fun clear() {
        mutableActive.value = null
    }

    /** End [session] alone; false, changing nothing, once another session or none is active. */
    fun clear(session: Long): Boolean {
        var cleared = false
        mutableActive.update { current ->
            cleared = current?.session == session
            if (cleared) null else current
        }
        return cleared
    }

    // Applies [change] to [session] in one step, when [applies] holds for it; whether it did.
    private inline fun updateSession(
        session: Long,
        crossinline applies: (ActiveRoute) -> Boolean = { true },
        crossinline change: (ActiveRoute) -> ActiveRoute,
    ): Boolean {
        var applied = false
        mutableActive.update { current ->
            current
                ?.takeIf { it.session == session && applies(it) }
                ?.let(change)
                .also { applied = it != null }
                ?: current
        }
        return applied
    }

    companion object {
        /** The one store the destination panel, the rerouter and the dashboard share in a process. */
        val shared: ActiveRouteStore = ActiveRouteStore()
    }
}
