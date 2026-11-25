package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import androidx.annotation.StringRes
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.X
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.MIN_MOVING_SPEED_MS
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.RouteProgress
import io.github.seijikohara.femto.data.routing.RouteRowTime
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.distanceLabel
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.locale.tripDistanceFromMeters
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.TabularFigures
import io.github.seijikohara.femto.ui.theme.glanceCaption
import io.github.seijikohara.femto.ui.theme.glanceMetric
import io.github.seijikohara.femto.ui.theme.heroNumeral
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.singleLineBox
import io.github.seijikohara.femto.ui.theme.strongWeight
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Glass overlay anchored to the map pane's bottom-centre (layout inherited
 * from the `.speed-overlay` of the retired dashboard-v2 design mockup):
 *
 *  - Metric row: hero current speed | separator | distance | separator |
 *    average speed | separator | reset-trip button (top-right).
 *  - Below: a 1 dp top border, then a MapPin · short-address row.
 *  - With an active route: another border, then a Flag · remaining distance ·
 *    arrival or remaining time (or a reroute's status) row with a clear button
 *    ([RouteRow]).
 *  - MaterialTheme.shapes.large corner and a 1 dp outline border. The Column wraps its
 *    content rather than claiming a fixed width, so the metric cells sit
 *    tight with a consistent 16 dp gap and the overlay never stretches to
 *    fill the map pane. The call site centres it via
 *    `Alignment.BottomCenter`, so a content-width Column stays compact and
 *    centred. Each numeric cell reserves its width from a widest-realistic
 *    sample rendered invisibly in the value's own style ([WidthReserve]) —
 *    a dp reserve cannot follow the user's font size / weight / spacing /
 *    family settings, which scale text but not dp — and the address row is
 *    kept out of the intrinsic width vote ([ZeroIntrinsicWidth]), so the
 *    overlay's width follows typography settings but never the ticking
 *    values or the passing geocodes.
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
    is24Hour: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    motionTier: MotionTier = MotionTier.STANDARD,
    // When set, a tap anywhere on the overlay (except the reset button, which
    // consumes its own taps) maximizes into the trip flyover — the same
    // whole-card idiom the calendar and weather cards use to expand.
    onExpand: (() -> Unit)? = null,
    // What is left of the active route (issue #389 phase 3); null hides the
    // route row. [reroute] is where an automatic reroute of it stands,
    // [onClearRoute] its clear button, and [clock] the "now" its arrival time
    // counts from (the dashboard's injected clock). [routeTime] picks the
    // arrival time or the time remaining (Settings → Navigation).
    route: RouteProgress? = null,
    reroute: RerouteStatus = RerouteStatus.NONE,
    onClearRoute: () -> Unit = {},
    clock: Clock = Clock.systemDefaultZone(),
    routeTime: RouteRowTime = RouteRowTime.ARRIVAL,
) {
    val expandLabel = stringResource(R.string.speed_expand_trip)
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
            if (location == null) {
                smoother.estimateMs = null
            } else {
                // dt is meaningful only once an estimate exists (the seed
                // path ignores it); gating on the estimate rather than a
                // zero-timestamp sentinel keeps a legitimate
                // elapsedRealtimeNanos == 0 fix from stalling a tick.
                val dtMillis =
                    if (smoother.estimateMs == null) {
                        0L
                    } else {
                        (location.elapsedRealtimeNanos - smoother.basisElapsedNanos) / 1_000_000L
                    }
                // A fix whose monotonic timestamp sits behind the basis is a
                // REPLAYED older fix (the location flow re-seeds
                // getLastKnownLocation on every re-subscribe, and this holder
                // outlives that teardown because the Activity is stopped, not
                // destroyed). Skip it entirely rather than clamping dt to 0:
                // clamping while still re-anchoring the basis would move the
                // basis backwards and inflate the next legitimate fix's dt.
                // TripRepository re-anchors without accruing for the same
                // reason (issue #351).
                if (dtMillis >= 0L) {
                    smoother.basisElapsedNanos = location.elapsedRealtimeNanos
                    smoother.estimateMs =
                        speedSmoothingStep(smoother.estimateMs, tripState.currentSpeedMs.toFloat(), dtMillis)
                }
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
    // altitude readout rather than showing a misleading 0. hasAltitude() only
    // says the HAL filled the field, not that it filled it with a number, and
    // Double.roundToInt() throws on NaN rather than saturating.
    val altitudeM = location?.takeIf { it.hasAltitude() && it.altitude.isFinite() }?.altitude?.roundToInt()
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
                // maximum. The address row never joins the intrinsic vote (see
                // [ZeroIntrinsicWidth]) and ellipsizes within the result.
                .widthIn(max = FemtoDimens.SpeedOverlayMaxWidth)
                // Whole-overlay tap to maximize (the reset button consumes its own
                // taps). Applied before the glass chrome so the ripple stays within
                // the card shape; the explicit contentDescription is hoisted because
                // the semantics lambda is not @Composable.
                .then(
                    if (onExpand != null) {
                        Modifier
                            .clickable(onClick = onExpand)
                            .semantics { contentDescription = expandLabel }
                    } else {
                        Modifier
                    },
                ).glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
                .padding(
                    start = FemtoDimens.OverlayPaddingHorizontal,
                    end = FemtoDimens.OverlayPaddingHorizontal,
                    top = RowInkGap - MetricRowTopAir,
                    bottom = RowInkGap - AddressRowAir,
                ),
    ) {
        MetricRow(
            currentSpeed = currentSpeedText,
            speedUnitLabel = speedUnit.label(),
            distance = distance,
            distanceUnitLabel = speedUnit.distanceLabel(),
            avgSpeed = avgSpeed,
            // The trip-start time rides in the metric row (a compact cell before
            // the reset button) so it sits at the speed/distance height instead of
            // adding a row below. Null (no trip yet) hides the cell.
            sinceTimestamp = tripState.startedAtEpochMs?.let { tripStartTimestamp(it, is24Hour) },
            tier = motionTier,
            onReset = onReset,
        )
        // Always render the address row (even with no fix / unresolved address) so
        // the overlay keeps a stable height instead of collapsing then growing when
        // the address arrives.
        Box(modifier = Modifier.height(RowInkGap - MetricRowBottomAir))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha))
        Box(modifier = Modifier.height(RowInkGap - AddressRowAir))
        ZeroIntrinsicWidth {
            AddressRow(text = shortAddress.ifBlank { NO_ADDRESS_PLACEHOLDER }, altitudeM = altitudeM)
        }
        // The route row (issue #389 phase 3) follows the address row's divider
        // and spacing; like the address it stays out of the width vote, so a
        // route never widens the card.
        if (route != null) {
            Box(modifier = Modifier.height(RowInkGap - AddressRowAir))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha))
            Box(modifier = Modifier.height(RowInkGap - AddressRowAir))
            ZeroIntrinsicWidth {
                RouteRow(
                    progress = route,
                    reroute = reroute,
                    speedUnit = speedUnit,
                    is24Hour = is24Hour,
                    clock = clock,
                    time = routeTime,
                    onClear = onClearRoute,
                )
            }
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
    sinceTimestamp: String?,
    tier: MotionTier,
    onReset: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    NowMetric(value = currentSpeed, unit = speedUnitLabel, tier = tier)
    Separator()
    SecondaryMetric(
        key = stringResource(R.string.speed_metric_distance),
        value = "%.1f".format(distance),
        unit = distanceUnitLabel,
        // Formatted through the same "%.1f" as the live value so the locale's
        // decimal separator is measured, not assumed.
        widthSample = "%.1f".format(DISTANCE_WIDTH_SAMPLE),
        tier = tier,
    )
    Separator()
    SecondaryMetric(
        key = stringResource(R.string.speed_metric_avg),
        value = "$avgSpeed",
        unit = speedUnitLabel,
        widthSample = THREE_DIGIT_WIDTH_SAMPLE,
        tier = tier,
    )
    Separator()
    if (sinceTimestamp != null) {
        SinceCell(timestamp = sinceTimestamp)
    }
    RowEndButton(
        icon = Lucide.RotateCcw,
        description = stringResource(R.string.speed_reset_trip),
        reportedHeight = SeparatorHeight,
        onClick = onReset,
    )
}

