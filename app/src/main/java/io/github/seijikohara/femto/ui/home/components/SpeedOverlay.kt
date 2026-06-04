package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.TripState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.distanceLabel
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.locale.tripDistanceFromMeters
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures
import io.github.seijikohara.femto.ui.theme.sectionLabel
import kotlin.math.roundToInt

/**
 * Glass overlay anchored to the map pane's bottom-centre, per
 * `docs/design/dashboard-v2-mockup.html` `.speed-overlay`:
 *
 *  - Metric row: hero current speed | separator | distance | separator |
 *    average speed | separator | reset-trip button (top-right).
 *  - Below: a 1 dp top border, then a MapPin · short-address row.
 *  - 20 dp corner radius and a 1 dp outline border. The Column wraps its
 *    content rather than claiming a fixed width, so the metric cells sit
 *    tight with a consistent 16 dp gap and the overlay never stretches to
 *    fill the map pane. The call site centres it via
 *    `Alignment.BottomCenter`, so a content-width Column stays compact and
 *    centred. Tabular figures plus reserved per-cell widths
 *    ([FemtoDimens.SpeedHeroValueMinWidth] / [FemtoDimens.SpeedMetricMinWidth])
 *    keep the overlay's width stable as the values tick, so it no longer grows
 *    and shrinks with the digit count.
 *
 * The 40 sp speed numeral is the only saturated value here; the
 * supporting metrics use `onSurface` / `onSurfaceVariant` so the hero
 * number reads as the "thing you glance at" on the move.
 *
 * Live speed honours the permissions contract: with no fix
 * (`location == null`) the hero cell shows an em-dash rather than "0",
 * so a missing/denied location reads as "unknown", not "standstill". A
 * 5-sample exponential moving average ([SPEED_OVERLAY_EMA_ALPHA])
 * smooths the raw 1 Hz speed so the numeral stops flickering between
 * adjacent integers on a steady cruise.
 */
@Composable
internal fun SpeedOverlay(
    location: Location?,
    address: ShortAddress?,
    tripState: TripState,
    speedUnit: SpeedUnit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Source the hero numeral from the trip's effective speed, not
    // location.speed: cheap GPS chips leave Location.speed at 0.0
    // (hasSpeed() == false) while moving, which would pin the numeral to
    // zero. currentSpeedMs falls back to the position-derived speed.
    //
    // The EMA accumulator is keyed off the latest fix so a new sample
    // advances the average and a null fix (no location) resets it; the
    // first non-null sample seeds the accumulator with itself.
    val smoothedSpeedMs = remember { mutableStateOf<Float?>(null) }
    smoothedSpeedMs.value =
        location?.let { emaStep(smoothedSpeedMs.value, tripState.currentSpeedMs.toFloat(), SPEED_OVERLAY_EMA_ALPHA) }
    val currentSpeedText by remember(speedUnit) {
        derivedStateOf {
            smoothedSpeedMs.value
                ?.let { "${speedUnit.fromMetersPerSecond(it).roundToInt()}" }
                ?: NO_SPEED_PLACEHOLDER
        }
    }
    val distance = speedUnit.tripDistanceFromMeters(tripState.distanceMeters)
    val avgSpeed = speedUnit.fromMetersPerSecond(tripState.avgSpeedMs.toFloat()).roundToInt()
    val shortAddress = address?.displayString().orEmpty()
    val glassAlpha = if (isSystemInDarkTheme()) FemtoDimens.GlassBgAlphaDark else FemtoDimens.GlassBgAlphaLight
    Column(
        modifier =
            modifier
                // Size to the widest row's content so the overlay hugs its
                // metrics; without this the inner HorizontalDivider (which
                // defaults to fillMaxWidth) would stretch the card to the full
                // map pane. The address-row divider then spans the same width.
                .width(IntrinsicSize.Max)
                .clip(RoundedCornerShape(FemtoDimens.SpeedOverlayCorner))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = glassAlpha))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.GlassBorderAlpha),
                    shape = RoundedCornerShape(FemtoDimens.SpeedOverlayCorner),
                ).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        MetricRow(
            currentSpeed = currentSpeedText,
            speedUnitLabel = speedUnit.label(),
            distance = distance,
            distanceUnitLabel = speedUnit.distanceLabel(),
            avgSpeed = avgSpeed,
            onReset = onReset,
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
    currentSpeed: String,
    speedUnitLabel: String,
    distance: Double,
    distanceUnitLabel: String,
    avgSpeed: Int,
    onReset: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    NowMetric(value = currentSpeed, unit = speedUnitLabel)
    Separator()
    SecondaryMetric(
        key = "DISTANCE",
        value = "%.1f %s".format(distance, distanceUnitLabel),
        modifier = Modifier.widthIn(min = FemtoDimens.SpeedMetricMinWidth),
    )
    Separator()
    SecondaryMetric(
        key = "AVG.",
        value = "$avgSpeed $speedUnitLabel",
        modifier = Modifier.widthIn(min = FemtoDimens.SpeedMetricMinWidth),
    )
    Separator()
    ResetButton(onReset = onReset)
}

