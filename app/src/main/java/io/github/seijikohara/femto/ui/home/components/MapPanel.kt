package io.github.seijikohara.femto.ui.home.components

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.location.Location
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPinOff
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapRenderMode
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.location.isFresh
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Map tile surface + permission fallback, in one of three backends selected
 * explicitly by [MapConfig.renderMode] (all render free OpenStreetMap vector
 * tiles via OpenFreeMap / MapLibre with a heading-up, oblique (tilted) camera):
 *
 * - SNAPSHOT (default, [SnapshotMap]) draws off-screen [MapSnapshotter] bitmaps
 *   into a Compose [Image]. A native live GL `MapView` never presents frames on
 *   the projected / virtualised displays of CarPlay / Android Auto AI boxes — the
 *   GL buffers are not scanned out, so it shows a grey rectangle — whereas a
 *   bitmap rides the normal Skia composition path and presents reliably. The
 *   snapshot re-renders on movement, single-flight, holding the previous frame so
 *   there is no flicker.
 * - LIVE ([WebMapView]) renders MapLibre GL JS (WebGL) in a hardware-accelerated
 *   WebView, which composites inline through HWUI and animates the camera for a
 *   smooth follow. There is NO auto-fallback: the chosen backend is kept as-is.
 *
 * Clock and speed overlays are placed by the parent on top of this surface.
 */
