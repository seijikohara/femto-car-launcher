package io.github.seijikohara.femto.ui.home.components

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Composable
internal fun SnapshotMap(
    location: Location,
    mapConfig: MapConfig,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) = BoxWithConstraints(modifier = modifier) {
    val context = LocalContext.current
    val isDark =
        when (mapConfig.style) {
            MapStyleSetting.AUTO -> LocalFemtoDarkTheme.current
            MapStyleSetting.LIGHT -> false
            MapStyleSetting.DARK -> true
        }
    val styleRef = mapStyleRefFor(if (isDark) mapConfig.schemeDark else mapConfig.schemeLight, isDark)
    val accentColors = accentMapColors(isDark)
    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
    val heightPx = with(LocalDensity.current) { maxHeight.roundToPx() }
    // Render at a fraction of the panel resolution; the Image upscales it to fill,
    // so a lower percent rasterizes fewer pixels (faster render, higher achievable
    // frame rate) at the cost of sharpness.
    val renderPercent = mapConfig.renderPercent.coerceIn(1, 100)
    val renderWidthPx = (widthPx * renderPercent / 100).coerceAtLeast(1)
    val renderHeightPx = (heightPx * renderPercent / 100).coerceAtLeast(1)
    // The chevron sits at a fixed on-screen spot — centre X, markerPos height — and
    // the camera look-ahead aims the location there, so the map slides beneath a
    // still marker (car-nav style) rather than the marker drifting per frame.
    val dropFraction = markerDropFraction(mapConfig.markerPos, mapConfig.bottomSafeFraction)
    val markerXPx = widthPx / 2f
    val markerYPx = (heightPx * (0.5 + dropFraction)).toFloat()

    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }

    // Cross-fade a scheme/style swap: when the style key changes, freeze the
    // outgoing style's last frame underneath and drop the top layer to alpha 0
    // (both layers still show the old bitmap, so nothing flashes); the incoming
    // style's first frame then fades in over the frozen capture. Movement
    // re-renders replace the bitmap directly — only a style swap animates.
    val styleKey = styleRef to accentColors
    var fadeFrom by remember { mutableStateOf<Bitmap?>(null) }
    var lastStyleKey by remember { mutableStateOf<Any?>(null) }
    val fadeIn = remember { Animatable(1f) }
    LaunchedEffect(styleKey) {
        if (lastStyleKey != null && lastStyleKey != styleKey && frame != null) {
            fadeFrom = frame
            fadeIn.snapTo(0f)
        }
        lastStyleKey = styleKey
    }
    LaunchedEffect(frame) {
        if (fadeFrom != null && frame != null && frame !== fadeFrom) {
            fadeIn.animateTo(1f, tween(STYLE_FADE_MS))
            fadeFrom = null
        }
    }
    // Carry the last non-zero bearing so a stopped vehicle (GPS bearing 0) keeps
    // the heading-up rotation instead of snapping back to north.
    val bearingHolder = remember { floatArrayOf(0f) }
    val currentLocation = rememberUpdatedState(location)

    // One reusable snapshotter, rebuilt only when the render size or style changes.
    val snapshotter =
        remember(renderWidthPx, renderHeightPx, styleRef, accentColors) {
            if (widthPx <= 0 || heightPx <= 0) {
                null
            } else {
                MapLibre.getInstance(context)
                MapSnapshotter(
                    context,
                    MapSnapshotter
                        .Options(renderWidthPx, renderHeightPx)
                        .withLogo(false)
                        // Suppress the snapshot's baked-in credit; the styled
                        // Compose [Attribution] overlay is the single source of the
                        // OSM / OpenMapTiles / OpenFreeMap credit (avoids the
                        // duplicate text the baked-in attribution drew on-device).
                        .withAttribution(false)
                        .withStyleBuilder(styleBuilderFor(context, styleRef, accentColors)),
                )
            }
        }

    DisposableEffect(snapshotter) {
        onDispose { snapshotter?.cancel() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(snapshotter, mapConfig, lifecycleOwner) {
        val snap = snapshotter ?: return@LaunchedEffect
        // Render only while the launcher is visible; pause off-screen to drop the
        // render cost. lastRendered resets on each return to STARTED so a stale
        // map refreshes immediately when the dashboard comes back to the front.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastRendered: Location? = null
            while (isActive) {
                val loc = currentLocation.value
                if (shouldRerender(lastRendered, loc)) {
                    val camera =
                        cameraFor(
                            location = loc,
                            bearingHolder = bearingHolder,
                            tiltDeg = mapConfig.tiltDeg,
                            zoom = mapConfig.zoom,
                            markerPos = mapConfig.markerPos,
                            bottomSafeFraction = mapConfig.bottomSafeFraction,
                            renderHeightPx = renderHeightPx,
                        )
                    val rendered = snap.render(camera)
                    if (rendered != null) {
                        frame = rendered
                        failed = false
                        lastRendered = loc
                    } else {
                        // Keep any previous frame; surface the fallback only when
                        // there is nothing to show yet. The loop retries next tick.
                        failed = frame == null
                    }
                }
                // Poll for movement at a fixed cadence; a frame is produced only when
                // the fix actually moves (shouldRerender), so this is a cheap throttle
                // on how often we re-check, not a frame-rate cap.
                delay(SNAPSHOT_POLL_INTERVAL_MS)
            }
        }
    }

    val current = frame
    when {
        current != null -> {
            fadeFrom?.let { outgoing ->
                Image(
                    bitmap = outgoing.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fadeIn.value }
                        .clickable { onTap() },
            )
            LocationMarker(
                xPx = markerXPx,
                yPx = markerYPx,
                tiltDeg = mapConfig.tiltDeg,
                fresh = rememberLocationFresh(location),
            )
        }

        failed -> {
            Fallback()
        }
        // Otherwise the first snapshot is still rendering; the card background shows.
    }
    if (current != null) {
        Attribution(modifier = Modifier.align(Alignment.BottomStart))
    }
}

