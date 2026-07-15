package io.github.seijikohara.femto.ui.home

import android.location.Location
import androidx.compose.runtime.Immutable
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
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
    // Validated-internet connectivity. Drives the live map's offline->online reload
    // (see WebMapView); starts true so the initial dashboard assumes connectivity
    // until the connectivity flow reports otherwise.
    val online: Boolean,
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
                online = true,
            )
    }
}
