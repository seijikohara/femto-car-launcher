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
import io.github.seijikohara.femto.data.MusicCardState
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
    musicState: MusicCardState,
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
            // Clock and weather both take their intrinsic heights — the cards
            // contain a fixed number of rows and look unbalanced when stretched.
            // Music absorbs the remaining space so the playing state's progress
            // bar and centered transport row sit at a stable vertical position.
            ClockPanel(
                tick = clock,
                is24Hour = is24Hour,
                sunrise = weather?.sunrise,
                sunset = weather?.sunset,
            )
            WeatherPanel(
                snapshot = weather,
                unit = temperatureUnit,
                speedUnit = speedUnit,
                is24Hour = is24Hour,
            )
            MusicPanel(
                state = musicState,
                onCommand = onMusicCommand,
                onConnect = onConnectMusic,
                modifier = Modifier.weight(1f),
            )
        }
    }
    AppsBar(onOpenDrawer = onOpenDrawer, onShortcut = onShortcut)
}
