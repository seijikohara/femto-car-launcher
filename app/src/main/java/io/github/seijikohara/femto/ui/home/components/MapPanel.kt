package io.github.seijikohara.femto.ui.home.components

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.location.Location
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
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
import io.github.seijikohara.femto.data.MapRenderMode
import io.github.seijikohara.femto.data.MapStyleSetting
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
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
import kotlin.math.roundToInt
import kotlin.math.sin

// User-tunable map rendering config (derived from DisplaySettings): target frame
// rate, light/dark style, oblique tilt, zoom, and the render resolution percent
// (lower renders a smaller bitmap, faster, upscaled to fill).
internal data class MapConfig(
    val fps: Int = 10,
    val style: MapStyleSetting = MapStyleSetting.AUTO,
    val tiltDeg: Int = 55,
    val zoom: Int = 16,
    val renderPercent: Int = 100,
    val renderMode: MapRenderMode = MapRenderMode.SNAPSHOT,
    val lookAheadM: Int = 180,
)

/**
 * Map tile surface + permission fallback, in one of two backends selected by
 * [MapConfig.renderMode] (both render free OpenStreetMap vector tiles via
 * OpenFreeMap / MapLibre with a heading-up, oblique (tilted) camera):
 *
 * - SNAPSHOT (default, [SnapshotMap]) draws off-screen [MapSnapshotter] bitmaps
 *   into a Compose [Image]. A live GL `MapView` never presents frames on the
 *   projected / virtualised displays of CarPlay / Android Auto AI boxes — the GL
 *   buffers are not scanned out, so it shows a grey rectangle — whereas a bitmap
 *   rides the normal Skia composition path and presents reliably. The snapshot
 *   re-renders on movement, capped at the frame-rate (fps) setting, single-flight,
 *   holding the previous frame so there is no flicker.
 * - LIVE ([LiveMap]) is the GL `MapView`: smoother where the device can scan out
 *   GL, blank where it cannot. Opt-in so the user can test their hardware.
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
    shape = RoundedCornerShape(FemtoDimens.CardCorner),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // A location fix is the only gate: with it we have permission and a
        // centre point; without it the map has nothing to show, so fall back.
        if (location != null) {
            when (mapConfig.renderMode) {
                MapRenderMode.SNAPSHOT -> {
                    SnapshotMap(
                        location = location,
                        mapConfig = mapConfig,
                        onTap = onTap,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                MapRenderMode.LIVE -> {
                    LiveMap(
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

@Composable
private fun SnapshotMap(
    location: Location,
    mapConfig: MapConfig,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) = BoxWithConstraints(modifier = modifier) {
    val context = LocalContext.current
    val isDark =
        when (mapConfig.style) {
            MapStyleSetting.AUTO -> isSystemInDarkTheme()
            MapStyleSetting.LIGHT -> false
            MapStyleSetting.DARK -> true
        }
    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
    val heightPx = with(LocalDensity.current) { maxHeight.roundToPx() }
    // Render at a fraction of the panel resolution; the Image upscales it to fill,
    // so a lower percent rasterizes fewer pixels (faster render, higher achievable
    // frame rate) at the cost of sharpness.
    val renderPercent = mapConfig.renderPercent.coerceIn(1, 100)
    val renderWidthPx = (widthPx * renderPercent / 100).coerceAtLeast(1)
    val renderHeightPx = (heightPx * renderPercent / 100).coerceAtLeast(1)
    // The snapshot pixels are upscaled to layout pixels by this factor, so the
    // marker (placed in layout pixels) scales the snapshot pixel it rendered at.
    val markerScale = widthPx.toFloat() / renderWidthPx

    var frame by remember { mutableStateOf<MapFrame?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Carry the last non-zero bearing so a stopped vehicle (GPS bearing 0) keeps
    // the heading-up rotation instead of snapping back to north.
    val bearingHolder = remember { floatArrayOf(0f) }
    val currentLocation = rememberUpdatedState(location)

    // One reusable snapshotter, rebuilt only when the render size or theme changes.
    val snapshotter =
        remember(renderWidthPx, renderHeightPx, isDark) {
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
                        .withStyleBuilder(styleBuilderFor(context, isDark)),
                )
            }
        }

    DisposableEffect(snapshotter) {
        onDispose { snapshotter?.cancel() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(snapshotter, mapConfig, lifecycleOwner) {
        val snap = snapshotter ?: return@LaunchedEffect
        // Target frame interval; coerceAtLeast(1) guards 0 fps from divide-by-zero.
        val intervalMs = 1_000L / mapConfig.fps.coerceAtLeast(1)
        // Render only while the launcher is visible; pause off-screen to drop the
        // render cost. lastRendered resets on each return to STARTED so a stale
        // map refreshes immediately when the dashboard comes back to the front.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastRendered: Location? = null
            while (isActive) {
                val loc = currentLocation.value
                if (shouldRerender(lastRendered, loc)) {
                    val camera = cameraFor(loc, bearingHolder, mapConfig.tiltDeg, mapConfig.zoom, mapConfig.lookAheadM)
                    val rendered = snap.render(camera, LatLng(loc.latitude, loc.longitude), markerScale)
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
                delay(intervalMs)
            }
        }
    }

    val current = frame
    when {
        current != null -> {
            Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { onTap() },
            )
            LocationMarker(xPx = current.markerX, yPx = current.markerY, tiltDeg = mapConfig.tiltDeg)
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

// A rendered frame plus the pixel where the current location landed, so the
// marker overlay sits exactly on it regardless of the look-ahead camera offset.
private class MapFrame(
    val bitmap: Bitmap,
    val markerX: Float,
    val markerY: Float,
)

// Render one frame off-screen. Suspends until the snapshotter's callback fires,
// keeping the caller single-flight; cancelling the coroutine cancels the render.
private suspend fun MapSnapshotter.render(
    camera: CameraPosition,
    markerLatLng: LatLng,
    markerScale: Float,
): MapFrame? =
    suspendCancellableCoroutine { cont ->
        setCameraPosition(camera)
        start(
            { snapshot ->
                // pixelForLatLng is in snapshot (render-bitmap) pixels; scale to the
                // layout pixels the upscaled Image occupies so the marker lands true.
                val p = snapshot.pixelForLatLng(markerLatLng)
                if (cont.isActive) cont.resume(MapFrame(snapshot.bitmap, p.x * markerScale, p.y * markerScale))
            },
            { _ -> if (cont.isActive) cont.resume(null) },
        )
        cont.invokeOnCancellation { cancel() }
    }

private fun cameraFor(
    location: Location,
    bearingHolder: FloatArray,
    tiltDeg: Int,
    zoom: Int,
    lookAheadM: Int,
): CameraPosition {
    val bearing = location.carriedBearing(bearingHolder).toDouble()
    // Aim the camera ahead of the current position (along the heading) so the
    // current location renders low in the frame: more road ahead is visible and
    // the marker sits just above the speed overlay (nav-style framing). The
    // look-ahead distance is user-tunable so the marker can be placed nearer to
    // (or further from) the speed panel.
    val target = LatLng(location.latitude, location.longitude).offsetForward(bearing, lookAheadM.toDouble())
    return CameraPosition
        .Builder()
        .target(target)
        .zoom(zoom.toDouble())
        .tilt(tiltDeg.toDouble())
        .bearing(bearing)
        .build()
}

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

private fun Location.carriedBearing(holder: FloatArray): Float =
    if (hasBearing() && bearing != 0f) {
        holder[0] = bearing
        bearing
    } else {
        holder[0]
    }

// Re-render only after the fix moves a meaningful distance; a stopped vehicle
// holds the last frame (no wasted off-screen renders). The first fix always
// renders (lastRendered == null).
private fun shouldRerender(
    last: Location?,
    current: Location,
): Boolean = last == null || last.distanceTo(current) >= REFRESH_DISTANCE_M

// Build the (light/dark) base style. A 3D building fill-extrusion layer was
// removed: the MapSnapshotter's GL crashed intermittently (native SIGSEGV in
// glDrawElements on its "Snapshotter" thread) while re-rendering an extrusion
// layer, taking the whole launcher down on movement. The oblique camera tilt
// still gives the map a bird's-eye perspective; only the extruded buildings are
// gone. True extruded 3D needs the live GL MapView, which does not present on the
// projected head-unit display.
private fun styleBuilderFor(
    context: Context,
    isDark: Boolean,
): Style.Builder =
    if (isDark) {
        Style.Builder().fromJson(
            context.assets
                .open(DARK_STYLE_ASSET)
                .bufferedReader()
                .use { it.readText() },
        )
    } else {
        Style.Builder().fromUri(POSITRON_STYLE_URL)
    }

// Live GL backend. A real MapView on a SurfaceView (its own SurfaceFlinger layer)
// with the location component driving a heading-up TRACKING_GPS camera. Smoother
// than the snapshot where the device can scan out GL buffers; on projected /
// virtualised displays that cannot, it shows the fallback's grey rectangle — hence
// SNAPSHOT is default and this is opt-in so the user can test their hardware.
// Shares styleBuilderFor and the OpenFreeMap host with the snapshot path.
@Composable
private fun LiveMap(
    location: Location,
    mapConfig: MapConfig,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark =
        when (mapConfig.style) {
            MapStyleSetting.AUTO -> isSystemInDarkTheme()
            MapStyleSetting.LIGHT -> false
            MapStyleSetting.DARK -> true
        }
    val onTapState = rememberUpdatedState(onTap)
    // Read the latest fix inside the async style-load callback, which fires after
    // the effect captured its closure (the style load can take seconds).
    val currentLocation = rememberUpdatedState(location)
    // Carry the last non-zero bearing so a stopped vehicle keeps its heading.
    val bearingHolder = remember { floatArrayOf(0f) }

    val mapView =
        remember {
            MapLibre.getInstance(context)
            // SurfaceView backend (textureMode = false), not TextureView. A
            // SurfaceView gets its own SurfaceFlinger-composited layer — the path
            // games / Google Maps use — which presents GL on more head units than
            // the TextureView texture-share path (that one showed grey on the
            // projected AI-box display). The snapshot backend stays the default; this
            // is the opt-in LIVE path for hardware that can scan out GL.
            val options =
                MapLibreMapOptions
                    .createFromAttributes(context)
                    .textureMode(false)
            MapView(context, options).apply {
                onCreate(null)
                configureMapGlSurface(this)
            }
        }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    // OpenFreeMap is a no-SLA host: re-key the style effect to retry a transient
    // failure, bounded so a hard outage settles on the fallback.
    var retryTick by remember { mutableIntStateOf(0) }
    var retryAttempt by remember { mutableIntStateOf(0) }

    ForwardLifecycle(mapView)
    ForwardLowMemory(mapView)

    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { loadFailed = true }
        mapView.getMapAsync { ready ->
            ready.uiSettings.apply {
                setAllGesturesEnabled(false)
                isCompassEnabled = false
                isLogoEnabled = false
                // The styled Compose [Attribution] overlay is the single credit.
                isAttributionEnabled = false
            }
            map = ready
        }
    }

    LaunchedEffect(map, isDark, mapConfig.zoom, mapConfig.tiltDeg, retryTick) {
        val ready = map ?: return@LaunchedEffect
        loadFailed = false
        ready.setStyle(styleBuilderFor(context, isDark)) { style ->
            retryAttempt = 0
            activateHeadingUp(context, ready, style, mapConfig.zoom, mapConfig.tiltDeg)
            ready.locationComponent.forceLocationUpdate(currentLocation.value.withCarriedBearing(bearingHolder))
        }
    }

    LaunchedEffect(loadFailed) {
        if (loadFailed && retryAttempt < MAX_RETRIES) {
            delay(RETRY_BASE_DELAY_MS shl retryAttempt)
            retryAttempt += 1
            retryTick += 1
        }
    }

    LaunchedEffect(map, location) {
        map
            ?.locationComponent
            ?.takeIf { it.isLocationComponentActivated }
            ?.forceLocationUpdate(location.withCarriedBearing(bearingHolder))
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clickable { onTapState.value() },
            factory = { mapView },
        )
        if (loadFailed) {
            Fallback(
                modifier = Modifier.clickable {
                    retryAttempt = 0
                    retryTick += 1
                },
            )
        } else {
            Attribution(modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

// Force MapLibre's inner GL SurfaceView to composite like a plain GLSurfaceView:
// an opaque holder with default (non-media-overlay) z-order. A bare opaque
// GLSurfaceView presents fine in the same spot, while MapLibre's translucent,
// media-overlay default surface showed the card through (= grey). This is the
// device-side bet; the emulator cannot judge it (it forces MapLibre onto an
// emulator-only EGL config path that never composites regardless).
private fun configureMapGlSurface(view: View) {
    when (view) {
        is SurfaceView -> {
            view.setZOrderMediaOverlay(false)
            view.holder.setFormat(PixelFormat.OPAQUE)
        }

        is ViewGroup -> {
            (0 until view.childCount).forEach { configureMapGlSurface(view.getChildAt(it)) }
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun ForwardLifecycle(mapView: MapView) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}

// MapView needs onLowMemory() to release its GL tile / glyph caches under
// pressure; lifecycle events alone never deliver it. Head units are RAM-tight, so
// forward both onLowMemory and onTrimMemory at RUNNING_LOW, before the OS starts
// killing background apps.
@Composable
private fun ForwardLowMemory(mapView: MapView) {
    val context = LocalContext.current
    DisposableEffect(context, mapView) {
        val callbacks =
            object : ComponentCallbacks2 {
                override fun onLowMemory() = mapView.onLowMemory()

                @Suppress("DEPRECATION")
                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                        mapView.onLowMemory()
                    }
                }

                override fun onConfigurationChanged(newConfig: Configuration) = Unit
            }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }
}

@SuppressLint("MissingPermission") // LiveMap renders only with a location fix, which implies the grant.
private fun activateHeadingUp(
    context: Context,
    map: MapLibreMap,
    style: Style,
    zoom: Int,
    tiltDeg: Int,
) {
    map.locationComponent.apply {
        activateLocationComponent(
            LocationComponentActivationOptions
                .builder(context, style)
                // The launcher owns a single GPS flow; do not start a second engine.
                .useDefaultLocationEngine(false)
                .build(),
        )
        isLocationComponentEnabled = true
        renderMode = RenderMode.GPS
        cameraMode = CameraMode.TRACKING_GPS
    }
    map.moveCamera(CameraUpdateFactory.zoomTo(zoom.toDouble()))
    map.moveCamera(CameraUpdateFactory.tiltTo(tiltDeg.toDouble()))
}

private fun Location.withCarriedBearing(holder: FloatArray): Location =
    if (hasBearing() && bearing != 0f) {
        holder[0] = bearing
        this
    } else {
        Location(this).apply { bearing = holder[0] }
    }

@Composable
private fun Attribution(modifier: Modifier = Modifier) =
    Text(
        text = stringResource(R.string.map_attribution),
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

// Current-location puck: a heading-up navigation chevron, positioned by the pixel
// the location rendered at (see MapFrame). The map is heading-up, so the chevron
// always points toward the top of the frame (forward); rotationX lays it onto the
// oblique ground plane so it matches the map's tilt and reads as a nav arrow
// resting on the road rather than a flat sticker. offset {} takes layout pixels;
// MapFrame already scaled the snapshot pixel to layout space, so an upscaled
// (sub-100%) render still lands the marker on the true position.
@Composable
private fun LocationMarker(
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
private fun Fallback(modifier: Modifier = Modifier) =
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

// internal so MapSnapshotRenderTest renders the SAME style host / zoom the panel
// uses, keeping the OpenFreeMap style URL and zoom a single source of truth.
internal const val MAP_ZOOM = 16.5
internal const val POSITRON_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
private const val DARK_STYLE_ASSET = "map/dark.json"

// Re-render once a fix moves at least this far; below it the last frame is held.
// Kept small so the map follows movement in smaller steps (smoother panning); the
// single-flight render + fps throttle still bound how often a frame is produced.
private const val REFRESH_DISTANCE_M = 2f

private const val EARTH_RADIUS_M = 6_371_000.0

// Heading-up nav chevron size and the graphicsLayer camera distance (multiplied by
// density) that softens the perspective when the chevron is laid onto the tilt.
private val MARKER_ARROW_SIZE = 30.dp
private const val MARKER_CAMERA_DISTANCE = 12f

// Small attribution type: legal credit only, deliberately tiny so the centred
// speed overlay does not bury it on a narrow map pane.
private val ATTRIBUTION_FONT_SIZE = 8.sp

// Live-map style-reload budget after a load failure: RETRY_BASE_DELAY_MS shifted
// left by the attempt index yields 2s, 4s, 8s before settling on the fallback.
private const val MAX_RETRIES = 3
private const val RETRY_BASE_DELAY_MS = 2_000L
