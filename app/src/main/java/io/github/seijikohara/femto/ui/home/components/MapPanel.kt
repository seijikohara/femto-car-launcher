package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.ui.locale.DistanceUnit
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.fromMeters
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import kotlin.math.roundToInt

@Composable
internal fun MapPanel(
    location: Location?,
    address: ShortAddress?,
    mapAvailable: Boolean,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
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
        }
        OverlaysScrim(modifier = Modifier.fillMaxSize())
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(FemtoDimens.GridGutter),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SpeedAltitudeOverlay(
                location = location,
                speedUnit = speedUnit,
                distanceUnit = distanceUnit,
                modifier = Modifier.testTag("speedAltitudeOverlay"),
            )
            AddressOverlay(
                address = address,
                modifier = Modifier.testTag("addressOverlay"),
            )
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
private fun OverlaysScrim(modifier: Modifier = Modifier) =
    Box(
        modifier =
            modifier.background(
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                        ),
                ),
            ),
    )

@Composable
private fun SpeedAltitudeOverlay(
    location: Location?,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.Bottom,
) {
    val speed = location?.speed?.let { speedUnit.fromMetersPerSecond(it).roundToInt() } ?: 0
    val altitude = location?.altitude?.let { distanceUnit.fromMeters(it).roundToInt() }
    Text(
        text = "$speed",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = " ${speedUnit.label()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
    if (altitude != null) {
        Text(
            text = "↑ $altitude ${distanceUnit.label()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun AddressOverlay(
    address: ShortAddress?,
    modifier: Modifier = Modifier,
) = Text(
    text = address?.displayString().orEmpty(),
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = modifier,
)

private const val MAP_ZOOM = 15f
