package io.github.seijikohara.femto.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.home.PresetId
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.Motion
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
 * Top-level dashboard layout: the map is the full-screen background and
 * everything else — the info cards, the map controls, the clock / speed overlays,
 * and the dock itself — floats over it as glass. [dockPosition] picks the dock's
 * hosting edge (bottom/top as a horizontal bar, left/right as a vertical rail);
 * the overlays inset by the dock's extent so none sit under its nav buttons.
 *
 * ```
 * Landscape (wide)                    Portrait (tall)
 * +-------------------------------+   +-----------------------+
 * | clock     [calend][weather]   |   | clock                 |
 * | [marker]  map     [music   ]  |   | [marker]   map        |
 * |  speed [== dock ========== ]  |   |  speed                |
 * +-------------------------------+   | [calend][weather]     |
 *  (the dock is glass, over the map)  | [music            ]   |
 *                                     | [== dock ======== ]   |
 *                                     +-----------------------+
 * ```
 *
 * Landscape floats the cards in a right-hand column — the calendar and weather
 * share the top row side by side, the music card takes the wider share below;
 * portrait lays the same arrangement along the bottom. The self-marker is offset
 * to stay in the exposed map region — left of the right cards
 * ([MapConfig.rightSafeFraction]) and above the bottom cards / speed overlay /
 * dock ([MapConfig.bottomSafeFraction]) — rather than pinned to screen centre. A
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
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
    activePreset: PresetId = PresetId.COCKPIT,
    motionTier: MotionTier = MotionTier.STANDARD,
) = DashboardContent(
    uiState = uiState,
    is24Hour = is24Hour,
    showClockSeconds = showClockSeconds,
    speedUnit = speedUnit,
    temperatureUnit = temperatureUnit,
    mapConfig = mapConfig,
    panels = panels,
    glassConfig = glassConfig,
    onAction = onAction,
    dockPosition = dockPosition,
    modifier =
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    spectrum = spectrum,
    musicShowAlbum = musicShowAlbum,
    musicShowArt = musicShowArt,
    activePreset = activePreset,
    motionTier = motionTier,
)

