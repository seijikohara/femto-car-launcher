package io.github.seijikohara.femto.ui.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.weather.HourlyForecast
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.WeatherDataColors
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.weatherDataColors
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import java.time.Duration
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Chart geometry: the reserved band heights inside the canvas, top to bottom —
// curve area (with glow headroom), precipitation bars, then the hour-label
// strip. The glyph row is a sibling composable sharing the same x mapping.
private val CurveGlowPad = 10.dp
private val AxisLabelBand = 18.dp
private val PrecipBarMaxWidth = 10.dp
private const val PRECIP_BAND_FRACTION = 0.24f

// Horizontal inset applied INSIDE the canvas x mapping (not as layout padding),
// so the endpoint markers — the "now" glow especially — draw complete instead
// of clipping at the canvas edge.
private val ChartInsetH = 12.dp

// Stroke pair: a wide soft pass under a thin core — the flyover's glow idiom
// at chart scale.
private val CurveGlowWidth = 9.dp
private val CurveCoreWidth = 2.5f.dp

// Reference precipitation for a full-height bar; heavier rain clamps. sqrt
// scaling keeps drizzle visible without letting a downpour flatten it.
private const val PRECIP_FULL_MM = 4f

private const val LABEL_EVERY_HOURS = 6
private const val GLYPH_EVERY_HOURS = 3
private const val REVEAL_MILLIS = 700

/**
 * The 24 h temperature curve — the weather panel's centerpiece. A smoothed
 * polyline through the hourly temperatures, stroked with a temperature-mapped
 * gradient (the [WeatherDataColors.tempStops] ramp) in the flyover's soft-glow
 * idiom, over a bottom band of precipitation bars (height = amount, opacity =
 * probability). The time axis carries hour labels, a glowing "now" dot on the
 * first point, and sunrise/sunset ticks; a sibling row places condition glyphs
 * on the same x mapping. One finite draw-on reveal runs per snapshot
 * ([MotionTier.OFF] snaps), so the chart costs zero frames once settled.
 */
