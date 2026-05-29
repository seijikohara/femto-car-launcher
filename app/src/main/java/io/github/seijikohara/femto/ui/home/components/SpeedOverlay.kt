package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.TripState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlin.math.roundToInt

/**
 * Glass overlay anchored to the map pane's bottom-centre, per
 * `docs/design/dashboard-v2-mockup.html` `.speed-overlay`:
 *
 *  - Three-cell grid: hero current speed | 1 dp × 36 dp separator |
 *    distance | separator | average speed.
 *  - Below: a 1 dp top border, then a MapPin · short-address row.
 *  - 20 dp corner radius, 1 dp outline border, 460 dp minimum width
 *    so the layout reads identically across head-units that may be
 *    slightly wider than 1280 px.
 *
 * The 40 sp speed numeral is the only saturated value here; the
 * supporting metrics use `onSurface` / `onSurfaceVariant` so the hero
 * number reads as the "thing you glance at" on the move.
 */
@Composable
internal fun SpeedOverlay(
    location: Location?,
    address: ShortAddress?,
    tripState: TripState,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier,
) {
    val currentSpeed = location?.speed?.let { speedUnit.fromMetersPerSecond(it).roundToInt() } ?: 0
    val distanceKm = tripState.distanceMeters / 1000.0
    val avgSpeed = speedUnit.fromMetersPerSecond(tripState.avgSpeedMs.toFloat()).roundToInt()
    val shortAddress = address?.displayString().orEmpty()
    Column(
        modifier =
            modifier
                .widthIn(min = 460.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp),
                ).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        MetricRow(
            currentSpeed = currentSpeed,
            speedUnitLabel = speedUnit.label(),
            distanceKm = distanceKm,
            avgSpeed = avgSpeed,
        )
        if (shortAddress.isNotBlank()) {
            Box(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(modifier = Modifier.height(10.dp))
            AddressRow(text = shortAddress)
        }
    }
}

@Composable
private fun MetricRow(
    currentSpeed: Int,
    speedUnitLabel: String,
    distanceKm: Double,
    avgSpeed: Int,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    NowMetric(value = currentSpeed, unit = speedUnitLabel)
    Separator()
    SecondaryMetric(key = "DISTANCE", value = "%.1f km".format(distanceKm), modifier = Modifier.weight(1f))
    Separator()
    SecondaryMetric(key = "AVG.", value = "$avgSpeed $speedUnitLabel", modifier = Modifier.weight(1f))
}

@Composable
private fun NowMetric(
    value: Int,
    unit: String,
) = Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(
        text = "$value",
        style =
            MaterialTheme.typography.displayMedium.copy(
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.03f).em,
                lineHeight = 40.sp,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
    Text(
        text = unit,
        style =
            MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.12f.em,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 4.dp),
        maxLines = 1,
    )
}

@Composable
private fun Separator() =
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(36.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )

@Composable
private fun SecondaryMetric(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        text = key,
        style =
            MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.14f.em,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        maxLines = 1,
    )
    Text(
        text = value,
        style =
            MaterialTheme.typography.titleSmall.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.01f).em,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

@Composable
private fun AddressRow(text: String) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Lucide.MapPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
            modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
        )
        Text(
            text = text,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            maxLines = 1,
        )
    }

@PreviewLightDark
@Preview(name = "Speed overlay", widthDp = 520, heightDp = 140)
@Composable
private fun SpeedOverlayPreview() {
    FemtoTheme {
        SpeedOverlay(
            location = null,
            address = ShortAddress(locality = "Minato-ku", region = "Tokyo"),
            tripState = TripState(distanceMeters = 24_400.0, avgSpeedMs = 11.7),
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
        )
    }
}
