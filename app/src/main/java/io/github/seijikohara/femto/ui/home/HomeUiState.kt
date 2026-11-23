package io.github.seijikohara.femto.ui.home

import android.location.Location
import androidx.compose.runtime.Immutable
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.routing.RouteProgress
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.data.weather.WeatherSnapshot

// @Immutable despite android.location.Location being a mutable Java type:
// LocationRepository emits each Location instance once and never mutates it
// afterwards, so the immutability promise holds and Compose can skip
// recomposition on reference equality.
@Immutable
internal data class HomeUiState(
    val location: Location?,
    val address: ShortAddress?,
    val weather: WeatherSnapshot?,
    val musicState: MusicCardState,
    val calendar: CalendarSnapshot?,
    val systemStatus: SystemStatus,
    val tripState: TripState,
    // Whether the dock's Settings button carries the update dot: an update is on
    // offer and a live GPS fix shows the vehicle parked (VehicleMotion.PARKED).
    // Fail-closed: no fix, a cached fix and a network fix never count as parked,
    // because the trip speed reads zero until live GPS fixes set it.
    val updateBadge: Boolean,
    // The route the map draws and the speed readout counts down (issue #389
    // phase 3), or null: none is set, or this build or map backend offers no
    // routing (routingAvailableFlow).
    val activeRoute: ActiveRouteState? = null,
    // Whether this build and map backend offer routing (routingAvailableFlow):
    // a long press on the map then picks a destination.
    val routingAvailable: Boolean = false,
) {
    companion object {
        val Initial: HomeUiState =
            HomeUiState(
                location = null,
                address = null,
                weather = null,
                musicState = MusicCardState.NeedsPermission,
                calendar = null,
                systemStatus = SystemStatus.Initial,
                tripState = TripState.Initial,
                updateBadge = false,
                activeRoute = null,
            )
    }
}

/**
 * The active route as the dashboard shows it: its [line] in travel order, what is left of it from the last fix, and
 * where an automatic reroute of it stands.
 */
internal data class ActiveRouteState(
    val line: List<RoutePoint>,
    val progress: RouteProgress,
    val reroute: RerouteStatus = RerouteStatus.NONE,
)
