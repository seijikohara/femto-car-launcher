package io.github.seijikohara.femto.data.routing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
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

/**
 * The route the dashboard follows, in one session: from the destination panel's route to the next one or a clear.
 * A reroute replaces the [route] and its [reference] within the session; [destination] is the point the driver chose,
 * kept so that a reroute never asks the geocoder again.
 */
internal data class ActiveRoute(
    val session: Long,
    val route: Route,
    val destination: RoutePoint,
    /** The engine's record of [route], which a reroute hands back to stop where it rejoins the route; or none. */
    val reference: File?,
    val reroute: RerouteStatus = RerouteStatus.NONE,
)

/**
 * The route the dashboard follows: the destination panel starts a session with it, a reroute replaces it within that
 * session, and the map line and the speed readout's route row read it.
 *
 * Every change but [start] and [clear] names its session and is dropped once that session has ended, so a reroute
 * that finishes after the driver cleared the route or set a new one changes nothing. Each change is one atomic update
 * of the state, so a replace racing a clear cannot bring the old session back.
 *
 * In memory only: a process restart forgets the route. A route is a preview the driver set up for this drive, and
 * one that came back after a restart could point somewhere the car has long since been. The constructor stays open for
 * JVM tests; production code shares [shared].
 */
internal class ActiveRouteStore {
    private val mutableActive = MutableStateFlow<ActiveRoute?>(null)
    private val sessions = AtomicLong()

    /** The active route, or null when there is none. */
    val active: StateFlow<ActiveRoute?> = mutableActive.asStateFlow()

    /** Make [route] to [destination] the active route in a new session, and return the session. */
    fun start(
        route: Route,
        destination: RoutePoint,
        reference: File?,
    ): Long =
        sessions.incrementAndGet().also { session ->
            mutableActive.value = ActiveRoute(session, route, destination, reference)
        }

    /** Replace the route of [session] and clear its reroute status; false, changing nothing, once it has ended. */
    fun replace(
        session: Long,
        route: Route,
        reference: File?,
    ): Boolean = updateSession(session) { it.copy(route = route, reference = reference, reroute = RerouteStatus.NONE) }

    /** Set the reroute status of [session]; false, changing nothing, once it has ended. */
    fun setStatus(
        session: Long,
        status: RerouteStatus,
    ): Boolean = updateSession(session) { it.copy(reroute = status) }

    /** End the session: no route is active until the next [start]. */
    fun clear() {
        mutableActive.value = null
    }

    private inline fun updateSession(
        session: Long,
        crossinline change: (ActiveRoute) -> ActiveRoute,
    ): Boolean =
        mutableActive
            .updateAndGet { current -> current?.takeIf { it.session == session }?.let(change) ?: current }
            ?.session == session

    companion object {
        /** The one store the destination panel, the rerouter and the dashboard share in a process. */
        val shared: ActiveRouteStore = ActiveRouteStore()
    }
}
