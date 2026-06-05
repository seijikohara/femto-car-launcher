package io.github.seijikohara.femto.ui.home.components

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPinOff
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.MapRefreshSetting
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.coroutines.resume

/**
 * Map tile surface + permission fallback.
 *
 * Renders free OpenStreetMap vector tiles (OpenFreeMap) through MapLibre's
 * off-screen [MapSnapshotter] as an oblique, heading-up bitmap shown in a
 * Compose [Image]. A live GL `MapView` (Surface/TextureView) never presents
 * frames on the projected / virtualised displays of CarPlay / Android Auto AI
 * boxes — the GL buffers are not scanned out, so the map shows as a grey
 * rectangle (the same failure as the emulator). A snapshot bitmap rides the
 * normal Skia composition path and presents reliably; the snapshotter also
 * yields the oblique 3D view (camera tilt + building fill-extrusion) for free.
 *
 * The snapshot re-renders on movement, throttled by [MapRefreshSetting]. It is
 * single-flight (one render in flight at a time) and holds the previous frame
 * until the next is ready, so there is no flicker. Clock and speed overlays are
 * placed by the parent on top of this surface.
 */
@Composable
internal fun MapPanel(
    location: Location?,
    mapRefresh: MapRefreshSetting,
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
            SnapshotMap(
                location = location,
                mapRefresh = mapRefresh,
                onTap = onTap,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Fallback()
        }
    }
}

@Composable
private fun SnapshotMap(
    location: Location,
    mapRefresh: MapRefreshSetting,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) = BoxWithConstraints(modifier = modifier) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
    val heightPx = with(LocalDensity.current) { maxHeight.roundToPx() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Carry the last non-zero bearing so a stopped vehicle (GPS bearing 0) keeps
    // the heading-up rotation instead of snapping back to north.
    val bearingHolder = remember { floatArrayOf(0f) }
    val currentLocation = rememberUpdatedState(location)

    // One reusable snapshotter, rebuilt only when the panel size or theme changes.
    val snapshotter =
        remember(widthPx, heightPx, isDark) {
            if (widthPx <= 0 || heightPx <= 0) {
                null
            } else {
                MapLibre.getInstance(context)
                MapSnapshotter(
                    context,
                    MapSnapshotter
                        .Options(widthPx, heightPx)
                        .withLogo(false)
                        .withStyleBuilder(styleBuilderFor(context, isDark)),
                )
            }
        }

    DisposableEffect(snapshotter) {
        onDispose { snapshotter?.cancel() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(snapshotter, mapRefresh, lifecycleOwner) {
        val snap = snapshotter ?: return@LaunchedEffect
        val intervalMs = mapRefresh.intervalMs()
        // Render only while the launcher is visible; pause off-screen to drop the
        // render cost. lastRendered resets on each return to STARTED so a stale
        // map refreshes immediately when the dashboard comes back to the front.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastRendered: Location? = null
            while (isActive) {
                val loc = currentLocation.value
                if (shouldRerender(lastRendered, loc)) {
                    val rendered = snap.render(cameraFor(loc, bearingHolder))
                    if (rendered != null) {
                        bitmap = rendered
                        failed = false
                        lastRendered = loc
                    } else {
                        // Keep any previous frame; surface the fallback only when
                        // there is nothing to show yet. The loop retries next tick.
                        failed = bitmap == null
                    }
                }
                delay(intervalMs)
            }
        }
    }

    val current = bitmap
    when {
        current != null -> {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { onTap() },
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
private suspend fun MapSnapshotter.render(camera: CameraPosition): Bitmap? =
    suspendCancellableCoroutine { cont ->
        setCameraPosition(camera)
        start(
            { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap) },
            { _ -> if (cont.isActive) cont.resume(null) },
        )
        cont.invokeOnCancellation { cancel() }
    }

private fun cameraFor(
    location: Location,
    bearingHolder: FloatArray,
): CameraPosition =
    CameraPosition
        .Builder()
        .target(LatLng(location.latitude, location.longitude))
        .zoom(MAP_ZOOM)
        .tilt(MAP_TILT)
        .bearing(location.carriedBearing(bearingHolder).toDouble())
        .build()

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

private fun MapRefreshSetting.intervalMs(): Long =
    when (this) {
        MapRefreshSetting.RESPONSIVE -> RESPONSIVE_INTERVAL_MS
        MapRefreshSetting.BALANCED -> BALANCED_INTERVAL_MS
        MapRefreshSetting.BATTERY_SAVER -> BATTERY_SAVER_INTERVAL_MS
    }

private fun styleBuilderFor(
    context: Context,
    isDark: Boolean,
): Style.Builder {
    val base =
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
    // Add the 3D building layer to the builder before the snapshotter loads it:
    // MapSnapshotter exposes no post-load style to mutate. Both Positron and the
    // bundled dark style carry OpenStreetMap buildings on the same OpenMapTiles
    // "openmaptiles" vector source / "building" source-layer.
    return base.withLayer(buildingExtrusionLayer(isDark))
}

private fun buildingExtrusionLayer(isDark: Boolean): FillExtrusionLayer =
    FillExtrusionLayer(BUILDING_3D_LAYER_ID, OPENMAPTILES_SOURCE_ID).apply {
        setSourceLayer(BUILDING_SOURCE_LAYER)
        setMinZoom(BUILDING_EXTRUSION_MIN_ZOOM)
        setProperties(
            PropertyFactory.fillExtrusionColor(if (isDark) BUILDING_COLOR_DARK else BUILDING_COLOR_LIGHT),
            PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
            PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
            PropertyFactory.fillExtrusionOpacity(BUILDING_OPACITY),
        )
        // Only extrude features that actually carry a height, so flat building
        // polygons without the attribute are left to the base style's fill.
        setFilter(Expression.all(Expression.has("render_height"), Expression.has("render_min_height")))
    }

@Composable
private fun Attribution(modifier: Modifier = Modifier) =
    Text(
        text = stringResource(R.string.map_attribution),
        // Legal credit, not glance content, so it intentionally sits below the
        // body-text floor — OSM ODbL / OpenMapTiles CC-BY require the credit.
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier =
            modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )

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
private const val MAP_TILT = 55.0
internal const val POSITRON_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
private const val DARK_STYLE_ASSET = "map/dark.json"

private const val OPENMAPTILES_SOURCE_ID = "openmaptiles"
private const val BUILDING_SOURCE_LAYER = "building"
private const val BUILDING_3D_LAYER_ID = "building-3d"
private const val BUILDING_EXTRUSION_MIN_ZOOM = 15f
private const val BUILDING_OPACITY = 0.85f
private const val BUILDING_COLOR_LIGHT = "#E7E3DC"
private const val BUILDING_COLOR_DARK = "#2A2E33"

// Re-render once a fix moves at least this far; below it the last frame is held.
private const val REFRESH_DISTANCE_M = 8f

// Snapshot cadence per MapRefreshSetting — the minimum interval between renders.
private const val RESPONSIVE_INTERVAL_MS = 500L
private const val BALANCED_INTERVAL_MS = 1_000L
private const val BATTERY_SAVER_INTERVAL_MS = 3_000L
