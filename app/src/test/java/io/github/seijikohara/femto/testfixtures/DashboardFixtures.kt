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
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.home.components.MapConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.cos

/**
 * The dashboard render inputs shared by the goldens (`DashboardScreenshotTest`)
 * and the screenshot catalog (`DashboardCatalogTest`), so both draw the same
 * state over the same still map.
 */
internal object DashboardFixtures {
    /** Fixed so the dashboard clock is deterministic across CI record/verify runs. */
    val fixedClock: Clock = Clock.fixed(Instant.parse("2026-05-01T10:08:00Z"), ZoneOffset.UTC)

    val state: HomeUiState = fakeHomeUiState()

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
 * The source is a device capture of this app rendering OpenFreeMap tiles
 * (OpenStreetMap data, ODbL) — see app/src/test/resources/README.md.
 */
@Composable
internal fun MapBackdrop(
    darkTheme: Boolean,
    mapConfig: MapConfig,
) = Box(modifier = Modifier.fillMaxSize()) {
    Image(
        // The app swaps the map style with the theme (Positron / Dark Matter),
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

// Ripple disc radius and opacity; a frozen mid-animation frame of the live CSS
// pulse (index.html's .ripple), which this static render cannot animate.
private val RippleRadius = 24.dp
private val RippleAlpha = 0.18f

// Matches the live marker's <svg width="34" height="34" viewBox="0 0 30 30">
// (webmap/index.html): a square icon box, sized in dp, housing a 30-unit
// viewBox scaled uniformly to fill it.
private val ChevronBoxSize = 34.dp
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
        val xShift = SelfMarkerAnchor.xShift(mapConfig.leftSafeFraction, mapConfig.rightSafeFraction)
        val drop = SelfMarkerAnchor.drop(mapConfig.markerPos, mapConfig.bottomSafeFraction)
        val center = Offset(x = size.width * (0.5f + xShift), y = size.height * (0.5f + drop))

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
        val foreshorten = cos(Math.toRadians(mapConfig.tiltDeg.toDouble())).toFloat()
        scale(scaleX = 1f, scaleY = foreshorten, pivot = center) {
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
