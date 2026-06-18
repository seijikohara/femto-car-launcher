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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.MapRenderMode
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlinx.coroutines.flow.StateFlow

// Which info cards the dashboard renders. Each defaults to visible; hiding one
// lets the remaining cards reflow, and hiding all leaves the bare full-screen map.
// Sourced from DisplaySettings and threaded down like MapConfig.
internal data class PanelVisibility(
    val calendar: Boolean = true,
    val weather: Boolean = true,
    val music: Boolean = true,
) {
    // True while at least one info card is shown. When false the floating card
    // overlay is dropped entirely so the map shows uncovered.
    val anyInfoPanel: Boolean get() = calendar || weather || music
}

/**
 * Top-level dashboard layout: a full-screen map with the info cards floating over
 * it as glass overlays, plus a fixed dock. [dockPosition] picks the dock's hosting
 * edge (bottom/top as a horizontal bar, left/right as a vertical rail); the map
 * content fills whatever the dock leaves.
 *
 * ```
 * Landscape (wide)                    Portrait (tall)
 * +-------------------------------+   +-----------------------+
 * | clock        [calendar]       |   | clock                 |
 * | [marker] map [weather ]       |   | [marker]  map         |
 * |  speed       [music   ]       |   |  speed                |
 * +-------------------------------+   | [calendar] [weather]  |
 * | DashboardDock                 |   | [music            ]   |
 * +-------------------------------+   +-----------------------+
 *                                     | DashboardDock         |
 *                                     +-----------------------+
 * ```
 *
 * The cards float on the right (landscape) or along the bottom (portrait). The
 * self-marker is offset to stay in the exposed map region — left of the right
 * cards ([MapConfig.rightSafeFraction]) and above the bottom cards / speed overlay
 * ([MapConfig.bottomSafeFraction]) — rather than pinned to screen centre. A
 * [BoxWithConstraints] reads the viewport to pick the orientation and tighten the
 * spacing on a compact panel, so the layout keys off geometry, never a device.
 *
 * `enableEdgeToEdge()` lets the activity paint under the system bars; the scaffold
 * reserves them back with [windowInsetsPadding] so the dock never hides behind the
 * navigation bar.
 */
@Composable
internal fun DashboardScaffold(
    uiState: HomeUiState,
    is24Hour: Boolean,
    showClockSeconds: Boolean,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    mapConfig: MapConfig,
    panels: PanelVisibility,
    glassConfig: GlassConfig,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    dockPosition: DockPosition = DockPosition.BOTTOM,
    spectrum: StateFlow<FloatArray?>? = null,
) {
    val rootModifier =
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        DashboardContent(
            uiState = uiState,
            is24Hour = is24Hour,
            showClockSeconds = showClockSeconds,
            speedUnit = speedUnit,
            temperatureUnit = temperatureUnit,
            mapConfig = mapConfig,
            panels = panels,
            glassConfig = glassConfig,
            onAction = onAction,
            modifier = contentModifier,
            spectrum = spectrum,
        )
    }
    val dock: @Composable (Modifier) -> Unit = { dockModifier ->
        DashboardDock(
            systemStatus = uiState.systemStatus,
            onAction = onAction,
            position = dockPosition,
            modifier = dockModifier,
        )
    }
    when (dockPosition) {
        DockPosition.BOTTOM -> {
            Column(rootModifier) {
                content(Modifier.weight(1f).fillMaxWidth())
                dock(Modifier.fillMaxWidth())
            }
        }

        DockPosition.TOP -> {
            Column(rootModifier) {
                dock(Modifier.fillMaxWidth())
                content(Modifier.weight(1f).fillMaxWidth())
            }
        }

        DockPosition.LEFT -> {
            Row(rootModifier) {
                dock(Modifier.fillMaxHeight())
                content(Modifier.weight(1f).fillMaxHeight())
            }
        }

        DockPosition.RIGHT -> {
            Row(rootModifier) {
                content(Modifier.weight(1f).fillMaxHeight())
                dock(Modifier.fillMaxHeight())
            }
        }
    }
}