// The full-screen dashboard body: the map fills the viewport as the background
// and every overlay — map controls, clock, speed, the floating cards, and the
// dock — layers over it. Reads the viewport itself so the orientation and spacing
// key off the available space, not the raw screen.
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
    dockPosition: DockPosition,
    modifier: Modifier = Modifier,
    spectrum: StateFlow<FloatArray?>? = null,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
    activePreset: PresetId = PresetId.COCKPIT,
    motionTier: MotionTier = MotionTier.STANDARD,
) = BoxWithConstraints(modifier = modifier) {
    val compact = maxHeight < CompactHeightBreakpoint || maxWidth < CompactWidthBreakpoint
    val portrait = maxHeight > maxWidth
    val outerPad = if (compact) CompactScreenPadding else FemtoDimens.ScreenPadding
    // One spacing unit: the inter-card gap equals the outer margin, so every floating
    // panel carries uniform margins on all four sides (gap-to-neighbour == edge-margin).
    val cardGap = outerPad
    val hasCards = panels.anyInfoPanel
    // Landscape floats the cards as a right-hand column over the map; portrait drops
    // them to a bottom band. The column compresses to the available height on a short
    // landscape (a phone) and caps on a tall one, so the map keeps the left either way.
    val landscapeCards = hasCards && !portrait
    val bottomCards = hasCards && portrait

    // The landscape card column scales with the viewport (wider panels give the
    // side-by-side calendar + weather room) but is clamped so it neither shrinks
    // those cards to nothing on a 16:9 unit nor eats the map on an ultra-wide one.
    val floatingCardWidth = (maxWidth * FLOATING_CARD_WIDTH_FRACTION).coerceIn(
        FloatingCardWidthMin,
        FloatingCardWidthMax,
    )

    // Shared Haze state: the map registers as the blur source, every glass overlay
    // (chrome, the floating cards, and the dock) samples it. The WebView GL surface
    // falls back to the tint (it cannot be captured by Haze).
    val hazeState = rememberHazeState()
    var following by remember { mutableStateOf(true) }
    var bearingDeg by remember { mutableFloatStateOf(0f) }
    var recenterNonce by remember { mutableIntStateOf(0) }
    var overlayHeightPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    // The dock floats over the map as a rounded panel, inset from its edge by
    // outerPad like the cards; the overlays inset by that whole footprint (the
    // margin + the thickness) so none sit under it.
    val dockExtent = FemtoDimens.DockThickness + outerPad

    // The right cards push the marker left; the bottom (portrait) cards and the
    // bottom dock extend the bottom safe band so the marker clears them. Each
    // orientation feeds one axis.
    val rightSafeFraction =
        if (landscapeCards) {
            with(density) {
                val widthPx = maxWidth.toPx()
                val dockEnd = if (dockPosition == DockPosition.RIGHT) dockExtent else 0.dp
                if (widthPx > 0f) {
                    ((floatingCardWidth + outerPad + dockEnd).toPx() / widthPx).coerceIn(0f, 0.45f)
                } else {
                    0f
                }
            }
        } else {
            0f
        }
    // The band height as the cards actually get it: a fraction of the overlay box
    // (the viewport already inset by the dock), so the speed/marker reserve matches
    // the rendered band instead of over-reserving by the dock's extent.
    // Capped so the band keeps its designed height on tall portrait panels rather
    // than stretching the cards into sparse glass; the extra height goes to the map.
    val bottomCardBand =
        if (bottomCards) {
            ((maxHeight - dockExtent) * PORTRAIT_CARD_HEIGHT_FRACTION).coerceAtMost(CardClusterMaxHeight)
        } else {
            0.dp
        }
    val bottomSafeFraction =
        with(density) {
            val heightPx = maxHeight.roundToPx()
            if (heightPx > 0) {
                val dockBottom = if (dockPosition == DockPosition.BOTTOM) dockExtent else 0.dp
                val overlay = overlayHeightPx + (cardGap + MarkerOverlayClearance + dockBottom).toPx()
                val cards = bottomCardBand.toPx()
                ((overlay + cards) / heightPx).coerceIn(0f, 0.5f)
            } else {
                0f
            }
        }

    // The map fills the whole viewport, behind the dock and every overlay.
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

    // Every overlay lives in a region inset from the dock edge, so the cards,
    // controls, and speed overlay never sit under the dock's nav buttons while the
    // map still shows through behind the dock. The cockpit face is extracted so the
    // map (blur source) and the dock stay composed once at this level, outside any
    // future preset branch.
    CockpitOverlays(
        uiState = uiState,
        is24Hour = is24Hour,
        showClockSeconds = showClockSeconds,
        speedUnit = speedUnit,
        temperatureUnit = temperatureUnit,
        panels = panels,
        glassConfig = glassConfig,
        outerPad = outerPad,
        cardGap = cardGap,
        landscapeCards = landscapeCards,
        bottomCards = bottomCards,
        bottomCardBand = bottomCardBand,
        floatingCardWidth = floatingCardWidth,
        hasCards = hasCards,
        hazeState = hazeState,
        following = following,
        bearingDeg = bearingDeg,
        motionTier = motionTier,
        onRecenter = { recenterNonce++ },
        onOverlayHeightChange = { overlayHeightPx = it },
        onAction = onAction,
        modifier = Modifier.fillMaxSize().padding(dockEdgePadding(dockPosition, dockExtent)),
        spectrum = spectrum,
        musicShowAlbum = musicShowAlbum,
        musicShowArt = musicShowArt,
    )

    // The dock as a glass bar / rail on its edge, drawn over the full-bleed map.
    DashboardDock(
        systemStatus = uiState.systemStatus,
        onAction = onAction,
        position = dockPosition,
        hazeState = hazeState,
        glassConfig = glassConfig,
        modifier =
            when (dockPosition) {
                DockPosition.BOTTOM, DockPosition.TOP -> {
                    Modifier
                        .align(dockAlignment(dockPosition))
                        .fillMaxWidth()
                        .padding(dockFloatPadding(dockPosition, outerPad))
                }

                DockPosition.LEFT, DockPosition.RIGHT -> {
                    Modifier
                        .align(dockAlignment(dockPosition))
                        .fillMaxHeight()
                        .padding(dockFloatPadding(dockPosition, outerPad))
                }
            },
    )
}

