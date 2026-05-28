package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * Map tile + permission fallback only.
 *
 * Clock and speed overlays live in their own composables (see
 * [ClockOverlay], [SpeedOverlay]) and are placed by the parent on top of
 * this surface inside a shared [Box]. Keeping the map pane focused makes
 * the overlay positions explicit at the call site and lets each piece be
 * previewed in isolation.
 */
@Composable
internal fun MapPanel(
    location: Location?,
    mapAvailable: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = RoundedCornerShape(FemtoDimens.OverlayCorner),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (mapAvailable && location != null) {
            LiteModeMap(
                latLng = LatLng(location.latitude, location.longitude),
                onTap = onTap,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Fallback()
        }
    }
}

@Composable
private fun LiteModeMap(
    latLng: LatLng,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onTap)
    val mapView =
        remember {
            MapView(context, GoogleMapOptions().liteMode(true).mapToolbarEnabled(false).compassEnabled(false)).apply {
                onCreate(null)
            }
        }
    LaunchedEffect(latLng) {
        mapView.getMapAsync { map ->
            map.uiSettings.setAllGesturesEnabled(false)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, MAP_ZOOM))
            map.setOnMapClickListener { callback.value() }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { mapView },
    )
}

@Composable
private fun Fallback() =
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = "Map unavailable",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Grant location access to enable the map and overlays.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

private const val MAP_ZOOM = 15f
