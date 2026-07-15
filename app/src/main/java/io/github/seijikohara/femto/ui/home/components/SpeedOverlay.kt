package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.RotateCcw
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.MIN_MOVING_SPEED_MS
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.distanceLabel
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.locale.tripDistanceFromMeters
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.glanceCaption
import io.github.seijikohara.femto.ui.theme.glanceMetric
import io.github.seijikohara.femto.ui.theme.heroNumeral
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.strongWeight
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Glass overlay anchored to the map pane's bottom-centre (layout inherited
 * from the `.speed-overlay` of the retired dashboard-v2 design mockup):
 *
 *  - Metric row: hero current speed | separator | distance | separator |
 *    average speed | separator | reset-trip button (top-right).
 *  - Below: a 1 dp top border, then a MapPin · short-address row.
 *  - MaterialTheme.shapes.large corner and a 1 dp outline border. The Column wraps its
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
 * so a missing/denied location reads as "unknown", not "standstill".
 * The numeral is smoothed with a TIME-based exponential step
 * ([speedSmoothingStep], τ = [SPEED_SMOOTHING_TAU_MS]) so its response
 * is the same at any fix cadence, and it snaps straight to 0 the moment
 * the raw reading drops below the stationary floor — a tick-based EMA
 * here used to drag a multi-second phantom-crawl tail after every stop.
 */