@Composable
private fun NowMetric(
    value: String,
    unit: String,
) = Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(
        text = value,
        style =
            MaterialTheme.typography.displayMedium.copy(
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.03f).em,
                lineHeight = 40.sp,
                fontFeatureSettings = TabularFigures,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        // Reserve a stable width sized for the clamped 3-digit range and
        // right-align within it, so the hero numeral does not reflow the
        // overlay as the speed's digit count changes.
        textAlign = TextAlign.End,
        modifier = Modifier.widthIn(min = FemtoDimens.SpeedHeroValueMinWidth),
    )
    Text(
        text = unit,
        style = MaterialTheme.typography.sectionLabel(12, 0.12f),
        // Mockup .speed-overlay .now .u { opacity: 0.7 } — the unit sits a step
        // below the speed numeral it annotates.
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
        style = MaterialTheme.typography.sectionLabel(10, 0.14f),
        // Mockup .speed-overlay .k { opacity: 0.62 } — the metric key is the
        // dimmest tier so the value beside it reads first.
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        maxLines = 1,
    )
    Text(
        text = value,
        style =
            MaterialTheme.typography.titleSmall.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.01f).em,
                fontFeatureSettings = TabularFigures,
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
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
                ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            maxLines = 1,
        )
    }

// Trailing reset control for the trip metrics, anchored to the overlay's
// top-right (the end of the metric row). A 64 dp hit area
// (CLAUDE.md#automotive-overrides) wraps a small glyph; the box also sets the
// metric row height, so the tap target is met without a separate overlay.
@Composable
private fun ResetButton(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier =
        modifier
            .size(FemtoDimens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onReset),
    contentAlignment = Alignment.Center,
) {
    Icon(
        imageVector = Lucide.RotateCcw,
        contentDescription = stringResource(R.string.speed_reset_trip),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
}

/**
 * Advance an exponential moving average (EMA) by one sample.
 *
 * Return [sample] when [previous] is null so the first reading seeds the
 * accumulator with itself; otherwise return the standard EMA update
 * `previous + alpha * (sample - previous)`. A larger [alpha] weights the
 * newest sample more heavily (faster response, less smoothing). The
 * function is pure so the smoothing math is JVM-unit-testable in
 * isolation from Compose.
 */
internal fun emaStep(
    previous: Float?,
    sample: Float,
    alpha: Float,
): Float = previous?.let { it + alpha * (sample - it) } ?: sample

/**
 * Roughly a 5-sample window: each sample retains `(1 - alpha)` of the
 * prior estimate, so 0.33 settles a steady reading within ~5 ticks of
 * the 1 Hz speed stream.
 */
private const val SPEED_OVERLAY_EMA_ALPHA = 0.33f

// Em-dash stands in for the live speed when there is no fix, mirroring
// the WeatherCard convention and the permissions contract (location
// panels read empty until granted). It avoids the ambiguous "0".
private const val NO_SPEED_PLACEHOLDER = "—"

@PreviewLightDark
@Preview(name = "Speed overlay", widthDp = 560, heightDp = 160)
@Composable
private fun SpeedOverlayPreview() {
    FemtoTheme {
        // A non-null fix exercises the live-speed path so the hero numeral
        // renders the smoothed value rather than the no-fix em-dash.
        SpeedOverlay(
            location = Location("preview").apply { speed = 13.2f },
            address = ShortAddress(locality = "Minato-ku", region = "Tokyo"),
            tripState = TripState(distanceMeters = 24_400.0, avgSpeedMs = 11.7, currentSpeedMs = 13.2),
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            onReset = {},
        )
    }
}

@PreviewLightDark
@Preview(name = "Speed overlay (wide values)", widthDp = 560, heightDp = 160)
@Composable
private fun SpeedOverlayWideValuesPreview() {
    FemtoTheme {
        // Large 3-digit speed and a long distance verify the reserved metric
        // widths hold the overlay's width stable instead of reflowing.
        SpeedOverlay(
            location = Location("preview").apply { speed = 30.5f },
            address = ShortAddress(locality = "Minato-ku", region = "Tokyo"),
            tripState = TripState(distanceMeters = 188_400.0, avgSpeedMs = 30.5, currentSpeedMs = 30.5),
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            onReset = {},
        )
    }
}

@PreviewLightDark
@Preview(name = "Speed overlay (no fix)", widthDp = 560, heightDp = 160)
@Composable
private fun SpeedOverlayNoFixPreview() {
    FemtoTheme {
        // No fix: the hero cell shows the em-dash placeholder while the
        // distance stays "0.0" for a fresh trip.
        SpeedOverlay(
            location = null,
            address = ShortAddress(locality = "Minato-ku", region = "Tokyo"),
            tripState = TripState.Initial,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            onReset = {},
        )
    }
}
