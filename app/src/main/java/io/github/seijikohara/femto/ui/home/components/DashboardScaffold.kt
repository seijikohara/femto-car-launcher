package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.clock.SystemZoneClock
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DockWidth
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.MotionTier
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

// The width a horizontal bar lays out in on a [viewportWidth] dashboard: the
// viewport less the start and end margins dockFloatPadding insets the floating
// bar by. Read off dockFloatPadding rather than restating the margin, so the two
// move together. The layout direction only decides which of the two margins is
// the start one; their sum — all this needs — is the same either way.
private fun horizontalDockWidth(
    viewportWidth: Dp,
    dockMargin: Dp,
): Dp {
    // BOTTOM stands in for either horizontal position: dockFloatPadding gives
    // BOTTOM and TOP the same start/end margins, and only their sum is read here.
    val floatPadding = dockFloatPadding(DockPosition.BOTTOM, dockMargin)
    return viewportWidth -
        floatPadding.calculateStartPadding(LayoutDirection.Ltr) -
        floatPadding.calculateEndPadding(LayoutDirection.Ltr)
}

// Whether the map's bottom-start attribution credit must be lifted clear of the
// dock instead of sitting flush in the corner as OSM/OpenFreeMap intend. Only a
// bottom-hosted dock reaches that corner, and only while the bar spans the width:
// a centred pill leaves the corner free.
//
// [viewportWidth] is the dashboard viewport, NOT the bar's own width, so this
// converts (horizontalDockWidth) before asking horizontalDockUsesPill. That
// conversion is the whole point of taking [dockMargin]: HorizontalDock answers
// the same question against the width it was actually given, and handing this
// one the raw viewport instead put the two answers a float margin apart —
// enough to disagree in real width bands and leave the credit under a
// full-width bar. Per-backend map attribution is an invariant here, so this
// stays a second evaluation of the SAME predicate on the SAME width; changing
// either side's inputs means changing both.
internal fun mapCreditClearsDock(
    dockPosition: DockPosition,
    dockWidth: DockWidth,
    viewportWidth: Dp,
    dockMargin: Dp,
    navCount: Int,
    statusCount: Int,
): Boolean {
    if (dockPosition != DockPosition.BOTTOM) return false
    val barWidth = horizontalDockWidth(viewportWidth, dockMargin)
    if (horizontalDockUsesPill(dockWidth, barWidth, navCount, statusCount)) return false
    // A bar wider than the content cap is clamped and centred (see
    // extendedDockMaxWidth), so it no longer reaches the bottom-start corner and
    // the credit belongs flush in it again.
    return barWidth <= extendedDockMaxWidth(dockShowsStatus(barWidth, navCount, statusCount))
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
 * |           [clock  ·   date ]  |   | (o)   [clock ·  date] |
 * | [marker]  [calend][weather]   |   | [marker]   map        |
 * |  map      [music   ]          |   |  speed                |
 * |  speed [== dock ========== ]  |   | [calend][weather]     |
 * +-------------------------------+   | [music            ]   |
 *  (the dock is glass, over the map)  | [== dock ======== ]   |
 *                                     +-----------------------+
 * ```
 *
 * Landscape floats the cards in a right-hand column headed by the clock + date
 * band ([DashboardHeader]) — the calendar and weather share the row below it
 * side by side and grow to fill the column, the music card sits at the bottom
 * (in its compact form when the column is too short to afford the full card
 * beside a usable row, and alone — the row yielding whole — when no form of
 * it leaves a readable row; see the card cluster in [FloatingCardColumn]). Portrait
 * lays the cards along the bottom and runs the header as a band along the top
 * edge instead, beside the compass and no wider than the landscape column. With
 * every card hidden the header alone keeps the column's slot (landscape) or the
 * top band (portrait), so the clock never disappears with the cards.
 * The self-marker is offset to stay in the exposed map region — left of
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
    dockWidth: DockWidth = DockWidth.COMPACT,
    dockConfig: DockConfig = DockConfig(),
    driverSide: DriverSide = DriverSide.RIGHT,
    spectrum: StateFlow<FloatArray?>? = null,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
    motionTier: MotionTier = MotionTier.STANDARD,
    clock: Clock = SystemZoneClock,
    // Forwarded to MapPanel; null means the real WebView map. Screenshot tests
    // pass a still capture (see MapPanel.mapSurface).
    mapSurface: (@Composable (Location) -> Unit)? = null,
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
    dockWidth = dockWidth,
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
    mapSurface = mapSurface,
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
    dockWidth: DockWidth,
    driverSide: DriverSide,
    modifier: Modifier = Modifier,
    dockConfig: DockConfig = DockConfig(),
    spectrum: StateFlow<FloatArray?>? = null,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
    motionTier: MotionTier = MotionTier.STANDARD,
    clock: Clock = SystemZoneClock,
    // Forwarded to MapPanel; null means the real WebView map. Screenshot tests
    // pass a still capture (see MapPanel.mapSurface).
    mapSurface: (@Composable (Location) -> Unit)? = null,
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
    var overlaySizePx by remember { mutableStateOf(IntSize.Zero) }

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

    // A horizontal dock beside a landscape card column takes one of three
    // placements, so it never ends as a pill straddling the column's edge (a
    // centred pill did, 55 dp short of the edge at 1024x600, and off the speed
    // overlay's axis on every 16:9 panel): the pill centred in the MAP STRIP
    // (the width left of the column, less the bar's float margins) when it fits
    // there; else the weight-shared bar filling the strip, when the strip holds
    // it at the tap-target floor with the same status cluster the full width
    // would show; else the bar across the full width, whose end lines up with
    // the column's edge. The first two share the speed overlay's centre. The
    // fit tests are the ones HorizontalDock runs, so the two agree on the
    // layout; forcing the bar goes through the same DockWidth preference the
    // user's setting does, so HorizontalDock needs no second switch.
    val navCount = dockConfig.visibleNav.size
    val statusCount = dockConfig.visibleStatus.size
    val horizontalDockBesideColumn =
        landscapeCards && (dockPosition == DockPosition.BOTTOM || dockPosition == DockPosition.TOP)
    val stripBarWidth = maxWidth - floatingCardWidth - cardGap - outerPad * 2
    val pillFitsStrip =
        horizontalDockBesideColumn && horizontalDockUsesPill(dockWidth, stripBarWidth, navCount, statusCount)
    val barFitsStrip =
        horizontalDockBesideColumn &&
            !pillFitsStrip &&
            stripBarWidth >= horizontalDockBarMinWidth(navCount, statusCount) &&
            dockShowsStatus(stripBarWidth, navCount, statusCount) ==
            dockShowsStatus(horizontalDockWidth(maxWidth, outerPad), navCount, statusCount)
    val dockCentresInStrip = pillFitsStrip || barFitsStrip
    val effectiveDockWidth = if (horizontalDockBesideColumn && !pillFitsStrip) DockWidth.EXTENDED else dockWidth

    val attributionBottomInset =
        if (mapCreditClearsDock(
                dockPosition = dockPosition,
                dockWidth = effectiveDockWidth,
                viewportWidth = maxWidth,
                dockMargin = outerPad,
                navCount = dockConfig.visibleNav.size,
                statusCount = dockConfig.visibleStatus.size,
            )
        ) {
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
    // The map control rail's height budget: the room above the speed overlay
    // where the two share a column. The overlay is the width it measured (it
    // hugs its metrics up to a cap), centred in the map strip beside a
    // landscape card column or on the full width otherwise — it meets the rail
    // only when that centred card reaches the rail's edge; in portrait it spans
    // the band over the rail's column, so it always does. Zero means the rail
    // has the whole overlay box (see MapControlRail for what it yields).
    val overlaySize = with(density) { DpSize(overlaySizePx.width.toDp(), overlaySizePx.height.toDp()) }
    val overlayStrip = if (landscapeCards) maxWidth - floatingCardWidth - cardGap else maxWidth
    val overlayReachesRail = (overlayStrip - overlaySize.width) / 2 < outerPad + MapControlsStripWidth + cardGap
    val railBottomReserve =
        when {
            bottomCards -> bottomCardBand + overlaySize.height + cardGap
            overlayReachesRail -> overlaySize.height + cardGap * 2
            else -> 0.dp
        }
    val bottomSafeFraction =
        with(density) {
            val heightPx = maxHeight.roundToPx()
            if (heightPx > 0) {
                val dockBottom = if (dockPosition == DockPosition.BOTTOM) dockExtent else 0.dp
                val overlay = overlaySizePx.height + (cardGap + MarkerOverlayClearance + dockBottom).toPx()
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
        mapSurface = mapSurface,
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
        portrait = portrait,
        bottomCardBand = bottomCardBand,
        floatingCardWidth = floatingCardWidth,
        hasCards = hasCards,
        // A phone's portrait band is narrower than the speed overlay's width cap, so
        // the overlay spans it edge to edge; a tablet's is wider and the cap holds.
        speedSpansBand = bottomCards && maxWidth - outerPad * 2 <= FemtoDimens.SpeedOverlayMaxWidth,
        railBottomReserve = railBottomReserve,
        hazeState = hazeState,
        following = following,
        // Bearing flows down as a deferred read (a lambda), not a Float: it updates
        // at up to ~6.7 Hz while turning in heading-up mode, and reading it here
        // would recompose this whole overlay body and re-run the layout math on
        // every event. MapControlRail invokes it inside its graphicsLayer instead,
        // confining the churn to the layer phase.
        bearingDeg = { bearingDeg },
        motionTier = motionTier,
        driverSide = driverSide,
        onRecenter = { recenterNonce++ },
        onOverlaySizeChange = { overlaySizePx = it },
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
        dockWidth = effectiveDockWidth,
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
                    // Beside a landscape card column the pill centres in the MAP STRIP
                    // instead — the same centre the speed overlay uses — so the two
                    // share one axis and the pill's end never lands inside the column's
                    // span; only when the pill fits the strip, so a bar that needs the
                    // full width (the 853 dp head unit) keeps it and its status cluster.
                    Modifier
                        .align(dockAlignment(dockPosition))
                        .padding(dockFloatPadding(dockPosition, outerPad))
                        .then(
                            if (dockCentresInStrip) {
                                Modifier.padding(
                                    cardSideInset(mirror = mirror, horizontal = floatingCardWidth + cardGap),
                                )
                            } else {
                                Modifier
                            },
                        )
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
// speed overlay, the floating info cards under their clock + date header, and
// the five maximize panels. The caller keeps the map (the blur source), the dock, and every
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
    // The viewport's orientation on its own (the two flags above fold in
    // hasCards): the card-less header placement still needs it.
    portrait: Boolean,
    bottomCardBand: Dp,
    floatingCardWidth: Dp,
    hasCards: Boolean,
    // Portrait only: the speed overlay fills the band's width (both of its edges
    // line up with the cards) when the band is no wider than the overlay's cap.
    speedSpansBand: Boolean,
    // Bottom inset that bounds the map control rail's height to the room above
    // the speed overlay where the two would meet (see the parent), so the rail
    // yields its lowest segments instead of running under the overlay.
    railBottomReserve: Dp,
    hazeState: HazeState,
    following: Boolean,
    // Deferred read forwarded straight to MapControlRail (see the call site in the
    // parent for why it is a lambda, not a Float).
    bearingDeg: () -> Float,
    motionTier: MotionTier,
    // Which side the info-dense dashboard anchors to: RIGHT (default) keeps today's
    // layout; LEFT mirrors every alignment/padding site (start <-> end) so the cards,
    // clock, map controls, and speed reserve all flip to the driver's side.
    driverSide: DriverSide,
    onRecenter: () -> Unit,
    onOverlaySizeChange: (IntSize) -> Unit,
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
    clock: Clock = SystemZoneClock,
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
        // Map controls render only when the map does (a fix exists): one rail —
        // compass, zoom, locate — pinned to the top corner opposite the cards,
        // flipping with the driver side. Top-anchored, it clears the speed overlay
        // (bottom-anchored) and the portrait card band on every recorded geometry;
        // the mid-edge column it replaces sat under both on short and portrait
        // screens, its zoom buttons unreachable (see MapControlRail).
        if (uiState.location != null) {
            MapControlRail(
                bearingDeg = bearingDeg,
                onCompassTap = { onAction(HomeAction.ToggleMapNorthUp) },
                showLocate = true,
                following = following,
                onLocate = onRecenter,
                onZoomIn = { onAction(HomeAction.AdjustMapZoom(1)) },
                onZoomOut = { onAction(HomeAction.AdjustMapZoom(-1)) },
                hazeState = hazeState,
                glassConfig = glassConfig,
                modifier =
                    Modifier
                        .align(if (mirror) Alignment.TopEnd else Alignment.TopStart)
                        .padding(outerPad)
                        .padding(bottom = railBottomReserve),
            )
        }

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
                            // by outerPad on every side, so its own top inset is the gap
                            // to the overlay — no cardGap on top of it, or the overlay
                            // would sit two gaps above the cards while the cards sit one
                            // apart. Match the band's side inset and start-align the
                            // speed/address (below) so their left edge lines up with the
                            // cards instead of floating centred above a full-width band.
                            PaddingValues(
                                start = outerPad,
                                end = outerPad,
                                bottom = bottomCardBand,
                            )
                        } else {
                            cardSideInset(
                                mirror = mirror,
                                // The card column's outer margin lives inside
                                // floatingCardWidth (its width is fixed before the padding
                                // applies), so the column's on-screen footprint is
                                // floatingCardWidth alone — adding outerPad on top would
                                // double-count the margin.
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
                modifier =
                    Modifier
                        .onSizeChanged { onOverlaySizeChange(it) }
                        // A phone's band is narrower than the overlay's width cap, so
                        // the overlay spans it and both edges line up with the cards
                        // below; on a tablet the cap holds and the card stays
                        // start-aligned (SpeedOverlay's own widthIn cap would lose to
                        // an outer fillMaxWidth, so the choice is made here).
                        .then(if (speedSpansBand) Modifier.fillMaxWidth() else Modifier),
            )
        }

        if (hasCards) {
            FloatingCardColumn(
                uiState = uiState,
                temperatureUnit = temperatureUnit,
                speedUnit = speedUnit,
                panels = panels,
                cardGap = cardGap,
                // The landscape column hosts the clock + date header as its first
                // child; portrait runs it as a top strip instead (below).
                showHeader = landscapeCards,
                is24Hour = is24Hour,
                showClockSeconds = showClockSeconds,
                clock = clock,
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
                        // height-capped so they never stretch; the map keeps the
                        // opposite side. Mirrors to the start edge on a LEFT driver side.
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
        if (!landscapeCards) {
            // The header outside the cluster. Portrait, always: a band along the
            // top edge on the card side, clear of the control rail in the opposite
            // corner, so the clock stays up top where the eye expects it instead of
            // sinking into the bottom band beneath the speed overlay — and the band
            // keeps its height for the cards. It fills a phone's width and caps at
            // the landscape column's width on a tablet, so the time and the date
            // stay a readable pair rather than drifting to opposite screen edges.
            // Landscape, only with every card hidden: the header keeps the column's
            // slot, so the clock never disappears with the cards. Otherwise it is
            // the column's first child (FloatingCardColumn).
            val railReserve = outerPad + MapControlsStripWidth + cardGap
            DashboardHeader(
                is24Hour = is24Hour,
                showSeconds = showClockSeconds,
                hazeState = hazeState,
                glassConfig = glassConfig,
                motionTier = motionTier,
                clock = clock,
                modifier =
                    if (portrait) {
                        Modifier
                            .align(if (mirror) Alignment.TopStart else Alignment.TopEnd)
                            .padding(
                                start = if (mirror) outerPad else railReserve,
                                end = if (mirror) railReserve else outerPad,
                                top = outerPad,
                            ).widthIn(max = FloatingCardWidthMax)
                            .fillMaxWidth()
                    } else {
                        Modifier
                            .align(if (mirror) Alignment.TopStart else Alignment.TopEnd)
                            .width(floatingCardWidth)
                            .padding(cardSideInset(mirror = mirror, horizontal = outerPad, top = outerPad))
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
                    // Same motion gate as the card below it: the panel's lines
                    // scroll while parked and rest as an ellipsis once moving.
                    stationary = uiState.tripState.stationary,
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

// The floating info cluster: the clock + date header (landscape) over the
// calendar+weather row over the music card, hosted in the landscape right column
// or the portrait bottom band. Each piece gets the shared glass treatment so the
// map shows through; CardCluster arbitrates the height between them.
@Composable
private fun FloatingCardColumn(
    uiState: HomeUiState,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
    panels: PanelVisibility,
    cardGap: Dp,
    showHeader: Boolean,
    is24Hour: Boolean,
    showClockSeconds: Boolean,
    clock: Clock,
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
    val music: @Composable (Modifier, Boolean) -> Unit = { cardModifier, compact ->
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
            // Parked, long title / artist / album lines may scroll to full
            // length; moving, they stay a static ellipsis to keep the ambient
            // card glanceable while driving.
            stationary = uiState.tripState.stationary,
            compact = compact,
        )
    }
    val header: @Composable () -> Unit = {
        // The clock + date header tops the cluster at the column's full width. It
        // belongs to the column, not to a card, so hiding any card keeps it; the
        // full width is what lets the time (seconds included) and the date share
        // one band on the head-unit geometry (see DashboardHeader).
        DashboardHeader(
            is24Hour = is24Hour,
            showSeconds = showClockSeconds,
            hazeState = hazeState,
            glassConfig = glassConfig,
            motionTier = motionTier,
            clock = clock,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val row: @Composable () -> Unit = {
        // Calendar + weather pair in a row so each keeps its designed height instead
        // of stacking three full cards into a column too short for them; a single
        // visible card takes the whole row. Calendar-first on both driver sides:
        // with the clock in the header there is no clock-facing edge for it to ride.
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(cardGap),
        ) {
            listOfNotNull(
                calendar.takeIf { panels.calendar },
                weather.takeIf { panels.weather },
            ).forEach { card -> card(Modifier.weight(1f).fillMaxHeight()) }
        }
    }
    val musicCard: @Composable (Boolean) -> Unit = { compact -> music(Modifier.fillMaxWidth(), compact) }
    CardCluster(
        header = header.takeIf { showHeader },
        row = row.takeIf { panels.calendar || panels.weather },
        music = musicCard.takeIf { panels.music },
        musicSample = { compact ->
            MusicCardPlayingHeightSample(showAlbum = musicShowAlbum && !compact, showProgress = !compact)
        },
        cardGap = cardGap,
        modifier = modifier,
    )
}

// The cluster's vertical arbitration. A plain Column let the calendar / weather
// row — its only weighted child — absorb every shortfall while the header and
// the content-height music card kept their natural heights, so a short column
// (the LARGE display scale on a 512 dp head unit, a phone in landscape) squeezed
// the row into a sliver. Here the row has two floors, measured against unplaced
// samples of the music card (MusicCardPlayingHeightSample) before anything is
// composed for real: beside the full card the row must clear CardRowMinHeight,
// else the card is composed in its compact form (MusicCard.compact); beside the
// compact card it must still clear CardRowFloorHeight, else the row yields whole
// and the card takes the fullest form the column affords. Deciding before
// composing — the subcompose pattern BoxWithConstraints is built on — means
// exactly one form of the card exists and a yielded row is absent, not merely
// unplaced: no second set of transport buttons, no hidden agenda, for TalkBack
// or a test to find. The row takes whatever height the header and the card
// leave; without a row the card follows the header at its own height.
@Composable
private fun CardCluster(
    header: (@Composable () -> Unit)?,
    row: (@Composable () -> Unit)?,
    music: (@Composable (compact: Boolean) -> Unit)?,
    musicSample: @Composable (compact: Boolean) -> Unit,
    cardGap: Dp,
    modifier: Modifier = Modifier,
) = SubcomposeLayout(modifier = modifier) { constraints ->
    val width = constraints.maxWidth
    val gapPx = cardGap.roundToPx()
    // Every piece spans the column; heights are the pieces' own.
    val spanning = Constraints(minWidth = width, maxWidth = width)
    val headerPlaceable = header?.let { subcompose(ClusterSlot.HEADER, it).single().measure(spanning) }
    val headerSpan = headerPlaceable?.let { it.height + gapPx } ?: 0
    val plan =
        if (music != null && row != null && constraints.hasBoundedHeight) {
            val sampleHeight = { compact: Boolean ->
                subcompose(
                    if (compact) ClusterSlot.MUSIC_SAMPLE_COMPACT else ClusterSlot.MUSIC_SAMPLE,
                ) { musicSample(compact) }
                    .single()
                    .measure(spanning)
                    .height
            }
            val rowBeside = { musicHeight: Int -> constraints.maxHeight - headerSpan - musicHeight - gapPx }
            val fullMusic = sampleHeight(false)
            when {
                rowBeside(
                    fullMusic,
                ) >= CardRowMinHeight.roundToPx() -> ClusterPlan(compactMusic = false, showRow = true)

                rowBeside(
                    sampleHeight(true),
                ) >= CardRowFloorHeight.roundToPx() -> ClusterPlan(compactMusic = true, showRow = true)

                // No form of the card leaves a readable row: the row yields, and the
                // card takes its full form when the column holds it after the header.
                else -> ClusterPlan(compactMusic = headerSpan + fullMusic > constraints.maxHeight, showRow = false)
            }
        } else {
            ClusterPlan(compactMusic = false, showRow = row != null)
        }
    val musicPlaceable = music?.let {
        subcompose(
            ClusterSlot.MUSIC,
        ) { it(plan.compactMusic) }.single().measure(spanning)
    }
    val musicSpan = musicPlaceable?.let { it.height + gapPx } ?: 0
    val rowPlaceable =
        row?.takeIf { plan.showRow }?.let {
            val rowConstraints =
                if (constraints.hasBoundedHeight) {
                    Constraints.fixed(width, (constraints.maxHeight - headerSpan - musicSpan).coerceAtLeast(0))
                } else {
                    spanning
                }
            subcompose(ClusterSlot.ROW, it).single().measure(rowConstraints)
        }
    val rowSpan = rowPlaceable?.let { it.height + gapPx } ?: 0
    // Spans carry a trailing gap each; the last piece's gap is not laid out.
    val natural = (headerSpan + rowSpan + musicSpan - gapPx).coerceAtLeast(0)
    layout(width, constraints.constrainHeight(natural)) {
        headerPlaceable?.placeRelative(0, 0)
        rowPlaceable?.placeRelative(0, headerSpan)
        musicPlaceable?.placeRelative(0, headerSpan + rowSpan)
    }
}

// The cluster's decision for one measure pass (see CardCluster).
private data class ClusterPlan(
    val compactMusic: Boolean,
    val showRow: Boolean,
)

private enum class ClusterSlot { HEADER, MUSIC_SAMPLE, MUSIC_SAMPLE_COMPACT, MUSIC, ROW }

// The two floors under the calendar / weather row (see CardCluster). Above
// CardRowMinHeight — the calendar head with its next entry, or the weather head
// with one forecast row: the least that still answers "what is next" at a
// glance — the row sits beside the full music card. Down to CardRowFloorHeight
// the music card yields its compact form and the row still shows both card
// heads whole (the eyebrow and the hero numeral under the card padding). Below
// it no form of the card leaves a readable row, so the row yields whole and its
// height returns to the map.
private val CardRowMinHeight: Dp = 120.dp
private val CardRowFloorHeight: Dp = 72.dp

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
