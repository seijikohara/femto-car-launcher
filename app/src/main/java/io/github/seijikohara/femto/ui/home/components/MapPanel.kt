package io.github.seijikohara.femto.ui.home.components

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPinOff
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Map tile + permission fallback only.
 *
 * Renders free OpenStreetMap vector tiles (OpenFreeMap) through MapLibre
 * with a heading-up camera. Clock and speed overlays live in their own
 * composables (see [ClockOverlay], [SpeedOverlay]) and are placed by the
 * parent on top of this surface inside a shared [Box]. Keeping the map pane
 * focused makes the overlay positions explicit at the call site and lets
 * each piece be previewed in isolation.
 */
@Composable
internal fun MapPanel(
    location: Location?,
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
            VectorMap(
                location = location,
                onTap = onTap,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Fallback()
        }
    }
}

@Composable
private fun VectorMap(
    location: Location,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val onTapState = rememberUpdatedState(onTap)

    // Heading-up needs a bearing on every pushed fix. GPS fixes only carry one
    // while moving, so carry the last non-zero bearing forward; otherwise
    // CameraMode.TRACKING_GPS would snap the map back to north when stopped.
    val bearingHolder = remember { floatArrayOf(0f) }

    val mapView =
        remember {
            MapLibre.getInstance(context)
            // textureMode(true) renders the map into a TextureView (drawn inline
            // in the view hierarchy), so it is clipped by the parent Surface's
            // rounded corners and the Compose overlays (clock / speed) sit cleanly
            // on top. translucentTextureSurface(false) forces the buffer opaque so
            // the surfaceContainer behind it cannot bleed through, and saves a
            // blend pass on real GPUs. (On software-GL emulators the emulator
            // cannot composite this surface into a screencap, but it renders on
            // real head-unit GPUs — see MainActivity.enableEmulatorMapRendering.)
            val options =
                MapLibreMapOptions
                    .createFromAttributes(context)
                    .textureMode(true)
                    .translucentTextureSurface(false)
            // onCreate(null) is mandatory before getMapAsync; without it the
            // ready callback never fires and the map silently stays blank.
            MapView(context, options).apply { onCreate(null) }
        }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    // OpenFreeMap is a no-SLA host: a single transient style/tile failure must
    // not strand the fallback forever. retryTick re-keys the style effect to
    // re-run setStyle; retryAttempt bounds the automatic retries so a hard
    // outage settles on the fallback instead of looping indefinitely.
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
                // Keep the attribution control: OSM ODbL / OpenMapTiles CC-BY
                // require the credit to stay reachable from the map.
                isAttributionEnabled = true
            }
            ready.addOnMapClickListener {
                onTapState.value()
                true
            }
            map = ready
        }
    }

    // Re-apply the style (and re-activate the location layer it owns) whenever
    // the map becomes ready, the system theme flips, or a retry is scheduled.
    // retryTick is in the key so a bumped tick re-runs setStyle.
    LaunchedEffect(map, isDark, retryTick) {
        val ready = map ?: return@LaunchedEffect
        loadFailed = false
        ready.setStyle(styleFor(context, isDark)) { style ->
            // A completed style load means the host answered: clear the attempt
            // budget so a later, unrelated failure starts its backoff fresh.
            retryAttempt = 0
            activateHeadingUp(context, ready, style)
            ready.locationComponent.forceLocationUpdate(location.withCarriedBearing(bearingHolder))
        }
    }

    // When a load fails, schedule a bounded, exponentially backed-off retry by
    // bumping retryTick. The delay grows 2s, 4s, 8s so a flapping host is given
    // room to recover without hammering it; after MAX_RETRIES the fallback stays.
    LaunchedEffect(loadFailed) {
        if (loadFailed && retryAttempt < MAX_RETRIES) {
            delay(RETRY_BASE_DELAY_MS shl retryAttempt)
            retryAttempt += 1
            retryTick += 1
        }
    }

    // Push each new fix to the location component; TRACKING_GPS recentres and
    // rotates the camera from the fix's position and (carried) bearing.
    LaunchedEffect(map, location) {
        val ready = map ?: return@LaunchedEffect
        ready.locationComponent
            .takeIf { it.isLocationComponentActivated }
            ?.forceLocationUpdate(location.withCarriedBearing(bearingHolder))
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
        )
        // OpenFreeMap is a free, no-SLA service; a failed style/tile load shows
        // the static fallback rather than a blank or black rectangle. A tap
        // forces an immediate retry: reset the budget so the user gets a fresh
        // round of backed-off attempts even after the automatic ones are spent.
        if (loadFailed) {
            Fallback(
                modifier =
                    Modifier.clickable {
                        retryAttempt = 0
                        retryTick += 1
                    },
            )
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
// pressure; lifecycle events alone never deliver it. Head units are RAM-tight,
// so forward both the explicit onLowMemory callback and onTrimMemory once the
// system reaches RUNNING_LOW, before the OS starts killing background apps.
@Composable
private fun ForwardLowMemory(mapView: MapView) {
    val context = LocalContext.current
    DisposableEffect(context, mapView) {
        val callbacks =
            object : ComponentCallbacks2 {
                override fun onLowMemory() = mapView.onLowMemory()

                // onTrimMemory(level) is the live callback; only the TRIM_MEMORY_RUNNING_LOW
                // level constant is deprecated on API 34+. Suppress narrowly here so the
                // low-memory forwarding keeps firing across API levels without warnings.
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

private fun styleFor(
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

@SuppressLint("MissingPermission") // VectorMap renders only with a location fix, which implies the grant.
private fun activateHeadingUp(
    context: Context,
    map: MapLibreMap,
    style: Style,
) = map.locationComponent.apply {
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
    map.moveCamera(CameraUpdateFactory.zoomTo(MAP_ZOOM))
}

private fun Location.withCarriedBearing(holder: FloatArray): Location =
    if (hasBearing() && bearing != 0f) {
        holder[0] = bearing
        this
    } else {
        Location(this).apply { bearing = holder[0] }
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

private const val MAP_ZOOM = 15.0
private const val POSITRON_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
private const val DARK_STYLE_ASSET = "map/dark.json"

// Automatic style-reload budget after a load failure. The delay is
// RETRY_BASE_DELAY_MS shifted left by the attempt index, yielding 2s, 4s, 8s.
private const val MAX_RETRIES = 3
private const val RETRY_BASE_DELAY_MS = 2_000L
