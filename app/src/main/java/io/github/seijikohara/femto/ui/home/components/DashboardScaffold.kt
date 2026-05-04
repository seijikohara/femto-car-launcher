package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.WeatherSnapshot
import io.github.seijikohara.femto.ui.locale.DistanceUnit
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens

@Composable
internal fun DashboardScaffold(
    clock: ClockTick,
    is24Hour: Boolean,
    weather: WeatherSnapshot?,
    temperatureUnit: String,
    address: ShortAddress?,
    location: Location?,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    mapAvailable: Boolean,
    nowPlaying: NowPlaying?,
    onMapTap: () -> Unit,
    onMusicCommand: (MusicCommand) -> Unit,
    onConnectMusic: () -> Unit,
    onOpenDrawer: () -> Unit,
    onShortcut: (AppsBarShortcut) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxSize()
            .padding(FemtoDimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MapPanel(
            location = location,
            address = address,
            mapAvailable = mapAvailable,
            speedUnit = speedUnit,
            distanceUnit = distanceUnit,
            onTap = onMapTap,
            modifier = Modifier
                .weight(1.6f)
                .fillMaxHeight(),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClockPanel(
                tick = clock,
                is24Hour = is24Hour,
                modifier = Modifier.weight(1f),
            )
            WeatherPanel(
                snapshot = weather,
                unit = temperatureUnit,
                modifier = Modifier.weight(1f),
            )
            MusicPanel(
                nowPlaying = nowPlaying,
                onCommand = onMusicCommand,
                onConnect = onConnectMusic,
                modifier = Modifier.weight(1f),
            )
        }
    }
    AppsBar(onOpenDrawer = onOpenDrawer, onShortcut = onShortcut)
}