@Composable
private fun NowMetric(
    value: String,
    unit: String,
    tier: MotionTier,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    // Strong-tier hero numeral: heavier than the ambient clock's normal tier
    // since the speed is the safety-critical glance that must stay legible on a
    // dim head unit. Tracks the user's weight setting while keeping that
    // relative emphasis across the range.
    val heroStyle = MaterialTheme.typography.heroNumeral(weight = MaterialTheme.typography.strongWeight)
    // The numeral dissolves on a change of the DISPLAYED, EMA-rounded string (not
    // the raw smoothed float), so it fades once per real digit change. The width
    // reserve travels INSIDE each dissolve layer, so the outgoing and incoming
    // numerals right-align within the same reserve and the cell cannot reflow the
    // overlay even mid-swap; the boxes keep propagating the numeral's baseline.
    Motion.ContentCrossfade(
        targetState = value,
        tier = tier,
        label = "speedHero",
        modifier = Modifier.alignByBaseline(),
    ) { numeral ->
        WidthReserve(
            sample = THREE_DIGIT_WIDTH_SAMPLE,
            style = heroStyle,
            // Pinned to the 40sp line: fallback line spacing otherwise grows the
            // line to the fallback faces' metrics (48dp), and the extra air above
            // and below the digits made this row look padded twice as deep as
            // the address row.
            modifier = Modifier.singleLineBox(heroStyle),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = numeral,
                style = heroStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
    // Dimmed unit trailing the numeral on its baseline (the shared dashboard unit
    // treatment) — a step below the value it annotates.
    UnitSuffix(unit, modifier = Modifier.alignByBaseline())
}

// The metric row's vertical rules; also the height the reset control reports
// (see [RowEndButton]).
private val SeparatorHeight = 36.dp

// The gap from each row's ink to the nearest card edge or to the divider: the
// same above and below both rows, so the two rows read as one balanced card.
private val RowInkGap = 11.dp

// The air each row's line boxes carry beyond their ink at the default font
// settings, measured on the head-unit geometry. The paddings and the divider
// gaps subtract it, so [RowInkGap] lands on the visible text rather than on
// the line boxes.
private val MetricRowTopAir = 4.dp
private val MetricRowBottomAir = 3.dp
private val AddressRowAir = 1.dp

@Composable
private fun Separator() =
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(SeparatorHeight)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha)),
    )

