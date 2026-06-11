package io.github.seijikohara.femto.ui.home.components

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.location.Location
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
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

// Current-location puck: a heading-up navigation chevron at a fixed on-screen spot
// (the camera look-ahead keeps the location there, so the chevron stays still while
// the map slides beneath it). The map is heading-up, so the chevron always points
// toward the top of the frame (forward); rotationX lays it onto the oblique ground
// plane so it matches the map's tilt and reads as a nav arrow resting on the road
// rather than a flat sticker.
@Composable
internal fun LocationMarker(
    xPx: Float,
    yPx: Float,
    tiltDeg: Int,
    modifier: Modifier = Modifier,
) {
    val half = with(LocalDensity.current) { MARKER_ARROW_SIZE.toPx() } / 2f
    val fill = MaterialTheme.colorScheme.primary
    val outlinePx = with(LocalDensity.current) { 1.5.dp.toPx() }
    Box(
        modifier =
            modifier
                .offset { IntOffset((xPx - half).roundToInt(), (yPx - half).roundToInt()) }
                .size(MARKER_ARROW_SIZE)
                .graphicsLayer {
                    // Lay the chevron back onto the tilted ground plane so it sits in
                    // the same perspective as the map. transformOrigin centres the
                    // pivot on the rendered pixel; cameraDistance softens the foreshorten.
                    rotationX = tiltDeg.toFloat()
                    cameraDistance = MARKER_CAMERA_DISTANCE * density
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // An upward arrowhead with a concave tail notch (the classic nav chevron):
            // tip at top-centre, the two base corners, and a notch rising from the base.
            val chevron =
                Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(w, h)
                    lineTo(w / 2f, h * 0.72f)
                    lineTo(0f, h)
                    close()
                }
            drawPath(chevron, color = fill)
            drawPath(chevron, color = Color.White, style = Stroke(width = outlinePx))
        }
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

// Heading-up nav chevron size and the graphicsLayer camera distance (multiplied by
// density) that softens the perspective when the chevron is laid onto the tilt.
private val MARKER_ARROW_SIZE = 30.dp
private const val MARKER_CAMERA_DISTANCE = 12f

// Small attribution type: legal credit only, deliberately tiny so the centred
// speed overlay does not bury it on a narrow map pane.
private val ATTRIBUTION_FONT_SIZE = 8.sp
