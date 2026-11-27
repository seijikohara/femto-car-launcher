package io.github.seijikohara.femto.testfixtures

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.RouteProgress
import io.github.seijikohara.femto.data.routing.RouteRowTime
import io.github.seijikohara.femto.ui.home.ActiveRouteState
import io.github.seijikohara.femto.ui.home.CandidateSummary
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.home.RouteChoiceState
import io.github.seijikohara.femto.ui.home.TurnCardState
import io.github.seijikohara.femto.ui.home.components.ChevronHalfBox
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.chevronSquash
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The dashboard render inputs shared by the goldens (`DashboardScreenshotTest`)
 * and the screenshot catalog (`DashboardCatalogTest`), so both draw the same
 * state over the same still map.
 */
internal object DashboardFixtures {
    /** Fixed so the dashboard clock is deterministic across CI record/verify runs. */
    val fixedClock: Clock = Clock.fixed(Instant.parse("2026-05-01T10:08:00Z"), ZoneOffset.UTC)

    val state: HomeUiState = fakeHomeUiState()

    /**
     * [state] with an active route (issue #389 phase 3), as a nightly build on the OSM backend shows it: 12.4 km and
     * 18 minutes left, which the fixed clock turns into a 10:26 arrival. The still map draws no line; the speed
     * readout's route row is what the goldens hold.
     */
    val routeState: HomeUiState =
        state.copy(
            activeRoute =
                ActiveRouteState(
                    line = fakeRoute().points,
                    progress = RouteProgress(remainingMeters = 12_400.0, remainingSeconds = 1_080.0),
                ),
        )

    /** [routeState] with the route row set to the time remaining (Settings → Navigation): "18 min". */
    val routeRemainingState: HomeUiState =
        routeState.copy(activeRoute = routeState.activeRoute?.copy(rowTime = RouteRowTime.REMAINING))

    /** [routeState] while the launcher reroutes after the vehicle left the route: the row says so in place of the ETA. */
    val reroutingState: HomeUiState =
        routeState.copy(activeRoute = routeState.activeRoute?.copy(reroute = RerouteStatus.REROUTING))

    /**
     * [routeState] just after the route was found: the choice card lists it and a second route, the first one
     * followed, and searches for a third.
     */
    val routeChoiceFindingState: HomeUiState =
        routeState.withChoice(
            RouteChoiceState(
                session = 1L,
                candidates = FixtureRouteCandidates.take(2),
                selected = 0,
                finding = true,
                searchCompleted = false,
            ),
        )

    /** [routeState] once three routes were found and the driver chose the second: the card at its tallest. */
    val routeChoiceState: HomeUiState =
        routeState.withChoice(
            RouteChoiceState(
                session = 1L,
                candidates = FixtureRouteCandidates,
                selected = 1,
                finding = false,
                searchCompleted = true,
            ),
        )

    /**
     * [routeState] with the turn card (issue #389 phase 4): a left turn 300 m ahead. The route states above carry no
     * card, so their goldens hold the dashboard as it was before the card.
     */
    val routeTurnState: HomeUiState = routeState.withTurnCard(TurnCardFixtures.turn)

    /** [routeTurnState] with a right turn following closely: the card's small arrow under "Then". */
    val routeTurnThenState: HomeUiState = routeState.withTurnCard(TurnCardFixtures.turnThen)

    /** [routeState] near its end, 1.2 km and 2 minutes left with no turn left: the card shows the destination. */
    val routeArriveState: HomeUiState =
        routeState.copy(
            activeRoute =
                routeState.activeRoute?.copy(
                    progress = RouteProgress(remainingMeters = 1_240.0, remainingSeconds = 120.0),
                    turnCard = TurnCardFixtures.arrive,
                ),
        )

    private fun HomeUiState.withChoice(choice: RouteChoiceState): HomeUiState =
        copy(activeRoute = activeRoute?.copy(choice = choice))

    private fun HomeUiState.withTurnCard(turnCard: TurnCardState): HomeUiState =
        copy(activeRoute = activeRoute?.copy(turnCard = turnCard))

    // Decoded once per JVM. The captures are smaller than the widest geometry
    // (2000x1200), so Crop upscales there — acceptable because the backdrop is
    // scenery behind the UI, not a subject under test, and the renders exist to
    // show what the app draws over it. Enlarging the asset would add megabytes
    // to every golden re-record for pixels nothing asserts on.
    val backdrop: ImageBitmap = backdrop("/map-backdrop-osm.png")

    val backdropDark: ImageBitmap = backdrop("/map-backdrop-osm-dark.png")

    private fun backdrop(resource: String): ImageBitmap =
        checkNotNull(DashboardFixtures::class.java.getResourceAsStream(resource)) {
            "$resource missing from test resources"
        }.use { stream ->
            // decodeStream returns null on a corrupt or unreadable file; without
            // this the failure surfaces as an NPE inside asImageBitmap with no
            // clue which resource was at fault.
            checkNotNull(BitmapFactory.decodeStream(stream)) { "$resource could not be decoded" }
        }.asImageBitmap()
}

/**
 * Still OSM capture standing in for the live map, with the self-marker drawn
 * on top.
 *
 * Robolectric's WebView is a shadow with no Chromium behind it, so the real
 * [io.github.seijikohara.femto.ui.home.components.WebMapView] can only ever
 * paint an empty region here — and a render that fetched live tiles would stop
 * being deterministic. This keeps every pixel the app itself draws (cards,
 * dock, overlays, controls) generated from the current code. The map imagery
 * is a pinned capture, and the self-marker chevron — which the live page
 * draws into the WebView DOM, invisible to Robolectric — is reproduced by
 * [SelfMarker] from the same [MapConfig] placement inputs (see
 * [SelfMarkerAnchor]); both change only when the map style or marker design
 * does.
 *
 * The source is a render of this app's map page drawing OpenFreeMap tiles
 * (OpenStreetMap data, ODbL) — see app/src/test/resources/README.md.
 */