@Composable
private fun SecondaryMetric(
    key: String,
    value: String,
    unit: String,
    widthSample: String,
    tier: MotionTier,
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
    // hero speed's value/unit treatment; the [widthSample] reserve inside each
    // dissolve layer keeps a digit change from reflowing the row (the key and
    // unit are fixed strings). The value dissolves on a change of its DISPLAYED
    // string (keyed on the formatted value, not the raw trip total). CenterEnd
    // mirrors NowMetric: the value right-aligns against the trailing unit, so
    // the reserve's slack falls invisibly at the cell start instead of opening
    // a gap between the value and its unit.
    val metricStyle = MaterialTheme.typography.glanceMetric()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Motion.ContentCrossfade(
            targetState = value,
            tier = tier,
            label = "speedMetric",
            modifier = Modifier.alignByBaseline(),
        ) { metric ->
            WidthReserve(
                sample = widthSample,
                style = metricStyle,
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = metric,
                    style = metricStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        UnitSuffix(unit, modifier = Modifier.alignByBaseline())
    }
}

// The trip-start clock time (time only — a bare "Since 8:04", no date). Uses the
// shared [clockTimeFormatter], so the 12/24-hour choice follows the app's clock
// setting ([is24Hour]) — the same formatter the calendar and weather panels use —
// and the result never carries a date (DateUtils.FORMAT_SHOW_TIME appends one for
// a non-today instant, which is what we are avoiding here).
@Composable
private fun tripStartTimestamp(
    startedAtEpochMs: Long,
    is24Hour: Boolean,
): String =
    remember(startedAtEpochMs, is24Hour) {
        Instant
            .ofEpochMilli(startedAtEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(clockTimeFormatter(is24Hour))
    }

// "Since / 8:04" — when the current reset-to-reset trip began (the wall-clock
// time of its first GPS fix, also the track log's first point for the trip). A
// compact two-line cell (the "Since" key over the timestamp, the same key-over-
// value shape as the distance / average cells) placed just before the reset
// button, so the trip-start time sits at the speed/distance height rather than
// spreading across a row below. Only shown once the trip has a start time; it is
// static per trip (no ticking), so it needs no width reserve.
@Composable
private fun SinceCell(timestamp: String) =
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
        Text(
            text = stringResource(R.string.speed_trip_since_label),
            style = MaterialTheme.typography.sectionLabel(12),
            color = dim,
            maxLines = 1,
        )
        Text(
            text = timestamp,
            style = MaterialTheme.typography.sectionLabel(12),
            color = dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }

@Composable
private fun AddressRow(
    text: String,
    altitudeM: Int?,
) = Row(
    // Fill the width the metric row won (the [ZeroIntrinsicWidth] wrapper keeps
    // this row's long strings out of the overlay's intrinsic vote), so a long
    // address ellipsizes within it instead of stretching the card wider.
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
        style = rowCaptionStyle(tabularFigures = false),
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

/**
 * Reserve a value cell's width from [sample], rendered invisibly in [style] —
 * the exact style of the value drawn on top. A dp reserve cannot follow the
 * user's font size / weight / letter-spacing / family settings (they scale
 * text, not dp), which is how the overlay's width came to breathe with the
 * digit count; a same-style sample re-measures with every one of them, and
 * '8' stays the safest widest digit on proportional faces (tabular faces
 * render all digits equal). The sample is cleared from semantics so screen
 * readers and tests see only the real value.
 */
@Composable
private fun WidthReserve(
    sample: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable () -> Unit,
) = Box(modifier = modifier, contentAlignment = contentAlignment) {
    Text(
        text = sample,
        style = style,
        maxLines = 1,
        modifier =
            Modifier
                .alpha(0f)
                .clearAndSetSemantics {},
    )
    content()
}

// The tap target of a row's trailing control (the trip reset, the route
// clear): 48 dp, a deliberate exception to the 64 dp automotive floor
// (AGENTS.md#automotive-overrides), decided 2026-11-22. A 64 dp target set the
// metric row's height and left a band of about 22 dp above and below the
// speed, three times the address row's; at 48 dp the metric row hugs its
// numerals like the address row does.
private val RowButtonTapTarget = 48.dp

// A trailing control's reported layout width, smaller than its tap target so
// the control does not claim a metric cell's worth of width at the row's end.
// Its reported height is its row's own (see [RowEndButton]), so it does not
// set the row's height either. The overhang lands in the row gap, the card's
// end padding and the gaps around the row, dead space inside the card.
private val RowButtonReportedWidth = 40.dp

// A row's trailing control: the trip reset at the end of the metric row, the
// route clear at the end of the route row. See [RowButtonTapTarget] for its
// size and [RowButtonReportedWidth] for why its layout size is smaller;
// [reportedHeight] is the height of the row it ends.
@Composable
private fun RowEndButton(
    icon: ImageVector,
    description: String,
    reportedHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier =
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val reportedWidth = RowButtonReportedWidth.roundToPx().coerceAtMost(placeable.width)
                val reportedHeightPx = reportedHeight.roundToPx().coerceAtMost(placeable.height)
                layout(reportedWidth, reportedHeightPx) {
                    placeable.placeRelative(
                        (reportedWidth - placeable.width) / 2,
                        (reportedHeightPx - placeable.height) / 2,
                    )
                }
            }.size(RowButtonTapTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = description,
        // onSurface (not onSurfaceVariant): these are the overlay's actionable
        // controls, and the variant tone read as disabled/decorative next to the
        // hairline-thin Lucide stroke. Full onSurface still stays secondary to
        // the saturated 40 sp speed numeral by virtue of its much smaller size.
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
}

/**
 * The route row under the address: the remaining distance in the speed
 * unit's family, the arrival time on the user's clock format or the travel
 * time left, as [time] picks, and the clear button. It shares the address
 * row's caption style and glyph size, so it carries the same air
 * ([AddressRowAir]) and the card's row spacing holds.
 * Its clear button reports the glyph's height, so the 48 dp target overhangs
 * into the gaps around the row rather than growing it.
 *
 * The arrival time is read from [clock] whenever the progress changes (each
 * fix), and once a minute besides, so it keeps moving while no fix arrives.
 * The time left is in the locale's short units ("1 hr, 5 min left"), which
 * fit the row whole up to a day's drive on the head unit (RouteRowTimeTest),
 * so the row never needs the narrow ones the route choice card falls back to.
 * While a reroute runs, or after one that found nothing, its status takes the
 * time's place: the old line's time no longer holds.
 */
@Composable
private fun RouteRow(
    progress: RouteProgress,
    reroute: RerouteStatus,
    speedUnit: SpeedUnit,
    is24Hour: Boolean,
    clock: Clock,
    time: RouteRowTime,
    onClear: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    val minute by produceState(initialValue = 0L, clock) {
        while (true) {
            delay(MINUTE_MS - clock.millis() % MINUTE_MS)
            value++
        }
    }
    val timeText =
        remember(minute, progress.remainingSeconds, is24Hour, clock, time) {
            val seconds = progress.remainingSeconds
            when (time) {
                RouteRowTime.ARRIVAL -> routeArrivalText(clock.instant(), clock.zone, seconds, is24Hour)
                RouteRowTime.REMAINING -> travelTimeText(seconds.roundToInt())
            }
        }
    val style = rowCaptionStyle(tabularFigures = true)
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
    FemtoIcon(
        imageVector = Lucide.Flag,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
        modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = remainingDistanceText(progress.remainingMeters, speedUnit),
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        UnitSuffix(speedUnit.distanceLabel(), modifier = Modifier.alignByBaseline())
    }
    Text(
        text =
            when (reroute) {
                RerouteStatus.NONE -> stringResource(routeRowFormat(time), timeText)
                RerouteStatus.REROUTING -> stringResource(R.string.speed_route_rerouting)
                RerouteStatus.NO_DATA -> stringResource(R.string.speed_route_reroute_no_data)
                RerouteStatus.FAILED -> stringResource(R.string.speed_route_reroute_failed)
            },
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    RowEndButton(
        icon = Lucide.X,
        description = stringResource(R.string.speed_route_clear),
        reportedHeight = FemtoDimens.WeatherGlyphSmall,
        onClick = onClear,
    )
}

// The route row's text around its time: "ETA 10:26" or "18 min left".
@StringRes
private fun routeRowFormat(time: RouteRowTime): Int =
    when (time) {
        RouteRowTime.ARRIVAL -> R.string.speed_route_arrival
        RouteRowTime.REMAINING -> R.string.speed_route_remaining
    }

// The caption style the address and route rows share: the glance caption on
// the body lineage, on a 16 sp line. Tabular figures keep a ticking value
// (the route's remaining distance) from reflowing; the address has no digits
// to keep steady.
@Composable
private fun rowCaptionStyle(tabularFigures: Boolean): TextStyle =
    MaterialTheme.typography.glanceCaption(
        base = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
        fontFeatureSettings = if (tabularFigures) TabularFigures else null,
    )

private const val MINUTE_MS = 60_000L

// A route's remaining distance below this many units shows a decimal; from
// here on whole units read faster and the tenths change too often to matter.
private const val WHOLE_DISTANCE_FROM = 10.0

/**
 * Return the remaining distance [meters] in [speedUnit]'s distance unit (km
 * or mi): one decimal below [WHOLE_DISTANCE_FROM] units, whole units from
 * there. [locale] picks the decimal separator.
 */
internal fun remainingDistanceText(
    meters: Double,
    speedUnit: SpeedUnit,
    locale: Locale = Locale.getDefault(),
): String =
    speedUnit.tripDistanceFromMeters(meters).let { distance ->
        // The threshold judges the value as shown, rounded to tenths: 9.96
        // shows as 10.0, which belongs with the whole units.
        val tenths = (distance * 10.0).roundToLong() / 10.0
        if (tenths < WHOLE_DISTANCE_FROM) String.format(locale, "%.1f", tenths) else "${distance.roundToInt()}"
    }

/**
 * Return the arrival clock time [remainingSeconds] after [now] in [zone], on
 * the user's 12/24-hour format, rounded to the nearest minute as the clock
 * shows minutes.
 */
internal fun routeArrivalText(
    now: Instant,
    zone: ZoneId,
    remainingSeconds: Double,
    is24Hour: Boolean,
): String =
    now
        .plusSeconds(remainingSeconds.roundToLong() + HALF_MINUTE_SECONDS)
        .truncatedTo(ChronoUnit.MINUTES)
        .atZone(zone)
        .format(clockTimeFormatter(is24Hour))

private const val HALF_MINUTE_SECONDS = 30L

/**
 * Advance the displayed speed by one fix using a TIME-based exponential
 * step: `alpha = 1 - exp(-dt/τ)`, so the response settles in ~3·τ of real
 * time at any fix cadence (a fixed-alpha tick EMA made the settle time
 * scale with the location-interval setting). Three deliberate edges,
 * in the order the `when` tests them:
 *
 *  - A [sampleMs] below [MIN_MOVING_SPEED_MS] returns 0 immediately — a
 *    real speedometer reads a crisp 0 at a standstill, and smoothing the
 *    decay below the stationary floor only manufactures a phantom crawl
 *    (the on-device "takes seconds to reach 0" report).
 *  - A null [previous] seeds the estimate with the sample itself.
 *  - A non-positive [dtMillis] holds the estimate. The gain is a
 *    fraction of the remaining error only while the clock moves
 *    forward; a backwards one turns it into a large negative
 *    multiplier that diverges the estimate by orders of magnitude in
 *    either direction, and far enough back it overflows `exp` to
 *    Infinity and yields NaN, which `Float.roundToInt()` throws on
 *    rather than saturating (issue #351). The caller rejects such a
 *    fix outright; this keeps the step total.
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
        dtMillis <= 0L -> previous
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

// Width samples for the value cells (see [WidthReserve]). Three integer
// digits cover the hero and average speeds in either unit after
// TripRepository's plausibility clamp; the distance sample covers the
// common sub-1000 trip range (a longer trip still widens the cell — the
// reserve is a floor, not a cap).
private const val THREE_DIGIT_WIDTH_SAMPLE = "888"
private const val DISTANCE_WIDTH_SAMPLE = 888.8

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
            tripState =
                TripState(
                    distanceMeters = 24_400.0,
                    avgSpeedMs = 11.7,
                    currentSpeedMs = 13.2,
                    // A same-day start renders the "Since <time>" row's live shape.
                    startedAtEpochMs = System.currentTimeMillis(),
                ),
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            is24Hour = true,
            onReset = {},
        )
    }
}

@PreviewLightDark
@PreviewTextStress
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
            is24Hour = true,
            onReset = {},
        )
    }
}

@PreviewLightDark
@PreviewTextStress
@Preview(name = "Speed overlay (route)", widthDp = 560, heightDp = 200)
@Composable
private fun SpeedOverlayRoutePreview() {
    FemtoTheme {
        // An active route adds its row under the address.
        SpeedOverlay(
            location = Location("preview").apply { speed = 13.2f },
            address = ShortAddress(locality = "Minato-ku", region = "Tokyo"),
            tripState = TripState(distanceMeters = 24_400.0, avgSpeedMs = 11.7, currentSpeedMs = 13.2),
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            is24Hour = true,
            onReset = {},
            route = RouteProgress(remainingMeters = 12_400.0, remainingSeconds = 1_080.0),
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
            is24Hour = true,
            onReset = {},
        )
    }
}
