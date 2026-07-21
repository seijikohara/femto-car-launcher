package io.github.seijikohara.femto.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.location.MIN_MOVING_SPEED_MS
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.ui.drawer.AppDrawerPanelHost
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock

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
 * share the top row side by side and grow to fill the column, the music card sits
 * below at its content height; portrait lays the same arrangement along the
 * bottom. The self-marker is offset to stay in the exposed map region — left of
 * the right cards
 * ([MapConfig.rightSafeFraction]) and above the bottom cards / speed overlay /
 * dock ([MapConfig.bottomSafeFraction]) — rather than pinned to screen centre. A
 * [BoxWithConstraints] reads the viewport to pick the orientation and tighten the
 * spacing on a compact panel, so the layout keys off geometry, never a device.
 * The whole arrangement above mirrors to the driver's side via [driverSide]
 * ([DriverSide.RIGHT] is the default and the layout drawn here).
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
    dockConfig: DockConfig = DockConfig(),
    driverSide: DriverSide = DriverSide.RIGHT,
    spectrum: StateFlow<FloatArray?>? = null,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
    motionTier: MotionTier = MotionTier.STANDARD,
    clock: Clock = Clock.systemDefaultZone(),
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
    dockConfig = dockConfig,
    driverSide = driverSide,
    modifier =
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    spectrum = spectrum,
    musicShowAlbum = musicShowAlbum,
    musicShowArt = musicShowArt,
    motionTier = motionTier,
    clock = clock,
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
    driverSide: DriverSide,
    modifier: Modifier = Modifier,
    dockConfig: DockConfig = DockConfig(),
    spectrum: StateFlow<FloatArray?>? = null,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
    motionTier: MotionTier = MotionTier.STANDARD,
    clock: Clock = Clock.systemDefaultZone(),
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

    // Every maximize panel's expanded state lives HERE, one level above the
    // overlay tree, so one dismiss definition can drive catchers on both sides
    // of the dock inset: the inner catcher (inside DashboardOverlays) covers
    // the overlay box, the outer catcher below covers the dock-margin slivers
    // outside it. The apps panel's trigger is the dock's APPS button — a
    // sibling of the overlays — so its OpenAppDrawer action is intercepted
    // here rather than routed to the ViewModel. rememberSaveable keeps an open
    // panel open across rotation.
    var appsExpanded by rememberSaveable { mutableStateOf(false) }
    var nowPlayingExpanded by rememberSaveable { mutableStateOf(false) }
    var calendarExpanded by rememberSaveable { mutableStateOf(false) }
    var weatherExpanded by rememberSaveable { mutableStateOf(false) }
    var tripExpanded by rememberSaveable { mutableStateOf(false) }
    // Auto-collapse when a panel's backing data disappears (session ended,
    // permission revoked mid-session, cold cache) so a dead panel never strands
    // over the map. The trip panel needs no gate: its ViewModel always has
    // state. The snapshot render-caches that keep exit animations fed stay in
    // DashboardOverlays.
    val hasNowPlaying = (uiState.musicState as? MusicCardState.Playing)?.nowPlaying != null
    LaunchedEffect(hasNowPlaying) {
        if (!hasNowPlaying) nowPlayingExpanded = false
    }
    val hasCalendar = uiState.calendar?.takeIf { it.hasCalendarAccess && !it.queryFailed } != null
    LaunchedEffect(hasCalendar) {
        if (!hasCalendar) calendarExpanded = false
    }
    val hasWeather = uiState.weather != null
    LaunchedEffect(hasWeather) {
        if (!hasWeather) weatherExpanded = false
    }
    // The apps panel is the only one reachable while another panel is open —
    // the dock stays operable — so opening it collapses whatever is underneath,
    // mirroring how the old drawer sheet covered everything.
    LaunchedEffect(appsExpanded) {
        if (appsExpanded) {
            nowPlayingExpanded = false
            calendarExpanded = false
            weatherExpanded = false
            tripExpanded = false
        }
    }
    // A tap outside an open panel's body dismisses it, matching the modal
    // sheets' scrim-tap behavior. One definition shared by both catchers.
    val dismissOpenPanel: (() -> Unit)? =
        when {
            nowPlayingExpanded -> ({ nowPlayingExpanded = false })
            calendarExpanded -> ({ calendarExpanded = false })
            weatherExpanded -> ({ weatherExpanded = false })
            tripExpanded -> ({ tripExpanded = false })
            appsExpanded -> ({ appsExpanded = false })
            else -> null
        }
    val overlayAction =
        remember(onAction) {
            { action: HomeAction ->
                if (action is HomeAction.OpenAppDrawer) appsExpanded = true else onAction(action)
            }
        }

    val density = LocalDensity.current
    // The dock floats over the map as a rounded panel, inset from its edge by
    // outerPad like the cards; the overlays inset by that whole footprint (the
    // margin + the thickness) so none sit under it.
    val dockExtent = FemtoDimens.DockThickness + outerPad

    // The map's bottom-start attribution credit sits in the bottom-left screen
    // corner. When the horizontal dock is a centred pill it frees that corner, so
    // the credit sits flush there (inset 0) as OSM/OpenFreeMap intend; when the pill
    // would overflow and the dock falls back to a full-width bar (the 853 dp head
    // unit, portrait phones) the credit is lifted by the dock's footprint so it
    // clears the bar. Left/right/top docks never cover that corner.
    val attributionBottomInset =
        if (dockPosition == DockPosition.BOTTOM && !horizontalDockPillFits(maxWidth, dockConfig.visibleNav.size)) {
            dockExtent
        } else {
            0.dp
        }

    // The landscape card column reserves a horizontal band the marker must clear; the
    // bottom (portrait) cards and the bottom dock extend the bottom safe band so the
    // marker clears them. Each orientation feeds one axis. This reserve is assigned to
    // the right OR left safe-fraction below by the driver side; the dock term stays on
    // the RIGHT edge (a left driver with a right dock is a rare combo, out of scope).
    val cardSafeFraction =
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

    // The map's safe-area triple: the card column reserves the driver's side (the
    // right by default, the left when mirrored) plus the bottom band computed above.
    // Only one horizontal reserve is ever non-zero. Changing these pushes camera
    // padding through MapPanel's LaunchedEffect — it never recreates the WebView.
    val mirror = driverSide == DriverSide.LEFT
    val (rightSafeFraction, leftSafeFraction) =
        if (mirror) 0f to cardSafeFraction else cardSafeFraction to 0f

    // The map fills the whole viewport, behind the dock and every overlay; it stays
    // composed as a single instance so the WebView is never torn down and rebuilt.
    MapPanel(
        location = uiState.location,
        mapConfig =
            mapConfig.copy(
                bottomSafeFraction = bottomSafeFraction,
                rightSafeFraction = rightSafeFraction,
                leftSafeFraction = leftSafeFraction,
            ),
        onTap = { onAction(HomeAction.OpenMaps) },
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        recenterNonce = recenterNonce,
        online = uiState.online,
        onFollowChange = { following = it },
        onBearingChange = { bearingDeg = it },
        attributionBottomInset = attributionBottomInset,
    )

    // While a panel is open, the sliver of viewport outside the overlay box —
    // the dock's float margins, over the map — also dismisses on tap, and the
    // map's own tap (OpenMaps) can no longer fire underneath an open panel.
    // Composed under DashboardOverlays so panel bodies and overlay content keep
    // winning hit-testing; the dock, a later sibling, stays operable above.
    if (dismissOpenPanel != null) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .pointerInput(dismissOpenPanel) {
                        detectTapGestures { dismissOpenPanel() }
                    },
        )
    }

    // The overlay tree insets by the dock footprint so its glass never sits under
    // the dock's nav buttons while the map shows through behind it.
    DashboardOverlays(
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
        // Bearing flows down as a deferred read (a lambda), not a Float: it updates
        // at up to ~6.7 Hz while turning in heading-up mode, and reading it here
        // would recompose this whole overlay body and re-run the layout math on
        // every event. MapCompass invokes it inside its graphicsLayer instead,
        // confining the churn to the layer phase.
        bearingDeg = { bearingDeg },
        motionTier = motionTier,
        driverSide = driverSide,
        onRecenter = { recenterNonce++ },
        onOverlayHeightChange = { overlayHeightPx = it },
        onAction = overlayAction,
        nowPlayingExpanded = nowPlayingExpanded,
        onExpandNowPlaying = { nowPlayingExpanded = true },
        onCloseNowPlaying = { nowPlayingExpanded = false },
        calendarExpanded = calendarExpanded,
        onExpandCalendar = { calendarExpanded = true },
        onCloseCalendar = { calendarExpanded = false },
        weatherExpanded = weatherExpanded,
        onExpandWeather = { weatherExpanded = true },
        onCloseWeather = { weatherExpanded = false },
        tripExpanded = tripExpanded,
        onExpandTrip = { tripExpanded = true },
        onCloseTrip = { tripExpanded = false },
        appsExpanded = appsExpanded,
        onCloseApps = { appsExpanded = false },
        dismissOpenPanel = dismissOpenPanel,
        modifier = Modifier.fillMaxSize().padding(dockEdgePadding(dockPosition, dockExtent)),
        spectrum = spectrum,
        musicShowAlbum = musicShowAlbum,
        musicShowArt = musicShowArt,
        clock = clock,
    )

    // The dock as a glass bar / rail on its edge, drawn over the full-bleed map.
    DashboardDock(
        systemStatus = uiState.systemStatus,
        onAction = overlayAction,
        position = dockPosition,
        hazeState = hazeState,
        glassConfig = glassConfig,
        dockConfig = dockConfig,
        motionTier = motionTier,
        modifier =
            when (dockPosition) {
                DockPosition.BOTTOM, DockPosition.TOP -> {
                    // The bar floats off its edges by the shared margin (dockFloatPadding).
                    // HorizontalDock picks its own width from the available space: a
                    // wrap-content centred pill when the fixed-margin layout fits, else a
                    // width-filling weight-shared bar that shrinks the nav to fit. Centre
                    // alignment centres the pill; the fallback fills the inset band.
                    Modifier
                        .align(dockAlignment(dockPosition))
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

// The dashboard's glass overlay tree that floats over the map — map controls, the
// clock and speed overlays, the floating info cards, and the five maximize
// panels. The caller keeps the map (the blur source), the dock, and every
// panel's expanded state composed one level up, outside this tree, and supplies
// the dock-edge inset through [modifier] so the overlays never sit under the
// dock's nav buttons while the map shows through behind it.
@Composable
private fun DashboardOverlays(
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
    // Deferred read forwarded straight to MapCompass (see the call site in the
    // parent for why it is a lambda, not a Float).
    bearingDeg: () -> Float,
    motionTier: MotionTier,
    // Which side the info-dense dashboard anchors to: RIGHT (default) keeps today's
    // layout; LEFT mirrors every alignment/padding site (start <-> end) so the cards,
    // clock, map controls, and speed reserve all flip to the driver's side.
    driverSide: DriverSide,
    onRecenter: () -> Unit,
    onOverlayHeightChange: (Int) -> Unit,
    onAction: (HomeAction) -> Unit,
    // Every panel's expanded state is owned by the parent (see DashboardContent:
    // the hoist lets one dismiss definition drive catchers on both sides of the
    // dock inset); this tree renders the panels and raises the expand/close
    // events.
    nowPlayingExpanded: Boolean,
    onExpandNowPlaying: () -> Unit,
    onCloseNowPlaying: () -> Unit,
    calendarExpanded: Boolean,
    onExpandCalendar: () -> Unit,
    onCloseCalendar: () -> Unit,
    weatherExpanded: Boolean,
    onExpandWeather: () -> Unit,
    onCloseWeather: () -> Unit,
    tripExpanded: Boolean,
    onExpandTrip: () -> Unit,
    onCloseTrip: () -> Unit,
    appsExpanded: Boolean,
    onCloseApps: () -> Unit,
    // Non-null while any panel is open: the inner outside-tap catcher's action.
    dismissOpenPanel: (() -> Unit)?,
    modifier: Modifier = Modifier,
    spectrum: StateFlow<FloatArray?>? = null,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
    clock: Clock = Clock.systemDefaultZone(),
) {
    // Render-caches for the exit animations, derived from uiState (the expanded
    // booleans themselves live in DashboardContent). Hold the last live value so
    // the collapse still renders content when the backing data goes null the
    // same frame the panel starts fading out, rather than flashing empty
    // mid-exit.
    val expandedNowPlaying = (uiState.musicState as? MusicCardState.Playing)?.nowPlaying
    var panelNowPlaying by remember { mutableStateOf(expandedNowPlaying) }
    LaunchedEffect(expandedNowPlaying) {
        if (expandedNowPlaying != null) panelNowPlaying = expandedNowPlaying
    }
    val expandedCalendar = uiState.calendar?.takeIf { it.hasCalendarAccess && !it.queryFailed }
    var panelCalendar by remember { mutableStateOf(expandedCalendar) }
    LaunchedEffect(expandedCalendar) {
        if (expandedCalendar != null) panelCalendar = expandedCalendar
    }
    val expandedWeather = uiState.weather
    var panelWeather by remember { mutableStateOf(expandedWeather) }
    LaunchedEffect(expandedWeather) {
        if (expandedWeather != null) panelWeather = expandedWeather
    }

    // LEFT driver side mirrors the dashboard start <-> end: the cards, clock, and speed
    // reserve move to the left; the map controls (opposite the cards) move to the
    // right. Each site below reduces to its current RIGHT expression when !mirror.
    val mirror = driverSide == DriverSide.LEFT

    Box(modifier = modifier) {
        // Map controls render only when the map does (a fix exists). The compass pins
        // to the top corner opposite the cards; the control column to the mid edge
        // opposite the cards — both flip with the driver side.
        if (uiState.location != null) {
            MapCompass(
                bearingDeg = bearingDeg,
                onTap = { onAction(HomeAction.ToggleMapNorthUp) },
                hazeState = hazeState,
                glassConfig = glassConfig,
                modifier = Modifier.align(if (mirror) Alignment.TopEnd else Alignment.TopStart).padding(outerPad),
            )
            MapControlColumn(
                showLocate = true,
                following = following,
                onLocate = onRecenter,
                onZoomIn = { onAction(HomeAction.AdjustMapZoom(1)) },
                onZoomOut = { onAction(HomeAction.AdjustMapZoom(-1)) },
                hazeState = hazeState,
                glassConfig = glassConfig,
                modifier =
                    Modifier
                        .align(if (mirror) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(if (mirror) PaddingValues(end = outerPad) else PaddingValues(start = outerPad)),
            )
        }

        // Clock beside the date: in landscape it sits just inside the card column's
        // outer edge (the top of that column), reading as a pair with the date; in
        // portrait the column is absent, so it sits in the top corner on the card
        // side, clear of the bottom cards. Both flip to the opposite corner/edge on a
        // LEFT driver side.
        ClockOverlay(
            is24Hour = is24Hour,
            showSeconds = showClockSeconds,
            hazeState = hazeState,
            glassConfig = glassConfig,
            motionTier = motionTier,
            clock = clock,
            modifier =
                Modifier
                    .align(if (mirror) Alignment.TopStart else Alignment.TopEnd)
                    .padding(
                        cardSideInset(
                            mirror = mirror,
                            // The card column's outer margin lives inside floatingCardWidth
                            // (its width is fixed before the padding applies), so the
                            // column's on-screen footprint is floatingCardWidth alone —
                            // adding outerPad on top double-counted the margin and doubled
                            // the clock <-> cards gap relative to every other panel gap.
                            horizontal = if (landscapeCards) floatingCardWidth + cardGap else outerPad,
                            top = outerPad,
                        ),
                    ),
        )

        // Speed overlay centred in the exposed map area above the dock, held clear of
        // the card column (landscape, on the driver's side) or the bottom card band
        // (portrait) by reserving their footprint so it centres in the visible map
        // strip. The horizontal reserve flips to the start edge on a LEFT driver side.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        if (bottomCards) {
                            // Portrait: the card band is a full-width bottom row inset
                            // by outerPad. Match that inset and start-align the
                            // speed/address (below) so their left edge lines up with the
                            // cards instead of floating centred above a full-width band.
                            PaddingValues(
                                start = outerPad,
                                end = outerPad,
                                bottom = cardGap + bottomCardBand,
                            )
                        } else {
                            cardSideInset(
                                mirror = mirror,
                                // floatingCardWidth alone is the column's footprint — see
                                // the clock inset above for the double-count rationale.
                                horizontal = if (landscapeCards) floatingCardWidth + cardGap else 0.dp,
                                bottom = cardGap,
                            )
                        },
                    ),
            // Portrait aligns to the band's start edge; landscape centres in the
            // exposed map strip.
            contentAlignment = if (bottomCards) Alignment.BottomStart else Alignment.BottomCenter,
        ) {
            SpeedOverlay(
                location = uiState.location,
                address = uiState.address,
                tripState = uiState.tripState,
                speedUnit = speedUnit,
                is24Hour = is24Hour,
                onReset = { onAction(HomeAction.ResetTrip) },
                hazeState = hazeState,
                glassConfig = glassConfig,
                motionTier = motionTier,
                onExpand = onExpandTrip,
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
                // Landscape only: portrait's bottom band has no clock adjacency
                // to preserve, so it keeps the calendar-first reading order.
                calendarTrailing = mirror && landscapeCards,
                is24Hour = is24Hour,
                hazeState = hazeState,
                glassConfig = glassConfig,
                onAction = onAction,
                onExpandNowPlaying = onExpandNowPlaying,
                onExpandCalendar = onExpandCalendar,
                onExpandWeather = onExpandWeather,
                spectrum = spectrum,
                musicShowAlbum = musicShowAlbum,
                musicShowArt = musicShowArt,
                motionTier = motionTier,
                modifier =
                    if (bottomCards) {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(bottomCardBand)
                            .padding(horizontal = outerPad, vertical = outerPad)
                    } else {
                        // Cards in a column on the driver's side, top-anchored and
                        // height-capped so they pair with the clock and never stretch;
                        // the map keeps the opposite side. Mirrors to the start edge on
                        // a LEFT driver side.
                        Modifier
                            .align(if (mirror) Alignment.TopStart else Alignment.TopEnd)
                            .width(floatingCardWidth)
                            .heightIn(max = CardClusterMaxHeight)
                            .fillMaxHeight()
                            .padding(
                                cardSideInset(
                                    mirror = mirror,
                                    horizontal = outerPad,
                                    top = outerPad,
                                    bottom = outerPad,
                                ),
                            )
                    },
            )
        }

        // A tap on the margin ring around an open maximize panel dismisses it,
        // matching the modal sheets' scrim-tap behavior. The catcher is drawn
        // under the panels (they are later siblings) and OVER the cards, clock,
        // and speed overlay, so their taps cannot fire behind an open panel;
        // the panel's Surface blocks touch propagation, so panel-body taps
        // never reach it; the dock is outside this Box and stays operable.
        // pointerInput only — no visual scrim (the glass design keeps the map
        // visible) and no semantics node (the back gesture and the collapse
        // button remain the accessible dismiss paths). The dock-margin sliver
        // outside this Box is covered by the outer catcher in DashboardContent,
        // driven by this same [dismissOpenPanel].
        if (dismissOpenPanel != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .pointerInput(dismissOpenPanel) {
                            detectTapGestures { dismissOpenPanel() }
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
                    onClose = onCloseNowPlaying,
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    spectrum = spectrum,
                    showAlbum = musicShowAlbum,
                    showArt = musicShowArt,
                    motionTier = motionTier,
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
                    onClose = onCloseCalendar,
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    motionTier = motionTier,
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
                    onClose = onCloseWeather,
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    motionTier = motionTier,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedVisibility(
            visible = tripExpanded,
            enter = Motion.panelEnter(motionTier),
            exit = Motion.panelExit(motionTier),
            modifier = Modifier.fillMaxSize().padding(outerPad),
        ) {
            // The trip flyover's native path is a media-overlay SurfaceView that
            // the window fade/scale can't touch, so it would pop out at the end of
            // the collapse. Gate the Vulkan surface on the transition being fully
            // settled; while entering/exiting, TripPanel shows the in-window 2D
            // fallback, which fades and scales like the other panels.
            val settled =
                transition.currentState == EnterExitState.Visible &&
                    transition.targetState == EnterExitState.Visible
            TripPanel(
                onClose = onCloseTrip,
                speedUnit = speedUnit,
                settled = settled,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The app launcher, opened by the dock's APPS button (see the parent's
        // OpenAppDrawer interception). Unlike the data-backed panels it needs no
        // "has data" gate: an empty app list renders its own "No apps" state.
        AnimatedVisibility(
            visible = appsExpanded,
            enter = Motion.panelEnter(motionTier),
            exit = Motion.panelExit(motionTier),
            modifier = Modifier.fillMaxSize().padding(outerPad),
        ) {
            AppDrawerPanelHost(
                onClose = onCloseApps,
                hazeState = hazeState,
                glassConfig = glassConfig,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// The horizontal inset for a dashboard overlay that sits on the card side of the
// screen — [horizontal] rides the end edge for the default RIGHT driver and flips to
// the start edge when [mirror] anchors the dashboard to a LEFT driver. [top] / [bottom]
// carry the unchanged vertical insets. Overlays opposite the cards (the map controls)
// invert the alignment themselves; this helper only builds the card-side reserve.
private fun cardSideInset(
    mirror: Boolean,
    horizontal: Dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues =
    if (mirror) {
        PaddingValues(start = horizontal, top = top, bottom = bottom)
    } else {
        PaddingValues(end = horizontal, top = top, bottom = bottom)
    }

// Margins that float the dock off its free edges by [margin] (a vertical rail's
// inner edge faces the dashboard, where the overlay inset already opens the gap).
// A horizontal bar floats off its start / end / hosting edges — the fixed pill
// centres within that inset band and the weight-shared fallback fills it; a
// vertical rail floats off its top / bottom / hosting edges.
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
    calendarTrailing: Boolean,
    is24Hour: Boolean,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onAction: (HomeAction) -> Unit,
    onExpandNowPlaying: () -> Unit,
    onExpandCalendar: () -> Unit,
    onExpandWeather: () -> Unit,
    motionTier: MotionTier,
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
            motionTier = motionTier,
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
            motionTier = motionTier,
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
            onPlay = { onAction(HomeAction.PlayDefaultMusic) },
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = cardModifier,
            spectrum = spectrum,
            showAlbum = musicShowAlbum,
            showArt = musicShowArt,
            motionTier = motionTier,
            // Below the trip's moving-speed floor the vehicle is parked, so long
            // title / artist / album lines may scroll to full length; above it
            // they stay a static ellipsis to keep the ambient card glanceable
            // while driving.
            stationary = uiState.tripState.currentSpeedMs < MIN_MOVING_SPEED_MS,
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(cardGap)) {
        // Calendar + weather pair in a row so each keeps its designed height instead
        // of stacking three full cards into a column too short for them; a single
        // visible card takes the whole row. The row is the only weighted child, so it
        // grows to fill whatever height the content-height music card below leaves.
        if (panels.calendar || panels.weather) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(cardGap),
            ) {
                // The pair reads calendar-first, but a mirrored (LEFT-driver)
                // landscape cluster trails the calendar instead so it rides the
                // clock-facing inner edge — the clock pairs with the calendar
                // on both driver sides.
                listOfNotNull(
                    calendar.takeIf { panels.calendar },
                    weather.takeIf { panels.weather },
                ).let { pair -> if (calendarTrailing) pair.reversed() else pair }
                    .forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        // The music card sizes to its own content height (no weight): the row above
        // takes all the remaining column height, so no space is left as an empty band
        // and the card never stretches past its content on a tall display.
        if (panels.music) music(Modifier.fillMaxWidth())
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