// Render one frame off-screen. Suspends until the snapshotter's callback fires,
// keeping the caller single-flight; cancelling the coroutine cancels the render.
// The marker is NOT placed from pixelForLatLng: the camera look-ahead already aims
// the location at the fixed on-screen marker spot, so the chevron stays put and the
// map slides beneath it (car-nav style) rather than drifting per frame.
private suspend fun MapSnapshotter.render(camera: CameraPosition): Bitmap? =
    suspendCancellableCoroutine { cont ->
        setCameraPosition(camera)
        start(
            { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap) },
            { error ->
                Log.w(TAG, "snapshot render failed: $error")
                if (cont.isActive) cont.resume(null)
            },
        )
        cont.invokeOnCancellation { cancel() }
    }

// The marker's drop below centre as a fraction of the map height, shared by the
// camera look-ahead and the fixed on-screen marker spot so the two always agree.
// The drop is capped at MAX_MARKER_DROP and additionally clamped so the chevron
// stays above the bottom speed overlay: the lowest the centre may sit is
// 0.5 - bottomSafeFraction (the overlay's measured footprint plus marker
// clearance), so a tall overlay or a short map pane shrinks the usable range
// rather than burying the marker.
private fun markerDropFraction(
    markerPos: Int,
    bottomSafeFraction: Float,
): Double = (markerPos.coerceIn(0, 100) / 100.0) * (0.5 - bottomSafeFraction).coerceIn(0.0, MAX_MARKER_DROP)

