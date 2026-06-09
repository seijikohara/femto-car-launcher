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
import io.github.seijikohara.femto.data.MapColorScheme
import io.github.seijikohara.femto.data.MapRenderMode
import io.github.seijikohara.femto.data.MapStyleSetting
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

// User-tunable map rendering config (derived from DisplaySettings): light/dark
// style, oblique tilt, zoom, the render resolution percent (lower renders a
// smaller bitmap, faster, upscaled to fill), the user-picked render backend, and
// the LIVE-only feature toggles (3D buildings / terrain relief).
internal data class MapConfig(
    val style: MapStyleSetting = MapStyleSetting.AUTO,
    val schemeLight: MapColorScheme = MapColorScheme.ACCENT,
    val schemeDark: MapColorScheme = MapColorScheme.ACCENT,
    val tiltDeg: Int = 55,
    val zoom: Int = 16,
    val renderPercent: Int = 100,
    val renderMode: MapRenderMode = MapRenderMode.SNAPSHOT,
    val markerPos: Int = 70,
    val buildings3d: Boolean = false,
    val terrain: Boolean = false,
)

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
    shape = RoundedCornerShape(FemtoDimens.CardCorner),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
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
    val styleRef = mapStyleRefFor(if (isDark) mapConfig.schemeDark else mapConfig.schemeLight, isDark)
    val accentColors = accentMapColors()
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
                            renderHeightPx = renderHeightPx,
                        )
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
    markerPos: Int,
    renderHeightPx: Int,
): CameraPosition {
    val bearing = location.carriedBearing(bearingHolder).toDouble()
    // Aim the camera ahead of the current position (along the heading) so the
    // location renders low in the frame (nav-style framing). [markerPos] (0..100)
    // picks the marker's screen height: 0 keeps it centred, 100 drops it just above
    // the speed overlay. Convert that to a look-ahead distance via the ground
    // resolution at this zoom/latitude so the same setting reads consistently across
    // zooms (the tilt makes this approximate; pixelForLatLng still places the marker
    // exactly where the location renders).
    val dropFraction = (markerPos.coerceIn(0, 100) / 100.0) * MAX_MARKER_DROP
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

internal fun Location.carriedBearing(holder: FloatArray): Float =
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

// Recolour the background, water, and landcover/landuse/park fills of a style JSON
// with the accent palette, leaving roads and labels (legible against the base) as
// they are. The same three layer groups are recoloured in map.html for the live
// backend; keep the two in sync.
private fun recolorAccent(
    styleJson: String,
    colors: AccentMapColors,
): String {
    val root = JSONObject(styleJson)
    val layers = root.optJSONArray("layers") ?: return styleJson
    for (i in 0 until layers.length()) {
        val layer = layers.getJSONObject(i)
        val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
        val sourceLayer = layer.optString("source-layer")
        when {
            layer.optString("type") == "background" -> {
                paint.put("background-color", colors.background)
            }

            layer.optString("type") == "fill" && sourceLayer == "water" -> {
                paint.put("fill-color", colors.water)
            }

            layer.optString("type") == "fill" && sourceLayer in ACCENT_LAND_LAYERS -> {
                paint.put("fill-color", colors.land)
            }
        }
    }
    return root.toString()
}

private val ACCENT_LAND_LAYERS = setOf("landcover", "landuse", "park")

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

// internal so MapSnapshotRenderTest renders the SAME zoom the panel uses, keeping
// it a single source of truth (POSITRON_STYLE_URL lives in MapScheme.kt).
internal const val MAP_ZOOM = 16.5

// How often the snapshot loop polls the current fix for movement. A frame is only
// produced when the fix actually moves (shouldRerender), so this just bounds the
// re-check cadence, not the render rate.
private const val SNAPSHOT_POLL_INTERVAL_MS = 200L

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

// Heading-up nav chevron size and the graphicsLayer camera distance (multiplied by
// density) that softens the perspective when the chevron is laid onto the tilt.
private val MARKER_ARROW_SIZE = 30.dp
private const val MARKER_CAMERA_DISTANCE = 12f

// Small attribution type: legal credit only, deliberately tiny so the centred
// speed overlay does not bury it on a narrow map pane.
private val ATTRIBUTION_FONT_SIZE = 8.sp