@Composable
internal fun WeatherTempCurve(
    hourly: List<HourlyForecast>,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    is24Hour: Boolean,
    motionTier: MotionTier,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(hourly) { tempCurveGeometry(hourly) } ?: return
    val dataColors = weatherDataColors()
    val glyphs = weatherGlyphs()
    val labelStyle: TextStyle = MaterialTheme.typography.sectionLabel(12)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    val textMeasurer = rememberTextMeasurer()
    val nowLabel = stringResource(R.string.weather_curve_now)

    // One finite reveal per snapshot; OFF snaps so composition settles in one
    // frame (golden-safe, and no work at 30 Hz once drawn).
    val reveal = remember(hourly) { Animatable(if (motionTier == MotionTier.OFF) 1f else 0f) }
    LaunchedEffect(reveal) {
        if (reveal.value < 1f) {
            reveal.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    val pointColors = remember(geometry, dataColors) { geometry.tempsC.map { tempColorAt(dataColors.tempStops, it) } }
    val sunriseFraction = sunrise?.let { sunEventFraction(hourly.first().time, it, geometry.spanHours) }
    val sunsetFraction = sunset?.let { sunEventFraction(hourly.first().time, it, geometry.spanHours) }

    Column(modifier = modifier) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(FemtoDimens.WeatherCurveHeight),
        ) {
            val w = size.width
            val inset = ChartInsetH.toPx()
            val chartWidth = w - 2f * inset
            val axisBand = AxisLabelBand.toPx()
            val chartBottom = size.height - axisBand
            val barsBand = (chartBottom * PRECIP_BAND_FRACTION)
            val curveTop = CurveGlowPad.toPx()
            val curveBottom = chartBottom - barsBand
            val progress = reveal.value

            fun xOf(fraction: Float) = inset + fraction * chartWidth

            fun xAt(i: Int) = xOf(geometry.xs[i])

            fun yAt(i: Int) = curveTop + (1f - geometry.heat[i]) * (curveBottom - curveTop)

            // Temperature-mapped gradient across the chart span; each point
            // contributes its own colour stop.
            val brush =
                Brush.linearGradient(
                    colorStops = Array(geometry.xs.size) { i -> geometry.xs[i] to pointColors[i] },
                    start = Offset(inset, 0f),
                    end = Offset(inset + chartWidth, 0f),
                )

            // Smoothed path: quadratics through segment midpoints — C1-smooth
            // and never overshoots the data hull (a spline overshoot would
            // fabricate temperatures).
            val path = Path()
            path.moveTo(xAt(0), yAt(0))
            for (i in 0 until geometry.xs.size - 1) {
                val midX = (xAt(i) + xAt(i + 1)) / 2f
                val midY = (yAt(i) + yAt(i + 1)) / 2f
                path.quadraticTo(xAt(i), yAt(i), midX, midY)
            }
            path.lineTo(xAt(geometry.xs.size - 1), yAt(geometry.xs.size - 1))

            // Under-fill down to the bar band, faint, so the curve reads as a
            // filled "day shape" rather than a floating wire.
            val fill = Path().apply {
                addPath(path)
                lineTo(xAt(geometry.xs.size - 1), curveBottom)
                lineTo(xAt(0), curveBottom)
                close()
            }

            // Draw-on: stroke only the revealed arc length; clip the fill and
            // gate the bars to the same front.
            val revealed = Path()
            val measure = PathMeasure()
            measure.setPath(path, forceClosed = false)
            measure.getSegment(0f, measure.length * progress, revealed, startWithMoveTo = true)

            clipRect(right = xOf(progress)) {
                drawPath(fill, brush, alpha = 0.08f)
            }
            drawPath(
                revealed,
                brush,
                alpha = 0.30f,
                style = Stroke(CurveGlowWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                revealed,
                brush,
                style = Stroke(CurveCoreWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // Precipitation bars, bottom-anchored between curve area and axis.
            val slot = if (geometry.xs.size > 1) chartWidth / (geometry.xs.size - 1) else chartWidth
            val barWidth = minOf(slot * 0.5f, PrecipBarMaxWidth.toPx())
            hourly.forEachIndexed { i, hour ->
                val mm = hour.precipitationMm ?: return@forEachIndexed
                if (mm <= 0.0 || geometry.xs[i] > progress) return@forEachIndexed
                val heightFraction = sqrt((mm.toFloat() / PRECIP_FULL_MM).coerceIn(0f, 1f))
                val barHeight = maxOf(heightFraction * barsBand, 3.dp.toPx())
                val alpha =
                    hour.precipitationProbabilityPercent
                        ?.let { (it / 100f).coerceIn(0.35f, 0.9f) }
                        ?: 0.6f
                drawRoundRect(
                    color = dataColors.precipitation,
                    topLeft = Offset(xAt(i) - barWidth / 2f, chartBottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                    alpha = alpha,
                )
            }

            // Sunrise / sunset: a hairline through the chart plus a tinted dot
            // on the axis line — sun tint for rise, moon tint for set.
            listOfNotNull(
                sunriseFraction?.let { it to glyphs.sun },
                sunsetFraction?.let { it to glyphs.moon },
            ).forEach { (fraction, tint) ->
                val x = xOf(fraction)
                drawLine(tint, Offset(x, curveTop), Offset(x, chartBottom), strokeWidth = 1.dp.toPx(), alpha = 0.18f)
                drawCircle(tint, radius = 2.5f.dp.toPx(), center = Offset(x, chartBottom))
            }

            // Hour labels: "Now" on the first point, then every 6 h.
            hourly.forEachIndexed { i, hour ->
                if (i != 0 && i % LABEL_EVERY_HOURS != 0) return@forEachIndexed
                val text = if (i == 0) nowLabel else forecastHourLabel(hour.time, is24Hour)
                val measured = textMeasurer.measure(text, labelStyle)
                val x = (xAt(i) - measured.size.width / 2f).coerceIn(0f, w - measured.size.width)
                drawText(
                    measured,
                    color = labelColor,
                    topLeft = Offset(
                        x,
                        chartBottom + (axisBand - measured.size.height) / 2f,
                    ),
                )
            }

            // The glowing "now" marker rides the first point.
            val nowCenter = Offset(xAt(0), yAt(0))
            drawCircle(pointColors.first(), radius = 9.dp.toPx(), center = nowCenter, alpha = 0.25f)
            drawCircle(pointColors.first(), radius = 4.dp.toPx(), center = nowCenter)
        }
        GlyphAxisRow(
            hourly = hourly,
            sunrise = sunrise,
            sunset = sunset,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Condition glyphs every 3 h, centred on the same fractional x positions the
// canvas uses, via a one-off measurement policy (a Row cannot express "centre
// at fraction f of the width").
@Composable
private fun GlyphAxisRow(
    hourly: List<HourlyForecast>,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    modifier: Modifier = Modifier,
) {
    val glyphs = weatherGlyphs()
    val indices = hourly.indices.filter { it % GLYPH_EVERY_HOURS == 0 }
    val span = (hourly.size - 1).coerceAtLeast(1).toFloat()
    Layout(
        modifier = modifier.height(FemtoDimens.WeatherGlyphLarge),
        content = {
            indices.forEach { i ->
                val hour = hourly[i]
                val day = isDaylight(hour.time, sunrise, sunset)
                FemtoIcon(
                    imageVector = glyphIconFor(hour.code, day),
                    contentDescription = null,
                    tint = glyphTintFor(hour.code, day, glyphs),
                    modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        // Mirror the canvas's inset x mapping so the glyphs sit exactly under
        // their hours.
        val inset = ChartInsetH.roundToPx()
        val chartWidth = constraints.maxWidth - 2 * inset
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { slot, placeable ->
                val fraction = indices[slot] / span
                val x = (inset + fraction * chartWidth - placeable.width / 2f).roundToInt()
                placeable.placeRelative(
                    x.coerceIn(0, constraints.maxWidth - placeable.width),
                    (constraints.maxHeight - placeable.height) / 2,
                )
            }
        }
    }
}

/** Normalized curve geometry, precomputed once per snapshot. */
internal class TempCurveGeometry(
    /** Per-point x fraction in [0, 1]. */
    val xs: FloatArray,
    /** Per-point heat fraction in [0, 1] — 1 = the window's hottest hour. */
    val heat: FloatArray,
    val tempsC: FloatArray,
    /** Hours spanned by the window (points - 1). */
    val spanHours: Float,
)

// A near-flat day still deserves a visible curve: pad the normalization span
// to at least this many degrees, centred on the data.
private const val MIN_TEMP_SPAN_C = 4f

/** Build the normalized geometry, or null when there are too few points. */
internal fun tempCurveGeometry(hourly: List<HourlyForecast>): TempCurveGeometry? {
    if (hourly.size < 2) return null
    val temps = FloatArray(hourly.size) { hourly[it].tempC.toFloat() }
    val min = temps.min()
    val max = temps.max()
    val span = maxOf(max - min, MIN_TEMP_SPAN_C)
    val base = (min + max) / 2f - span / 2f
    val last = (hourly.size - 1).toFloat()
    return TempCurveGeometry(
        xs = FloatArray(hourly.size) { it / last },
        heat = FloatArray(hourly.size) { ((temps[it] - base) / span).coerceIn(0f, 1f) },
        tempsC = temps,
        spanHours = last,
    )
}

/** Lerp the ramp at [tempC]; clamps beyond the outer stops. */
internal fun tempColorAt(
    stops: List<Pair<Float, Color>>,
    tempC: Float,
): Color {
    if (tempC <= stops.first().first) return stops.first().second
    if (tempC >= stops.last().first) return stops.last().second
    val upper = stops.indexOfFirst { it.first >= tempC }
    val (loTemp, loColor) = stops[upper - 1]
    val (hiTemp, hiColor) = stops[upper]
    return lerp(loColor, hiColor, (tempC - loTemp) / (hiTemp - loTemp))
}

/**
 * Where [event] falls inside the window starting at [windowStart] spanning
 * [spanHours], as a fraction in [0, 1] — or null when outside. Wall-clock
 * arithmetic wraps midnight, so an overnight window still finds both events.
 */
internal fun sunEventFraction(
    windowStart: LocalTime,
    event: LocalTime,
    spanHours: Float,
): Float? {
    val minutes = Duration.between(windowStart, event).toMinutes().let { if (it < 0) it + 24 * 60 else it }
    val fraction = minutes / (spanHours * 60f)
    return fraction.takeIf { it <= 1f }
}