@Composable
internal fun SpeedOverlay(
    location: Location?,
    address: ShortAddress?,
    tripState: TripState,
    speedUnit: SpeedUnit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    // Source the hero numeral from the trip's effective speed, not
    // location.speed: cheap GPS chips leave Location.speed at 0.0
    // (hasSpeed() == false) while moving, which would pin the numeral to
    // zero. currentSpeedMs falls back to the position-derived speed.
    //
    // The smoothing state is keyed off the latest fix so a new sample
    // advances the estimate and a null fix (no location) resets it; the
    // first non-null sample seeds the estimate with itself. It lives in a
    // remembered plain holder (cf. MapPanel's bearingHolder) rather than
    // MutableState because writing state during composition would
    // invalidate the composition computing it; remember(location) advances
    // the estimate exactly once per fix. dt comes from the fixes' own
    // monotonic timestamps, so the time constant holds at any cadence.
    val smoother = remember { SpeedSmoothState() }
    val smoothedSpeedMs =
        remember(location) {
            smoother.estimateMs =
                location?.let { fix ->
                    // dt is meaningful only once an estimate exists (the seed
                    // path ignores it); gating on the estimate rather than a
                    // zero-timestamp sentinel keeps a legitimate
                    // elapsedRealtimeNanos == 0 fix from stalling a tick.
                    val dtMillis =
                        if (smoother.estimateMs == null) {
                            0L
                        } else {
                            (fix.elapsedRealtimeNanos - smoother.basisElapsedNanos) / 1_000_000L
                        }
                    smoother.basisElapsedNanos = fix.elapsedRealtimeNanos
                    speedSmoothingStep(smoother.estimateMs, tripState.currentSpeedMs.toFloat(), dtMillis)
                }
            smoother.estimateMs
        }
    val currentSpeedText =
        smoothedSpeedMs
            ?.let { "${speedUnit.fromMetersPerSecond(it).roundToInt()}" }
            ?: NO_SPEED_PLACEHOLDER
    val distance = speedUnit.tripDistanceFromMeters(tripState.distanceMeters)
    val avgSpeed = speedUnit.fromMetersPerSecond(tripState.avgSpeedMs.toFloat()).roundToInt()
    val shortAddress = address?.displayString().orEmpty()
    // Altitude (metres) from the fix when the chip reports it; null hides the
    // altitude readout rather than showing a misleading 0.
    val altitudeM = location?.takeIf { it.hasAltitude() }?.altitude?.roundToInt()
    Column(
        modifier =
            modifier
                // Size to the widest row's content so the overlay hugs its
                // metrics; without this the inner HorizontalDivider (which
                // defaults to fillMaxWidth) would stretch the card to the full
                // map pane. The address-row divider then spans the same width.
                .width(IntrinsicSize.Max)
                // Cap the width so a wide map pane (e.g. an 853 dp 5:3 head unit)
                // keeps the overlay a centred glass card, not a full-width bar.
                // IntrinsicSize.Max still hugs short content; this only bounds the
                // maximum, and the address row ellipsizes within it.
                .widthIn(max = FemtoDimens.SpeedOverlayMaxWidth)
                .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
                .padding(
                    horizontal = FemtoDimens.OverlayPaddingHorizontal,
                    vertical = FemtoDimens.OverlayPaddingVertical,
                ),
    ) {
        MetricRow(
            currentSpeed = currentSpeedText,
            speedUnitLabel = speedUnit.label(),
            distance = distance,
            distanceUnitLabel = speedUnit.distanceLabel(),
            avgSpeed = avgSpeed,
            onReset = onReset,
        )
        // Always render the address row (even with no fix / unresolved address) so
        // the overlay keeps a stable height instead of collapsing then growing when
        // the address arrives. The 5 dp gaps keep the metric row's breathing room in
        // step with the address row's.
        Box(modifier = Modifier.height(5.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha))
        Box(modifier = Modifier.height(5.dp))
        AddressRow(text = shortAddress.ifBlank { NO_ADDRESS_PLACEHOLDER }, altitudeM = altitudeM)
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
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    NowMetric(value = currentSpeed, unit = speedUnitLabel)
    Separator()
    SecondaryMetric(
        key = stringResource(R.string.speed_metric_distance),
        value = "%.1f".format(distance),
        unit = distanceUnitLabel,
        modifier = Modifier.widthIn(min = FemtoDimens.SpeedMetricMinWidth),
    )
    Separator()
    SecondaryMetric(
        key = stringResource(R.string.speed_metric_avg),
        value = "$avgSpeed",
        unit = speedUnitLabel,
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
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(
        text = value,
        // Strong-tier hero numeral: heavier than the ambient clock's normal tier
        // since the speed is the safety-critical glance that must stay legible on a
        // dim head unit. Tracks the user's weight setting while keeping that
        // relative emphasis across the range.
        style = MaterialTheme.typography.heroNumeral(weight = MaterialTheme.typography.strongWeight),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        // Reserve a stable width sized for the clamped 3-digit range and
        // right-align within it, so the hero numeral does not reflow the
        // overlay as the speed's digit count changes.
        textAlign = TextAlign.End,
        modifier = Modifier.widthIn(min = FemtoDimens.SpeedHeroValueMinWidth).alignByBaseline(),
    )
    // Dimmed unit trailing the numeral on its baseline (the shared dashboard unit
    // treatment) — a step below the value it annotates.
    UnitSuffix(unit, modifier = Modifier.alignByBaseline())
}

@Composable
private fun Separator() =
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(36.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha)),
    )

@Composable
private fun SecondaryMetric(
    key: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        text = key,
        style = MaterialTheme.typography.sectionLabel(12),
        // Mockup .speed-overlay .k { opacity: 0.62 } — the metric key is the
        // dimmest tier so the value beside it reads first.
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    // Numeric value + dimmed trailing unit on the value's baseline, matching the
    // hero speed's value/unit treatment; the caller's min-width cell keeps a
    // digit change from reflowing the row.
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.glanceMetric(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        UnitSuffix(unit, modifier = Modifier.alignByBaseline())
    }
}

