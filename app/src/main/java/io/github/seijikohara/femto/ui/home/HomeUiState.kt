package io.github.seijikohara.femto.ui.home

import android.location.Location
import androidx.compose.runtime.Immutable
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.data.update.UpdateManifest
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
    // Whether the dock's Settings button carries the update dot: an update is on
    // offer and a live GPS fix shows the vehicle parked (VehicleMotion.PARKED).
    // Fail-closed: no fix, a cached fix and a network fix never count as parked,
    // because the trip speed reads zero until live GPS fixes set it.
    val updateBadge: Boolean,
    // The build the dashboard's update prompt asks about, or null: an offer
    // waiting for its first step (to download, or to install the verified
    // file), once the badge's parked rule has held for
    // UPDATE_PROMPT_PARKED_DWELL_MS without a break, that the prompt has not
    // asked about and the Updates section has not shown
    // (UpdateSettings.promptedFor, or an answer earlier in this process).
    val updatePrompt: UpdateManifest?,
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
                updateBadge = false,
                updatePrompt = null,
            )
    }
}