// The full-screen dashboard body: the map fills the viewport and the info cards
// float over it as glass overlays. Reads the viewport itself so the orientation
// and spacing key off the space the dock placement leaves, not the raw screen.
@Composable
private fun DashboardContent(
    uiState: HomeUiState,
    is24Hour: Boolean,
    showClockSeconds: Boolean,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    mapConfig: MapConfig,
    panels: PanelVisibility,
    glassConfig: GlassConfig,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    spectrum: StateFlow<FloatArray?>? = null,
) = BoxWithConstraints(modifier = modifier) {
    val compact = maxHeight < CompactHeightBreakpoint || maxWidth < CompactWidthBreakpoint
    val portrait = maxHeight > maxWidth
    val outerPad = if (compact) CompactScreenPadding else FemtoDimens.ScreenPadding
    val cardGap = if (compact) CompactPaneGap else FemtoDimens.PaneGap
    val hasCards = panels.anyInfoPanel
    val landscapeCards = hasCards && !portrait
    val portraitCards = hasCards && portrait

    // Shared Haze state: the map registers as the blur source, every glass overlay
    // (chrome and the floating cards) samples it. Only the snapshot backend (a
    // Compose Image) can be captured; the Live GL surface falls back to the tint.
    val hazeState = rememberHazeState()
    val live = mapConfig.renderMode == MapRenderMode.LIVE
    var following by remember { mutableStateOf(true) }
    var bearingDeg by remember { mutableFloatStateOf(0f) }
    var recenterNonce by remember { mutableIntStateOf(0) }
    var overlayHeightPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    // The right cards push the marker left; the bottom (portrait) cards extend the
    // bottom safe band so the marker clears them. Each orientation feeds one axis.
    val rightSafeFraction =
        if (landscapeCards) {
            with(density) {
                val widthPx = maxWidth.toPx()
                if (widthPx > 0f) ((FloatingCardWidth + outerPad).toPx() / widthPx).coerceIn(0f, 0.45f) else 0f
            }
        } else {
            0f
        }
    val portraitCardBand = if (portraitCards) maxHeight * PORTRAIT_CARD_HEIGHT_FRACTION else 0.dp
    val bottomSafeFraction =
        with(density) {
            val heightPx = maxHeight.roundToPx()
            if (heightPx > 0) {
                val overlay = overlayHeightPx + (SpeedOverlayBottomGap + MarkerOverlayClearance).toPx()
                val cards = portraitCardBand.toPx()
                ((overlay + cards) / heightPx).coerceIn(0f, 0.5f)
            } else {
                0f
            }
        }

    MapPanel(
        location = uiState.location,
        mapConfig =
            mapConfig.copy(
                bottomSafeFraction = bottomSafeFraction,
                rightSafeFraction = rightSafeFraction,
            ),
        onTap = { onAction(HomeAction.OpenMaps) },
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        recenterNonce = recenterNonce,
        onFollowChange = { following = it },
        onBearingChange = { bearingDeg = it },
    )

    // Map controls render only when the map does (a fix exists). The compass and
    // locate button are LIVE-only (SNAPSHOT has no free camera); the zoom pair
    // works on both backends via the persisted setting.
    if (uiState.location != null) {
        if (live) {
            MapCompass(
                bearingDeg = bearingDeg,
                onTap = { onAction(HomeAction.ToggleMapNorthUp) },
                hazeState = hazeState,
                glassConfig = glassConfig,
                modifier = Modifier.align(Alignment.TopStart).padding(outerPad),
            )
        }
        MapControlColumn(
            showLocate = live,
            following = following,
            onLocate = { recenterNonce++ },
            onZoomIn = { onAction(HomeAction.AdjustMapZoom(1)) },
            onZoomOut = { onAction(HomeAction.AdjustMapZoom(-1)) },
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = outerPad),
        )
    }

    // Clock centred over the exposed map area — the right card column is reserved
    // in landscape, so it balances with the speed overlay below rather than crowding
    // the cards, and clears the top-start compass.
    Box(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    top = outerPad,
                    end = if (landscapeCards) FloatingCardWidth + outerPad else 0.dp,
                ),
        contentAlignment = Alignment.TopCenter,
    ) {
        ClockOverlay(
            is24Hour = is24Hour,
            showSeconds = showClockSeconds,
            hazeState = hazeState,
            glassConfig = glassConfig,
        )
    }

    // Speed overlay centred in the exposed map area: the right card column
    // (landscape) is reserved with end padding, and the bottom card band (portrait)
    // with extra bottom padding, so the overlay centres in the visible strip.
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    bottom = SpeedOverlayBottomGap + if (portraitCards) portraitCardBand + cardGap else 0.dp,
                    end = if (landscapeCards) FloatingCardWidth + outerPad else 0.dp,
                ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SpeedOverlay(
            location = uiState.location,
            address = uiState.address,
            tripState = uiState.tripState,
            speedUnit = speedUnit,
            onReset = { onAction(HomeAction.ResetTrip) },
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = Modifier.onSizeChanged { overlayHeightPx = it.height },
        )
    }

    if (hasCards) {
        FloatingCardColumn(
            uiState = uiState,
            temperatureUnit = temperatureUnit,
            speedUnit = speedUnit,
            panels = panels,
            cardGap = cardGap,
            is24Hour = is24Hour,
            portrait = portrait,
            hazeState = hazeState,
            glassConfig = glassConfig,
            onAction = onAction,
            spectrum = spectrum,
            modifier =
                if (portrait) {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(PORTRAIT_CARD_HEIGHT_FRACTION)
                        .padding(horizontal = outerPad, vertical = outerPad)
                } else {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(FloatingCardWidth)
                        .fillMaxHeight()
                        .padding(top = outerPad, bottom = outerPad, end = outerPad)
                },
        )
    }
}

