package io.github.seijikohara.femto.data.routing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The route the dashboard follows: the destination panel sets it, and the map line and the speed readout's route row
 * read it.
 *
 * In memory only: a process restart forgets the route. A route is a preview the driver set up for this drive, and
 * one that came back after a restart could point somewhere the car has long since been. The constructor stays open for
 * JVM tests; production code shares [shared].
 */
internal class ActiveRouteStore {
    private val mutableRoute = MutableStateFlow<Route?>(null)

    /** The active route, or null when there is none. */
    val route: StateFlow<Route?> = mutableRoute.asStateFlow()

    fun set(route: Route) {
        mutableRoute.value = route
    }

    fun clear() {
        mutableRoute.value = null
    }

    companion object {
        /** The one store the destination panel and the dashboard share in a process. */
        val shared: ActiveRouteStore = ActiveRouteStore()
    }
}