private fun cameraFor(
    location: Location,
    bearingHolder: FloatArray,
    tiltDeg: Int,
    zoom: Int,
    markerPos: Int,
    bottomSafeFraction: Float,
    renderHeightPx: Int,
): CameraPosition {
    val bearing = location.carriedBearing(bearingHolder).toDouble()
    // Aim the camera ahead of the current position (along the heading) so the
    // location renders low in the frame, under the fixed on-screen chevron
    // (nav-style framing). [markerPos] (0..100) picks the chevron's screen height: 0
    // keeps it centred, 100 drops it just above the speed overlay. The look-ahead is
    // computed in render-bitmap pixels (renderHeightPx); the bitmap is Crop-upscaled
    // to the layout at the same aspect, so the location lands at the same layout
    // fraction the chevron is pinned to regardless of renderPercent. The tilt makes
    // this approximate, but a fixed chevron stays steady rather than drifting with
    // the per-frame estimate.
    val dropFraction = markerDropFraction(markerPos, bottomSafeFraction)
    val lookAheadM = dropFraction * renderHeightPx * metersPerPixel(zoom, location.latitude)
    val target = LatLng(location.latitude, location.longitude).offsetForward(bearing, lookAheadM)
    return CameraPosition
        .Builder()
        .target(target)
        .zoom(zoom.toDouble())
        .tilt(tiltDeg.toDouble())
        .bearing(bearing)
        .build()
}

// Ground metres per screen pixel for a MapLibre (512-px tile) web-mercator zoom at
// [latDeg]; the basis for turning the marker-position fraction into a look-ahead.
private fun metersPerPixel(
    zoom: Int,
    latDeg: Double,
): Double = EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(latDeg)) / (512.0 * 2.0.pow(zoom))

// Destination point [meters] ahead of this point along [bearingDeg] (great-circle).
private fun LatLng.offsetForward(
    bearingDeg: Double,
    meters: Double,
): LatLng {
    val angular = meters / EARTH_RADIUS_M
    val br = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(latitude)
    val lon1 = Math.toRadians(longitude)
    val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(br))
    val lon2 = lon1 + atan2(sin(br) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
    return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

// Re-render only after the fix moves a meaningful distance; a stopped vehicle
// holds the last frame (no wasted off-screen renders). The first fix always
// renders (lastRendered == null).
private fun shouldRerender(
    last: Location?,
    current: Location,
): Boolean = last == null || last.distanceTo(current) >= REFRESH_DISTANCE_M

// Build the style for the resolved [MapStyleRef]: a hosted OpenFreeMap URL, a
// bundled asset, or the ACCENT scheme (a bundled base recoloured with the Material
// accent). No 3D fill-extrusion is added — the MapSnapshotter's GL crashed
// (native SIGSEGV) re-rendering one; the oblique tilt gives the bird's-eye look and
// extruded 3D needs the live WebGL backend.
private fun styleBuilderFor(
    context: Context,
    styleRef: MapStyleRef,
    accentColors: AccentMapColors,
): Style.Builder =
    when (styleRef) {
        is MapStyleRef.Hosted -> {
            Style.Builder().fromUri(styleRef.url)
        }

        is MapStyleRef.Bundled -> {
            Style.Builder().fromJson(context.readAsset(styleRef.asset))
        }

        is MapStyleRef.Accent -> {
            Style.Builder().fromJson(recolorAccent(context.readAsset(styleRef.baseAsset), accentColors))
        }
    }

private fun Context.readAsset(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

// How often the snapshot loop polls the current fix for movement. A frame is only
// produced when the fix actually moves (shouldRerender), so this just bounds the
// re-check cadence, not the render rate.
private const val SNAPSHOT_POLL_INTERVAL_MS = 200L

// How long a snapshot style swap takes to cross-fade (mirrors STYLE_FADE_MS in
// assets/web/map.html so both map backends transition at the same pace).
private const val STYLE_FADE_MS = 500
private const val TAG = "MapPanel"

// Re-render once a fix moves at least this far; below it the last frame is held.
// Kept small so the map follows movement in smaller steps (smoother panning); the
// single-flight render + fps throttle still bound how often a frame is produced.
private const val REFRESH_DISTANCE_M = 2f
private const val EARTH_RADIUS_M = 6_371_000.0
private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

// The lowest the marker drops as a fraction of the map height below centre at
// markerPos = 100 — kept below 0.5 so the puck stays clear of the speed overlay
// (mirrored by MAX_MARKER_DROP in map.html for the live backend).
private const val MAX_MARKER_DROP = 0.32
