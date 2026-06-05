package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.MapRefreshSetting
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * Top-level dashboard layout. Two panes plus a fixed footer, arranged
 * responsively from the available viewport rather than a fixed canvas:
 *
 * ```
 * Landscape (wide)                    Portrait (tall)
 * +-------------------+-----------+    +-----------------------+
 * | MapPane (1.5)     | InfoPane  |    | MapPane (1.1)         |
 * |  + ClockOverlay   |  Calendar |    |  + Clock + Speed      |
 * |  + SpeedOverlay   |  + Weather|    +-----------------------+
 * |                   |  MusicCard|    | InfoPane (1)          |
 * +-------------------+-----------+    |  Calendar + Weather   |
 * | DashboardFooter               |    |  MusicCard            |
 * +-------------------------------+    +-----------------------+
 *                                      | DashboardFooter       |
 *                                      +-----------------------+
 * ```
 *
 * A [BoxWithConstraints] reads the available width/height and (a) tightens the
 * outer / inter-pane spacing on a compact viewport and (b) stacks the panes
 * vertically when the screen is taller than wide. The info pane distributes its
 * height between the calendar/weather row and the music card with **weights**
 * (no fixed row height), so neither is starved on a short head unit. All of this
 * keys off geometry, never a specific device — the launcher stays
 * resolution-agnostic.
 *
 * `enableEdgeToEdge()` lets the activity paint under the system bars; the
 * scaffold reserves them back with [windowInsetsPadding] so nothing tap-able
 * (the footer especially) hides behind the navigation bar.
 *
 * The scaffold owns no state of its own; everything reads from [uiState] and
 * reports back through [onAction].
 */