// The cockpit face: the glass overlay tree that floats over the map — map controls,
// the clock and speed overlays, the floating info cards, and the three maximize
// panels. The caller keeps the map (the blur source) and the dock composed one level
// up, outside this face, and supplies the dock-edge inset through [modifier] so the
// overlays never sit under the dock's nav buttons while the map shows through behind
// it. Extracting the face keeps that map/dock pair stable across a future preset
// switch instead of tearing down the WebView.
@Composable
private fun CockpitOverlays(
    uiState: HomeUiState,
    is24Hour: Boolean,
    showClockSeconds: Boolean,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    panels: PanelVisibility,
    glassConfig: GlassConfig,
    outerPad: Dp,
    cardGap: Dp,
    landscapeCards: Boolean,
    bottomCards: Boolean,
    bottomCardBand: Dp,
    floatingCardWidth: Dp,
    hasCards: Boolean,
    hazeState: HazeState,
    following: Boolean,
    bearingDeg: Float,
    motionTier: MotionTier,
    onRecenter: () -> Unit,
    onOverlayHeightChange: (Int) -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    spectrum: StateFlow<FloatArray?>? = null,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
) {
    // Full-screen Now Playing panel (issue #231). Pure UI state — saveable so a
    // rotation keeps the panel open — auto-collapsed when the session leaves
    // Playing so a dead session never strands an empty panel over the map.
    var nowPlayingExpanded by rememberSaveable { mutableStateOf(false) }
    val expandedNowPlaying = (uiState.musicState as? MusicCardState.Playing)?.nowPlaying
    LaunchedEffect(expandedNowPlaying == null) {
        if (expandedNowPlaying == null) nowPlayingExpanded = false
    }
    // Hold the last live track so the collapse animation still renders content
    // when the session ends (expandedNowPlaying goes null the same frame the
    // panel starts fading out) rather than flashing empty mid-exit.
    var panelNowPlaying by remember { mutableStateOf(expandedNowPlaying) }
    LaunchedEffect(expandedNowPlaying) {
        if (expandedNowPlaying != null) panelNowPlaying = expandedNowPlaying
    }
    // Full-screen calendar panel, mirroring the Now Playing panel above: pure UI
    // state, saveable across rotation, auto-collapsed once the snapshot no longer
    // has real data to show (permission revoked mid-session or a query fault).
    var calendarExpanded by rememberSaveable { mutableStateOf(false) }
    val expandedCalendar = uiState.calendar?.takeIf { it.hasCalendarAccess && !it.queryFailed }
    LaunchedEffect(expandedCalendar == null) {
        if (expandedCalendar == null) calendarExpanded = false
    }
    var panelCalendar by remember { mutableStateOf(expandedCalendar) }
    LaunchedEffect(expandedCalendar) {
        if (expandedCalendar != null) panelCalendar = expandedCalendar
    }
    // Full-screen weather panel, mirroring the calendar panel above: pure UI
    // state, saveable across rotation, auto-collapsed once the snapshot goes
    // null (e.g. a cold-start window with no cached data yet).
    var weatherExpanded by rememberSaveable { mutableStateOf(false) }
    val expandedWeather = uiState.weather
    LaunchedEffect(expandedWeather == null) {
        if (expandedWeather == null) weatherExpanded = false
    }
    var panelWeather by remember { mutableStateOf(expandedWeather) }
    LaunchedEffect(expandedWeather) {
        if (expandedWeather != null) panelWeather = expandedWeather
    }

    Box(modifier = modifier) {
        // Map controls render only when the map does (a fix exists).
        if (uiState.location != null) {
            MapCompass(
                bearingDeg = bearingDeg,
                onTap = { onAction(HomeAction.ToggleMapNorthUp) },
                hazeState = hazeState,
                glassConfig = glassConfig,
                modifier = Modifier.align(Alignment.TopStart).padding(outerPad),
            )
            MapControlColumn(
                showLocate = true,
                following = following,
                onLocate = onRecenter,
                onZoomIn = { onAction(HomeAction.AdjustMapZoom(1)) },
                onZoomOut = { onAction(HomeAction.AdjustMapZoom(-1)) },
                hazeState = hazeState,
                glassConfig = glassConfig,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = outerPad),
            )
        }

        // Clock beside the date: in landscape it sits just left of the calendar card
        // (the top of the right column), reading as a pair with the date; in portrait
        // the right column is absent, so it sits in the top-right corner, clear of
        // the bottom cards.
        ClockOverlay(
            is24Hour = is24Hour,
            showSeconds = showClockSeconds,
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = outerPad,
                        end = if (landscapeCards) floatingCardWidth + outerPad + cardGap else outerPad,
                    ),
        )

        // Speed overlay centred in the exposed map area above the dock, held clear of
        // the right cards (landscape) or the bottom card band (portrait) by reserving
        // their footprint so it centres in the visible map strip.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        bottom = cardGap + if (bottomCards) bottomCardBand else 0.dp,
                        end = if (landscapeCards) floatingCardWidth + outerPad + cardGap else 0.dp,
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
                modifier = Modifier.onSizeChanged { onOverlayHeightChange(it.height) },
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
                hazeState = hazeState,
                glassConfig = glassConfig,
                onAction = onAction,
                onExpandNowPlaying = { nowPlayingExpanded = true },
                onExpandCalendar = { calendarExpanded = true },
                onExpandWeather = { weatherExpanded = true },
                spectrum = spectrum,
                musicShowAlbum = musicShowAlbum,
                musicShowArt = musicShowArt,
                modifier =
                    if (bottomCards) {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(bottomCardBand)
                            .padding(horizontal = outerPad, vertical = outerPad)
                    } else {
                        // Cards in a right-hand column, top-anchored and height-capped
                        // so they pair with the clock and never stretch; the map keeps
                        // the left.
                        Modifier
                            .align(Alignment.TopEnd)
                            .width(floatingCardWidth)
                            .heightIn(max = CardClusterMaxHeight)
                            .fillMaxHeight()
                            .padding(top = outerPad, bottom = outerPad, end = outerPad)
                    },
            )
        }

        // Drawn after (over) the cards but before the dock, which is a later
        // sibling of this Box — so the panel reaches exactly to the dock edge,
        // the map blurs through the glass, and the dock stays operable. The
        // maximize/minimize fades with a subtle scale so the panel grows into
        // place rather than popping; the exit renders panelNowPlaying so a
        // session ending mid-collapse still fades its last frame.
        AnimatedVisibility(
            visible = nowPlayingExpanded && expandedNowPlaying != null,
            enter = Motion.panelEnter(motionTier),
            exit = Motion.panelExit(motionTier),
            modifier = Modifier.fillMaxSize().padding(outerPad),
        ) {
            panelNowPlaying?.let { nowPlaying ->
                NowPlayingPanel(
                    nowPlaying = nowPlaying,
                    onCommand = { command -> onAction(HomeAction.Music(command)) },
                    onLaunchSource = { packageName -> onAction(HomeAction.LaunchMusicSource(packageName)) },
                    onClose = { nowPlayingExpanded = false },
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    spectrum = spectrum,
                    showAlbum = musicShowAlbum,
                    showArt = musicShowArt,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedVisibility(
            visible = calendarExpanded && expandedCalendar != null,
            enter = Motion.panelEnter(motionTier),
            exit = Motion.panelExit(motionTier),
            modifier = Modifier.fillMaxSize().padding(outerPad),
        ) {
            panelCalendar?.let { snapshot ->
                CalendarPanel(
                    snapshot = snapshot,
                    is24Hour = is24Hour,
                    onOpenExternal = { onAction(HomeAction.OpenCalendar) },
                    onClose = { calendarExpanded = false },
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedVisibility(
            visible = weatherExpanded && expandedWeather != null,
            enter = Motion.panelEnter(motionTier),
            exit = Motion.panelExit(motionTier),
            modifier = Modifier.fillMaxSize().padding(outerPad),
        ) {
            panelWeather?.let { snapshot ->
                WeatherPanel(
                    snapshot = snapshot,
                    temperatureUnit = temperatureUnit,
                    speedUnit = speedUnit,
                    is24Hour = is24Hour,
                    onOpenExternal = { onAction(HomeAction.OpenWeather) },
                    onClose = { weatherExpanded = false },
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// Margins that float the dock off its three free edges (the inner edge faces the
// dashboard, where the overlay inset already opens the gap).
private fun dockFloatPadding(
    position: DockPosition,
    margin: Dp,
): PaddingValues =
    when (position) {
        DockPosition.BOTTOM -> PaddingValues(start = margin, end = margin, bottom = margin)
        DockPosition.TOP -> PaddingValues(start = margin, end = margin, top = margin)
        DockPosition.LEFT -> PaddingValues(top = margin, bottom = margin, start = margin)
        DockPosition.RIGHT -> PaddingValues(top = margin, bottom = margin, end = margin)
    }

// Padding that keeps the overlay region clear of the dock on its hosting edge.
private fun dockEdgePadding(
    position: DockPosition,
    extent: Dp,
): PaddingValues =
    when (position) {
        DockPosition.BOTTOM -> PaddingValues(bottom = extent)
        DockPosition.TOP -> PaddingValues(top = extent)
        DockPosition.LEFT -> PaddingValues(start = extent)
        DockPosition.RIGHT -> PaddingValues(end = extent)
    }

// Alignment that pins the dock to its hosting edge within the full-screen box.
private fun dockAlignment(position: DockPosition): Alignment =
    when (position) {
        DockPosition.BOTTOM -> Alignment.BottomCenter
        DockPosition.TOP -> Alignment.TopCenter
        DockPosition.LEFT -> Alignment.CenterStart
        DockPosition.RIGHT -> Alignment.CenterEnd
    }

// The floating info cards: the calendar+weather row stacked over the music card,
// hosted in the landscape right column or the portrait bottom band. Each card gets
// the shared glass treatment so the map shows through.
@Composable
private fun FloatingCardColumn(
    uiState: HomeUiState,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
    panels: PanelVisibility,
    cardGap: Dp,
    is24Hour: Boolean,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onAction: (HomeAction) -> Unit,
    onExpandNowPlaying: () -> Unit,
    onExpandCalendar: () -> Unit,
    onExpandWeather: () -> Unit,
    modifier: Modifier = Modifier,
    spectrum: StateFlow<FloatArray?>? = null,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
) {
    val calendar: @Composable (Modifier) -> Unit = { cardModifier ->
        CalendarCard(
            snapshot = uiState.calendar,
            is24Hour = is24Hour,
            onExpand = onExpandCalendar,
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = cardModifier,
        )
    }
    val weather: @Composable (Modifier) -> Unit = { cardModifier ->
        WeatherCard(
            snapshot = uiState.weather,
            temperatureUnit = temperatureUnit,
            speedUnit = speedUnit,
            is24Hour = is24Hour,
            onExpand = onExpandWeather,
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = cardModifier,
        )
    }
    val music: @Composable (Modifier) -> Unit = { cardModifier ->
        MusicCard(
            state = uiState.musicState,
            onCommand = { command -> onAction(HomeAction.Music(command)) },
            onConnect = { onAction(HomeAction.ConnectMusicPlayer) },
            onLaunchSource = { packageName -> onAction(HomeAction.LaunchMusicSource(packageName)) },
            onExpand = onExpandNowPlaying,
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = cardModifier,
            spectrum = spectrum,
            showAlbum = musicShowAlbum,
            showArt = musicShowArt,
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(cardGap)) {
        // Calendar + weather pair in a row so each keeps its designed height instead
        // of stacking three full cards into a column too short for them; a single
        // visible card takes the whole row.
        if (panels.calendar || panels.weather) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(cardGap),
            ) {
                if (panels.calendar) calendar(Modifier.weight(1f).fillMaxHeight())
                if (panels.weather) weather(Modifier.weight(1f).fillMaxHeight())
            }
        }
        if (panels.music) music(Modifier.weight(MUSIC_CARD_WEIGHT).fillMaxWidth())
    }
}

// Below either breakpoint the dashboard switches to its compact spacing. The
// thresholds are deliberately coarse: they separate small / short head units from
// large in-dash panels without targeting any one resolution. The height threshold
// sits above the common ~512 dp 5:3 projection so those short landscapes take the
// tighter spacing, leaving more room for the floating cards.
private val CompactHeightBreakpoint: Dp = 560.dp
private val CompactWidthBreakpoint: Dp = 600.dp

// Compact outer spacing; the comfortable value is the FemtoDimens default used on
// large panels. The inter-card gap reuses this same value (cardGap = outerPad) so
// panel margins stay uniform.
private val CompactScreenPadding: Dp = 12.dp

// Extra room kept above the speed overlay so the self-marker chevron (and most of
// its ripple) clears it; together with the panel gap (cardGap) it forms the bottom
// band the marker drop must avoid (MapConfig.bottomSafeFraction).
private val MarkerOverlayClearance: Dp = 20.dp

// The landscape floating card column scales with the viewport so the side-by-side
// calendar + weather cards keep usable width, clamped at both ends. The fraction
// derives MapConfig.rightSafeFraction without a second layout pass; ~0.40 leaves
// the bulk of a 5:3 (853 dp) panel for the map, the min keeps a 16:9 unit's cards
// legible, and the max stops an ultra-wide panel from eating the map.
private const val FLOATING_CARD_WIDTH_FRACTION = 0.40f
private val FloatingCardWidthMin: Dp = 260.dp
private val FloatingCardWidthMax: Dp = 350.dp

// Cap on the floating card cluster's height (right column and portrait band) so the
// cards keep their designed proportions on tall displays (1080 dp+) instead of
// stretching — the music card especially — into sparse glass; the freed height goes
// to the full-bleed map. Sits above the 720 dp head-unit column, which still fills.
private val CardClusterMaxHeight: Dp = 680.dp

// The share of the height the portrait bottom card band takes (capped by
// CardClusterMaxHeight on tall panels).
private const val PORTRAIT_CARD_HEIGHT_FRACTION = 0.52f

// The music card's weight against the calendar + weather row in the floating column.
// It carries the most content (album art + title / artist / album + progress + the
// >= 64 dp transport row), so it takes a larger share than the cal / weather cards.
private const val MUSIC_CARD_WEIGHT = 1.2f

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