@Composable
internal fun MapPanel(
    location: Location?,
    mapConfig: MapConfig,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    recenterNonce: Int = 0,
    onFollowChange: (Boolean) -> Unit = {},
    onBearingChange: (Float) -> Unit = {},
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // A location fix is the only gate: with it we have permission and a
        // centre point; without it the map has nothing to show, so fall back.
        if (location != null) {
            when (mapConfig.renderMode) {
                MapRenderMode.LIVE -> {
                    WebMapView(
                        location = location,
                        mapConfig = mapConfig,
                        onTap = onTap,
                        modifier = Modifier.fillMaxSize(),
                        recenterNonce = recenterNonce,
                        onFollowChange = onFollowChange,
                        onBearingChange = onBearingChange,
                    )
                }

                MapRenderMode.SNAPSHOT -> {
                    SnapshotMap(
                        location = location,
                        mapConfig = mapConfig,
                        onTap = onTap,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            Fallback()
        }
    }
}

internal fun Location.carriedBearing(holder: FloatArray): Float =
    if (hasBearing() && bearing != 0f) {
        holder[0] = bearing
        bearing
    } else {
        holder[0]
    }

@Composable
internal fun Attribution(
    modifier: Modifier = Modifier,
    showTerrainCredit: Boolean = false,
) {
    // Append the terrain provider's required credit when that LIVE layer is active
    // (its licence mandates attribution); the base OSM / OpenMapTiles / OpenFreeMap
    // credit always shows.
    val base = stringResource(R.string.map_attribution)
    val terrain = stringResource(R.string.map_attribution_terrain)
    val text = base + (if (showTerrainCredit) " · $terrain" else "")
    Text(
        text = text,
        // Legal credit, not glance content — OSM ODbL / OpenMapTiles CC-BY require
        // it but it is not read on the move, so it sits well below the body-text
        // floor and is shrunk further here so the centred speed overlay does not
        // bury it on a narrow map pane.
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = ATTRIBUTION_FONT_SIZE,
            lineHeight = ATTRIBUTION_FONT_SIZE,
        ),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier =
            modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * Re-evaluate [location] freshness on a 1 s tick so the marker can grey out when
 * fixes stop arriving (a tunnel): the location flow forwards each fix verbatim
 * and never emits null on signal loss, so a stale fix would otherwise read as
 * live forever. A new fix restarts the tick (the key changes) and re-greens the
 * marker. Once stale, the loop stops — nothing changes until the next fix.
 */
@Composable
internal fun rememberLocationFresh(location: Location): Boolean =
    produceState(initialValue = true, location) {
        while (true) {
            value = location.isFresh(SystemClock.elapsedRealtimeNanos())
            if (!value) break
            delay(1_000L)
        }
    }.value

// Current-location puck: a heading-up navigation chevron at a fixed on-screen spot
// (the camera look-ahead keeps the location there, so the chevron stays still while
// the map slides beneath it). The map is heading-up, so the chevron always points
// toward the top of the frame (forward); rotationX lays it onto the oblique ground
// plane so it matches the map's tilt and reads as a nav arrow resting on the road
// rather than a flat sticker.
//
// While [fresh] the chevron is the Material primary and emits a restrained ripple
// (a live-position cue); once the fix is stale it greys out and the ripple stops,
// matching the LIVE chevron's `.stale` state in webmap (main.ts).
@Composable
internal fun LocationMarker(
    xPx: Float,
    yPx: Float,
    tiltDeg: Int,
    fresh: Boolean,
    modifier: Modifier = Modifier,
) {
    // The box is larger than the chevron so the ripple has room to expand beyond
    // the arrow; the chevron is drawn centred at its own size inside it.
    val half = with(LocalDensity.current) { MARKER_BOX_SIZE.toPx() } / 2f
    val fill = if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val outlinePx = with(LocalDensity.current) { 1.5.dp.toPx() }
    Box(
        modifier =
            modifier
                .offset { IntOffset((xPx - half).roundToInt(), (yPx - half).roundToInt()) }
                .size(MARKER_BOX_SIZE)
                .graphicsLayer {
                    // Lay the chevron back onto the tilted ground plane so it sits in
                    // the same perspective as the map. transformOrigin centres the
                    // pivot on the rendered pixel; cameraDistance softens the foreshorten.
                    rotationX = tiltDeg.toFloat()
                    cameraDistance = MARKER_CAMERA_DISTANCE * density
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
    ) {
        // Composed only while fresh, so the infinite animation (and the
        // recompositions it drives) stops entirely once the fix goes stale. Drawn
        // before the chevron, so it sits behind the arrow.
        if (fresh) {
            MarkerRipple(color = MaterialTheme.colorScheme.primary, strokeWidthPx = outlinePx)
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            // An upward arrowhead with a concave tail notch (the classic nav chevron):
            // tip at top-centre, the two base corners, and a notch rising from the base.
            // Centred at MARKER_ARROW_SIZE within the larger ripple box.
            val arrow = MARKER_ARROW_SIZE.toPx()
            val left = (size.width - arrow) / 2f
            val top = (size.height - arrow) / 2f
            val chevron =
                Path().apply {
                    moveTo(left + arrow / 2f, top)
                    lineTo(left + arrow, top + arrow)
                    lineTo(left + arrow / 2f, top + arrow * 0.72f)
                    lineTo(left, top + arrow)
                    close()
                }
            drawPath(chevron, color = fill)
            drawPath(chevron, color = Color.White, style = Stroke(width = outlinePx))
        }
    }
}

// One fading pulse per period: an expanding disc with a ring on its leading edge
// so it reads clearly. A separate composable so the host only places it while the
// fix is fresh — keeping the infinite animation out of the tree when it would
// draw nothing.
@Composable
private fun MarkerRipple(
    color: Color,
    strokeWidthPx: Float,
    modifier: Modifier = Modifier,
) {
    val ripple = rememberInfiniteTransition(label = "marker-ripple")
    val progress by ripple.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MARKER_RIPPLE_PERIOD_MS, easing = LinearEasing)),
        label = "marker-ripple-progress",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = (size.minDimension / 2f) * progress
        val fade = 1f - progress
        drawCircle(color = color.copy(alpha = MARKER_RIPPLE_MAX_ALPHA * fade), radius = radius, center = center)
        drawCircle(
            color = color.copy(alpha = MARKER_RIPPLE_EDGE_ALPHA * fade),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidthPx),
        )
    }
}

@Composable
internal fun Fallback(modifier: Modifier = Modifier) =
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Lucide.MapPinOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.map_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.map_permission_cta),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

// Heading-up nav chevron size, the larger box that gives the ripple room to
// expand past the arrow, and the graphicsLayer camera distance (multiplied by
// density) that softens the perspective when the chevron is laid onto the tilt.
private val MARKER_ARROW_SIZE = 30.dp

// Ripple headroom around the arrow; its 64 dp equality with
// FemtoDimens.MinTouchTarget is coincidental — this is not a tap target.
private val MARKER_BOX_SIZE = 64.dp
private const val MARKER_CAMERA_DISTANCE = 12f

// Live-position ripple: one disc per period expanding to the box edge, with a
// faint ring on its leading edge so the pulse stays legible. Peak opacity is
// kept moderate — a clear pulse, not a beacon. Mirrored in webmap (main.ts).
private const val MARKER_RIPPLE_PERIOD_MS = 2_800
private const val MARKER_RIPPLE_MAX_ALPHA = 0.38f
private const val MARKER_RIPPLE_EDGE_ALPHA = 0.55f

// Small attribution type: legal credit only, deliberately tiny so the centred
// speed overlay does not bury it on a narrow map pane.
private val ATTRIBUTION_FONT_SIZE = 8.sp
