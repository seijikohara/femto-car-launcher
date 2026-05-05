package io.github.seijikohara.femto.ui.home

import android.location.Location
import androidx.compose.runtime.Immutable
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.data.ShortAddress
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
    val nowPlaying: NowPlaying?,
    val mapAvailable: Boolean,
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
                nowPlaying = null,
                mapAvailable = false,
            )
    }
}
