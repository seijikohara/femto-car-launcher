package io.github.seijikohara.femto.ui.home

import android.location.Location
import androidx.compose.runtime.Immutable
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.routing.OnLinePoint
import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.routing.RouteProgress
import io.github.seijikohara.femto.data.routing.RouteRowTime
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
 * where an automatic reroute of it stands. While the driver may still choose between routes, [choice] is the choice
 * card and [alternativeLines] the routes not selected, which the map draws under the route; otherwise null and empty.
 * [rowTime] is the time the speed readout's route row shows beside the distance left (Settings → Navigation).
 * [vehicleOnLine] is the point of the line the map draws the vehicle at while it drives along the route (the point the
 * route follow matched the fix to, stamped with that fix), or null where the map draws it at the fix: a fix too far
 * from the line, or the setting off (Settings → Navigation). Only the map reads it; everything else keeps the fix.
 * [turnCard] is what the turn card over the map shows (issue #389 phase 4), or null for no card ([turnCardStateOrNull]).
 */
internal data class ActiveRouteState(
    val line: List<RoutePoint>,
    val progress: RouteProgress,
    val reroute: RerouteStatus = RerouteStatus.NONE,
    val choice: RouteChoiceState? = null,
    val alternativeLines: List<List<RoutePoint>> = emptyList(),
    val rowTime: RouteRowTime = RouteRowTime.ARRIVAL,
    val vehicleOnLine: OnLinePoint? = null,
    val turnCard: TurnCardState? = null,
)

/**
 * The choice card of route [session]: the routes found so far, the first one first, the [selected] one, whether a
 * search for another one runs, and whether the search ran to its end, so the routes found are all there are; a search
 * that was stopped never says there is no other route.
 */
internal data class RouteChoiceState(
    val session: Long,
    val candidates: List<CandidateSummary>,
    val selected: Int,
    val finding: Boolean,
    val searchCompleted: Boolean,
) {
    /** Whether the card says no other route was found: the search ended, and only the first route came of it. */
    val noOtherRoute: Boolean
        get() = searchCompleted && !finding && candidates.size == 1
}

/** One route on the choice card, as the card names it: its length and the profile's travel time. */
internal data class CandidateSummary(
    val distanceMeters: Int,
    val durationSeconds: Int,
)