// The floating info cards. Landscape stacks them vertically in a narrow right
// column; portrait lays the calendar + weather side by side with the music card
// below (the wide bottom strip has the width for it). Each card gets the shared
// glass treatment so the map shows through.
@Composable
private fun FloatingCardColumn(
    uiState: HomeUiState,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
    panels: PanelVisibility,
    cardGap: Dp,
    is24Hour: Boolean,
    portrait: Boolean,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    spectrum: StateFlow<FloatArray?>? = null,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(cardGap),
) {
    // Portrait pairs calendar + weather in a row (the bottom strip is wide); a
    // single visible card takes the whole row. Landscape stacks each card full-width
    // in the narrow column instead.
    if (panels.calendar || panels.weather) {
        if (portrait) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(cardGap),
            ) {
                if (panels.calendar) {
                    CalendarCard(
                        snapshot = uiState.calendar,
                        is24Hour = is24Hour,
                        onOpen = { onAction(HomeAction.OpenCalendar) },
                        hazeState = hazeState,
                        glassConfig = glassConfig,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (panels.weather) {
                    WeatherCard(
                        snapshot = uiState.weather,
                        temperatureUnit = temperatureUnit,
                        speedUnit = speedUnit,
                        is24Hour = is24Hour,
                        onOpen = { onAction(HomeAction.OpenWeather) },
                        hazeState = hazeState,
                        glassConfig = glassConfig,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        } else {
            if (panels.calendar) {
                CalendarCard(
                    snapshot = uiState.calendar,
                    is24Hour = is24Hour,
                    onOpen = { onAction(HomeAction.OpenCalendar) },
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            if (panels.weather) {
                WeatherCard(
                    snapshot = uiState.weather,
                    temperatureUnit = temperatureUnit,
                    speedUnit = speedUnit,
                    is24Hour = is24Hour,
                    onOpen = { onAction(HomeAction.OpenWeather) },
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
    if (panels.music) {
        MusicCard(
            state = uiState.musicState,
            onCommand = { command -> onAction(HomeAction.Music(command)) },
            onConnect = { onAction(HomeAction.ConnectMusicPlayer) },
            onLaunchSource = { packageName -> onAction(HomeAction.LaunchMusicSource(packageName)) },
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = Modifier.weight(MUSIC_CARD_WEIGHT).fillMaxWidth(),
            spectrum = spectrum,
        )
    }
}

// Below either breakpoint the dashboard switches to its compact spacing. The
// thresholds are deliberately coarse: they separate small / short head units from
// large in-dash panels without targeting any one resolution. The height threshold
// sits above the common ~512 dp 5:3 projection so those short landscapes take the
// tighter spacing, leaving more room for the floating cards.
private val CompactHeightBreakpoint: Dp = 560.dp
private val CompactWidthBreakpoint: Dp = 600.dp

// Compact outer / inter-card spacing; the comfortable values are the FemtoDimens
// defaults used on large panels.
private val CompactScreenPadding: Dp = 12.dp
private val CompactPaneGap: Dp = 10.dp

// Gap between the speed overlay and the map's bottom edge, and the extra room kept
// above the overlay so the self-marker chevron (and most of its ripple) clears it.
// Together they form the bottom band the marker drop must avoid
// (MapConfig.bottomSafeFraction).
private val SpeedOverlayBottomGap: Dp = 16.dp
private val MarkerOverlayClearance: Dp = 20.dp

// The fixed width of the right-hand floating card column in landscape. Fixed (not
// measured) so MapConfig.rightSafeFraction derives from it without a second layout
// pass; 240 dp leaves the bulk of a 5:3 (853 dp) panel for the map.
private val FloatingCardWidth: Dp = 240.dp

// The share of the height the bottom floating card band takes in portrait.
private const val PORTRAIT_CARD_HEIGHT_FRACTION = 0.42f

// The music card's weight within the floating column, a touch taller than an
// even split so its transport controls are not starved on a short panel.
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
            showClockSeconds = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapConfig = MapConfig(),
            panels = PanelVisibility(),
            glassConfig = GlassConfig(),
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
            showClockSeconds = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapConfig = MapConfig(),
            panels = PanelVisibility(),
            glassConfig = GlassConfig(),
            onAction = {},
        )
    }
}

// The real Carlinkit-class projection: 800x480 px at 150 dpi = 853x512 dp (5:3).
// Wider and shorter in dp than the 16:9 case, so it is the binding geometry for
// the floating-card width reservation and the marker safe region.
@PreviewLightDark
@Preview(name = "Dashboard - 5:3 head unit", widthDp = 853, heightDp = 512)
@Composable
private fun DashboardScaffoldHeadUnitPreview() {
    FemtoTheme {
        DashboardScaffold(
            uiState = HomeUiState.Initial,
            is24Hour = true,
            showClockSeconds = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapConfig = MapConfig(),
            panels = PanelVisibility(),
            glassConfig = GlassConfig(),
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
            showClockSeconds = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapConfig = MapConfig(),
            panels = PanelVisibility(),
            glassConfig = GlassConfig(),
            onAction = {},
        )
    }
}

// Dock as a left rail on the 5:3 head unit: the map content reflows into the
// remaining width and the nav buttons share the rail height.
@PreviewLightDark
@Preview(name = "Dashboard - left rail dock", widthDp = 853, heightDp = 512)
@Composable
private fun DashboardScaffoldLeftRailPreview() {
    FemtoTheme {
        DashboardScaffold(
            uiState = HomeUiState.Initial,
            is24Hour = true,
            showClockSeconds = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapConfig = MapConfig(),
            panels = PanelVisibility(),
            glassConfig = GlassConfig(),
            onAction = {},
            dockPosition = DockPosition.LEFT,
        )
    }
}

// Calendar hidden: the weather + music cards reflow, exercising the
// partial-visibility path.
@PreviewLightDark
@Preview(name = "Dashboard - calendar hidden", widthDp = 853, heightDp = 512)
@Composable
private fun DashboardScaffoldHiddenPanelPreview() {
    FemtoTheme {
        DashboardScaffold(
            uiState = HomeUiState.Initial,
            is24Hour = true,
            showClockSeconds = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapConfig = MapConfig(),
            panels = PanelVisibility(calendar = false),
            glassConfig = GlassConfig(),
            onAction = {},
        )
    }
}