@Composable
internal fun DashboardScaffold(
    uiState: HomeUiState,
    is24Hour: Boolean,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    mapRefresh: MapRefreshSetting,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth(),
    ) {
        val compact = maxHeight < CompactHeightBreakpoint || maxWidth < CompactWidthBreakpoint
        val portrait = maxHeight > maxWidth
        val screenPadding = if (compact) CompactScreenPadding else FemtoDimens.ScreenPadding
        val paneGap = if (compact) CompactPaneGap else FemtoDimens.PaneGap
        if (portrait) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(screenPadding),
                verticalArrangement = Arrangement.spacedBy(paneGap),
            ) {
                MapPane(
                    uiState = uiState,
                    is24Hour = is24Hour,
                    speedUnit = speedUnit,
                    mapRefresh = mapRefresh,
                    onAction = onAction,
                    modifier = Modifier.weight(MAP_PANE_PORTRAIT_WEIGHT).fillMaxWidth(),
                )
                InfoPane(
                    uiState = uiState,
                    temperatureUnit = temperatureUnit,
                    speedUnit = speedUnit,
                    paneGap = paneGap,
                    onAction = onAction,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(screenPadding),
                horizontalArrangement = Arrangement.spacedBy(paneGap),
            ) {
                MapPane(
                    uiState = uiState,
                    is24Hour = is24Hour,
                    speedUnit = speedUnit,
                    mapRefresh = mapRefresh,
                    onAction = onAction,
                    modifier = Modifier.weight(MAP_PANE_LANDSCAPE_WEIGHT).fillMaxHeight(),
                )
                InfoPane(
                    uiState = uiState,
                    temperatureUnit = temperatureUnit,
                    speedUnit = speedUnit,
                    paneGap = paneGap,
                    onAction = onAction,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
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
    mapRefresh: MapRefreshSetting,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier) {
    MapPanel(
        location = uiState.location,
        mapRefresh = mapRefresh,
        onTap = { onAction(HomeAction.OpenMaps) },
        modifier = Modifier.fillMaxSize(),
    )
    ClockOverlay(
        is24Hour = is24Hour,
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
    )
    SpeedOverlay(
        location = uiState.location,
        address = uiState.address,
        tripState = uiState.tripState,
        speedUnit = speedUnit,
        onReset = { onAction(HomeAction.ResetTrip) },
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
    )
}

@Composable
private fun InfoPane(
    uiState: HomeUiState,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
    paneGap: Dp,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(paneGap),
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(TOP_ROW_WEIGHT),
        horizontalArrangement = Arrangement.spacedBy(paneGap),
    ) {
        CalendarCard(
            snapshot = uiState.calendar,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        WeatherCard(
            snapshot = uiState.weather,
            city = uiState.address?.locality,
            temperatureUnit = temperatureUnit,
            speedUnit = speedUnit,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
    MusicCard(
        state = uiState.musicState,
        onCommand = { command -> onAction(HomeAction.Music(command)) },
        onConnect = { onAction(HomeAction.ConnectMusicPlayer) },
        onLaunchSource = { packageName -> onAction(HomeAction.LaunchMusicSource(packageName)) },
        modifier = Modifier.weight(MUSIC_CARD_WEIGHT).fillMaxWidth(),
    )
}

// Below either breakpoint the dashboard switches to its compact spacing. The
// thresholds are deliberately coarse: they separate small / short head units
// from large in-dash panels without targeting any one resolution. The height
// threshold sits above the common ~512 dp 5:3 projection so those short
// landscapes take the tighter spacing, leaving more room for the info-pane cards.
private val CompactHeightBreakpoint: Dp = 560.dp
private val CompactWidthBreakpoint: Dp = 600.dp

// Compact outer / inter-pane spacing; the comfortable values are the FemtoDimens
// defaults used on large panels.
private val CompactScreenPadding: Dp = 12.dp
private val CompactPaneGap: Dp = 10.dp

// Pane weights. The map is the dominant surface in landscape; in portrait it
// sits a little taller than the info pane. The info pane splits its height
// roughly evenly between the calendar/weather row and the music card so neither
// is starved on a short head unit — a ~512 dp-tall 5:3 projection clipped the
// calendar/weather row when the music card was weighted heavier.
private const val MAP_PANE_LANDSCAPE_WEIGHT = 1.4f
private const val MAP_PANE_PORTRAIT_WEIGHT = 1.1f
private const val TOP_ROW_WEIGHT = 1f
private const val MUSIC_CARD_WEIGHT = 1.05f

// Responsive previews. HomeUiState.Initial renders the empty/loading states (no
// network/GL in a preview), which is enough to lock the responsive arrangement
// across head-unit geometries. These dimensions are test cases, not targets.
@PreviewLightDark
@Preview(name = "Dashboard - 16:9", widthDp = 640, heightDp = 360)
@Composable
private fun DashboardScaffoldLandscapePreview() {
    FemtoTheme {
        DashboardScaffold(
            uiState = HomeUiState.Initial,
            is24Hour = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapRefresh = MapRefreshSetting.RESPONSIVE,
            onAction = {},
        )
    }
}

@PreviewLightDark
@Preview(name = "Dashboard - 8:3 ultra-wide", widthDp = 640, heightDp = 240)
@Composable
private fun DashboardScaffoldUltraWidePreview() {
    FemtoTheme {
        DashboardScaffold(
            uiState = HomeUiState.Initial,
            is24Hour = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapRefresh = MapRefreshSetting.RESPONSIVE,
            onAction = {},
        )
    }
}

// The real Carlinkit-class projection: 800x480 px at 150 dpi = 853x512 dp (5:3).
// Wider and shorter in dp than the 16:9 case, so it is the binding geometry for
// the speed-overlay width cap and the info-pane height split.
@PreviewLightDark
@Preview(name = "Dashboard - 5:3 head unit", widthDp = 853, heightDp = 512)
@Composable
private fun DashboardScaffoldHeadUnitPreview() {
    FemtoTheme {
        DashboardScaffold(
            uiState = HomeUiState.Initial,
            is24Hour = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapRefresh = MapRefreshSetting.RESPONSIVE,
            onAction = {},
        )
    }
}

@PreviewLightDark
@Preview(name = "Dashboard - portrait", widthDp = 360, heightDp = 640)
@Composable
private fun DashboardScaffoldPortraitPreview() {
    FemtoTheme {
        DashboardScaffold(
            uiState = HomeUiState.Initial,
            is24Hour = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapRefresh = MapRefreshSetting.RESPONSIVE,
            onAction = {},
        )
    }
}