@Composable
internal fun MapBackdrop(
    darkTheme: Boolean,
    mapConfig: MapConfig,
) = Box(modifier = Modifier.fillMaxSize()) {
    Image(
        // The app swaps the map style with the theme (Standard light / dark),
        // so the still has to swap too — a light map under dark glass chrome
        // would misrepresent the product, not just look odd.
        bitmap = if (darkTheme) DashboardFixtures.backdropDark else DashboardFixtures.backdrop,
        contentDescription = null,
        // Crop, not Fit: the renders span landscape and portrait geometries, and
        // a letterboxed backdrop would show bars the running app never has.
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    SelfMarker(mapConfig)
}

// The routes on the fixture choice card: the route followed in routeState, a
// longer and slower one, and a longer but quicker one over an hour.
private val FixtureRouteCandidates =
    listOf(
        CandidateSummary(distanceMeters = 12_400, durationSeconds = 1_080),
        CandidateSummary(distanceMeters = 13_900, durationSeconds = 1_260),
        CandidateSummary(distanceMeters = 61_500, durationSeconds = 3_900),
    )

// Ripple disc radius and opacity; a frozen mid-animation frame of the live CSS
// pulse (index.html's .ripple), which this static render cannot animate.
private val RippleRadius = 24.dp
private val RippleAlpha = 0.18f

// The live marker's square icon box (ChevronHalfBox), housing a 30-unit
// viewBox scaled uniformly to fill it.
private val ChevronBoxSize = ChevronHalfBox * 2
private val ChevronViewBoxSize = 30f
private val ChevronStrokeWidth = 1.5.dp

/**
 * Approximates `webmap/index.html`'s `#self-marker` — the DOM chevron the
 * live WebView draws for the vehicle's own position — since Robolectric's
 * WebView shadow never renders it. Same screen anchor as the live page
 * ([SelfMarkerAnchor], mirroring `webmap/src/style.ts`), same chevron path
 * and colors ([io.github.seijikohara.femto.ui.home.components.WebMapView]
 * pushes [MaterialTheme.colorScheme.primary] as the live fill), heading-up
 * (no rotation) since that is the follow-mode default.
 */
@Composable
private fun SelfMarker(mapConfig: MapConfig) {
    val chevronColor = MaterialTheme.colorScheme.primary
    val rippleColor = chevronColor.copy(alpha = RippleAlpha)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerFraction = mapConfig.selfMarkerCenterFraction()
        val center = Offset(x = size.width * centerFraction.x, y = size.height * centerFraction.y)

        // Map the SVG path's 30-unit viewBox coordinates into the 34dp icon
        // box centred on the anchor point.
        val boxPx = ChevronBoxSize.toPx()
        val viewBoxScale = boxPx / ChevronViewBoxSize
        val originX = center.x - boxPx / 2f
        val originY = center.y - boxPx / 2f

        fun viewBoxPoint(
            x: Float,
            y: Float,
        ) = Offset(originX + x * viewBoxScale, originY + y * viewBoxScale)

        // webmap/index.html's chevron path: "M15 0 L30 30 L15 21.6 L0 30 Z".
        val chevron =
            Path().apply {
                moveTo(viewBoxPoint(15f, 0f).x, viewBoxPoint(15f, 0f).y)
                lineTo(viewBoxPoint(30f, 30f).x, viewBoxPoint(30f, 30f).y)
                lineTo(viewBoxPoint(15f, 21.6f).x, viewBoxPoint(15f, 21.6f).y)
                lineTo(viewBoxPoint(0f, 30f).x, viewBoxPoint(0f, 30f).y)
                close()
            }

        // The live page lays the whole #self-marker (ripple included) onto the
        // pitched ground plane with perspective(600px) rotateX(tiltDeg), where
        // tiltDeg is the map pitch the same MapConfig drives. A vertical squash
        // by cos(tilt) is that transform's first-order effect; the perspective
        // term on a 34dp element is under 3 % and not worth a 3D projection.
        scale(scaleX = 1f, scaleY = mapConfig.markerForeshortening(), pivot = center) {
            drawCircle(color = rippleColor, radius = RippleRadius.toPx(), center = center)
            drawPath(chevron, color = chevronColor)
            drawPath(
                chevron,
                color = Color.White,
                style = Stroke(width = ChevronStrokeWidth.toPx(), join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * The box [SelfMarker] draws its chevron in on a map of [width] × [height], squashed by the map's pitch as it is
 * drawn: what a layout test checks an overlay leaves visible.
 */
internal fun selfMarkerChevronBounds(
    mapConfig: MapConfig,
    width: Dp,
    height: Dp,
): DpRect {
    val center = mapConfig.selfMarkerCenterFraction()
    val halfWidth = ChevronBoxSize / 2
    val halfHeight = halfWidth * mapConfig.markerForeshortening()
    return DpRect(
        left = width * center.x - halfWidth,
        top = height * center.y - halfHeight,
        right = width * center.x + halfWidth,
        bottom = height * center.y + halfHeight,
    )
}

// Where the chevron's centre sits, as fractions of the map's width and height.
private fun MapConfig.selfMarkerCenterFraction(): Offset =
    Offset(
        x = 0.5f + SelfMarkerAnchor.xShift(leftSafeFraction, rightSafeFraction),
        y = 0.5f + SelfMarkerAnchor.drop(markerPos, bottomSafeFraction),
    )

// The marker's vertical squash on the pitched ground plane (see SelfMarker).
private fun MapConfig.markerForeshortening(): Float = chevronSquash(tiltDeg)