@Composable
private fun AddressRow(
    text: String,
    altitudeM: Int?,
) = Row(
    // Fill the overlay's (metric-row-defined) width so a long address
    // ellipsizes within it instead of stretching the card wider.
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    FemtoIcon(
        imageVector = Lucide.MapPin,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
        modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
    )
    Text(
        text = text,
        style =
            MaterialTheme.typography.glanceCaption(
                base = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.sp,
                fontFeatureSettings = null,
            ),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    // Altitude readout, trailing the address (the address ellipsizes to make
    // room). Hidden when the fix carries no altitude.
    if (altitudeM != null) {
        // "ALT 42" + a dimmed trailing "m" on the label's baseline (altitude is
        // always metres), matching the shared dashboard value/unit treatment.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.speed_altitude, altitudeM),
                style = MaterialTheme.typography.sectionLabel(12),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            UnitSuffix("m", modifier = Modifier.alignByBaseline())
        }
    }
}

// Trailing reset control for the trip metrics, anchored to the overlay's
// top-right (the end of the metric row). Full MinTouchTarget size: the
// tap-target floor (CLAUDE.md#automotive-overrides) has no persisted exception
// for this control, and a mis-tap while driving resets the trip.
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
    FemtoIcon(
        imageVector = Lucide.RotateCcw,
        contentDescription = stringResource(R.string.speed_reset_trip),
        // onSurface (not onSurfaceVariant): this is the overlay's one actionable
        // control, and the variant tone read as disabled/decorative next to the
        // hairline-thin Lucide stroke. Full onSurface still stays secondary to
        // the saturated 40 sp speed numeral by virtue of its much smaller size.
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
}

/**
 * Advance the displayed speed by one fix using a TIME-based exponential
 * step: `alpha = 1 - exp(-dt/τ)`, so the response settles in ~3·τ of real
 * time at any fix cadence (a fixed-alpha tick EMA made the settle time
 * scale with the location-interval setting). Two deliberate edges:
 *
 *  - A [sampleMs] below [MIN_MOVING_SPEED_MS] returns 0 immediately — a
 *    real speedometer reads a crisp 0 at a standstill, and smoothing the
 *    decay below the stationary floor only manufactures a phantom crawl
 *    (the on-device "takes seconds to reach 0" report).
 *  - A null [previous] seeds the estimate with the sample itself.
 *
 * Pure so the smoothing math is JVM-unit-testable in isolation from
 * Compose.
 */
internal fun speedSmoothingStep(
    previous: Float?,
    sampleMs: Float,
    dtMillis: Long,
): Float =
    when {
        sampleMs < MIN_MOVING_SPEED_MS -> 0f
        previous == null -> sampleMs
        else -> previous + (1f - exp(-dtMillis / SPEED_SMOOTHING_TAU_MS)) * (sampleMs - previous)
    }

// Time constant of the speed numeral's smoothing: ~95% settled within ~1 s
// (3·τ) of a step change, fast enough to track braking while still ironing
// out per-fix jitter on a steady cruise.
private const val SPEED_SMOOTHING_TAU_MS = 350f

// Smoothing state for the hero numeral: the displayed estimate and the
// monotonic timestamp of the fix it was last advanced by.
private class SpeedSmoothState {
    var estimateMs: Float? = null
    var basisElapsedNanos: Long = 0L
}

// Em-dash stands in for the live speed when there is no fix, mirroring
// the WeatherCard convention and the permissions contract (location
// panels read empty until granted). It avoids the ambiguous "0".
private const val NO_SPEED_PLACEHOLDER = "—"

// Shown in the address row until a fix / reverse-geocode resolves, so the overlay
// reserves the row instead of collapsing (and the MapPin still reads as "location").
private const val NO_ADDRESS_PLACEHOLDER = "—"

@PreviewLightDark
@PreviewTextStress
@Preview(name = "Speed overlay", widthDp = 560, heightDp = 160)
@Composable
private fun SpeedOverlayPreview() {
    FemtoTheme {
        // A non-null fix exercises the live-speed path so the hero numeral
        // renders the smoothed value rather than the no-fix em-dash.
        SpeedOverlay(
            location =
                Location("preview").apply {
                    speed = 13.2f
                    altitude = 42.0
                },
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
