package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.locale.DistanceUnit
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * Top-level dashboard layout. Two horizontal panes plus a fixed footer:
 *
 * ```
 * +-----------------------------+--------------+
 * | MapPane (weight 1.5)        | InfoPane (1) |
 * | + ClockOverlay (top-right)  |  CalendarCard|
 * | + SpeedOverlay (bottom)     |  + Weather   |
 * |                             |  MusicCard   |
 * +-----------------------------+--------------+
 * | DashboardFooter (height 80) |              |
 * +-----------------------------+--------------+
 * ```
 *
 * `enableEdgeToEdge()` lets the activity paint under the system bars; the
 * scaffold itself reserves them back with [windowInsetsPadding] so nothing
 * tap-able (the footer especially) hides behind the navigation bar.
 *
 * The scaffold owns no state of its own; everything reads from
 * [uiState] and reports back through [onAction].
 */
@Composable
internal fun DashboardScaffold(
    uiState: HomeUiState,
    is24Hour: Boolean,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
) {
    Row(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(FemtoDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MapPane(
            uiState = uiState,
            is24Hour = is24Hour,
            speedUnit = speedUnit,
            distanceUnit = distanceUnit,
            onAction = onAction,
            modifier = Modifier.weight(1.5f).fillMaxHeight(),
        )
        InfoPane(
            uiState = uiState,
            onAction = onAction,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
    DashboardFooter(
        systemStatus = uiState.systemStatus,
        onAction = onAction,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MapPane(
    uiState: HomeUiState,
    is24Hour: Boolean,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier) {
    MapPanel(
        location = uiState.location,
        mapAvailable = uiState.mapAvailable,
        onTap = { onAction(HomeAction.OpenMaps) },
        modifier = Modifier.fillMaxSize(),
    )
    ClockOverlay(
        clock = uiState.clock,
        is24Hour = is24Hour,
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
    )
    SpeedOverlay(
        location = uiState.location,
        address = uiState.address,
        speedUnit = speedUnit,
        distanceUnit = distanceUnit,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
    )
}

@Composable
private fun InfoPane(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(FemtoDimens.TopRowHeight),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CalendarCard(
            snapshot = uiState.calendar,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        WeatherCard(
            snapshot = uiState.weather,
            city = uiState.address?.locality,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
    MusicCard(
        state = uiState.musicState,
        onCommand = { command -> onAction(HomeAction.Music(command)) },
        onConnect = { onAction(HomeAction.ConnectMusicPlayer) },
        modifier = Modifier.weight(1f).fillMaxWidth(),
    )
}
