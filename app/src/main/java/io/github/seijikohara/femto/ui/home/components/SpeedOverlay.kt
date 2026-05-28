package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.ui.locale.DistanceUnit
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.fromMeters
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlin.math.roundToInt

/**
 * Glass overlay anchored to the map pane's bottom-centre.
 *
 * Combines the speed hero (the only saturated overlay element), an optional
 * altitude row, and the reverse-geocoded short address into a single
 * read-only panel. Splitting these from [MapPanel] keeps the map composable
 * focused on tile rendering and makes the overlay independently previewable.
 */
@Composable
internal fun SpeedOverlay(
    location: Location?,
    address: ShortAddress?,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    val speed = location?.speed?.let { speedUnit.fromMetersPerSecond(it).roundToInt() } ?: 0
    val altitude = location?.altitude?.let { distanceUnit.fromMeters(it).roundToInt() }
    val shortAddress = address?.displayString().orEmpty()
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(FemtoDimens.OverlayCorner + 4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f))
                .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpeedRow(
            speed = speed,
            unitLabel = speedUnit.label(),
            altitudeLabel = altitude,
            distanceLabel = distanceUnit.label(),
        )
        if (shortAddress.isNotBlank()) {
            Box(modifier = Modifier.height(8.dp))
            AddressRow(text = shortAddress)
        }
    }
}

@Composable
private fun SpeedRow(
    speed: Int,
    unitLabel: String,
    altitudeLabel: Int?,
    distanceLabel: String,
) = Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(
        text = "$speed",
        style =
            MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.03f).em,
            ),
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = unitLabel,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    if (altitudeLabel != null) {
        Box(modifier = Modifier.width(12.dp))
        Text(
            text = "↑ $altitudeLabel $distanceLabel",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun AddressRow(text: String) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

@PreviewLightDark
@Preview(name = "Speed overlay", widthDp = 480, heightDp = 140)
@Composable
private fun SpeedOverlayPreview() {
    FemtoTheme {
        SpeedOverlay(
            location = null,
            address = null,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            distanceUnit = DistanceUnit.METERS,
        )
    }
}
