package io.github.seijikohara.femto.ui.home

import android.location.Location
import androidx.compose.runtime.Immutable
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.CalendarSnapshot
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.data.MusicCardState
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.SystemStatus
import io.github.seijikohara.femto.data.WeatherSnapshot
import java.time.LocalDate
import java.time.LocalTime

@Immutable
internal data class HomeUiState(
    val isLoading: Boolean,
    val apps: List<AppEntry>,
    val clock: ClockTick,
    val location: Location?,
    val address: ShortAddress?,
    val weather: WeatherSnapshot?,
    val musicState: MusicCardState,
    val mapAvailable: Boolean,
    val calendar: CalendarSnapshot?,
    val systemStatus: SystemStatus,
) {
    companion object {
        val Initial: HomeUiState =
            HomeUiState(
                isLoading = true,
                apps = emptyList(),
                clock = ClockTick(LocalTime.of(0, 0), LocalDate.now()),
                location = null,
                address = null,
                weather = null,
                musicState = MusicCardState.NeedsPermission,
                mapAvailable = false,
                calendar = null,
                systemStatus = SystemStatus.Initial,
            )
    }
}
