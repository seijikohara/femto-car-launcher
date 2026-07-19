package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.windLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.panelMetric
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.weatherDataColors
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import java.time.Duration
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Tile chrome: the quiet fill the trip chips use, so the grid reads as cells
// on the glass rather than nested cards.
private const val TILE_FILL_ALPHA = 0.06f
private val TileCorner = 12.dp
private val TileMinHeight = 92.dp
private val TileCanvasHeight = 40.dp

/**
 * The weather panel's visual metric tiles — sun arc, wind compass, UV, and
 * humidity — as a fixed 2x2 grid. Tiles with no data (no sun times, no UV)
 * render an em-dash body rather than vanishing, so the grid never reflows
 * between refreshes.
 */
@Composable
internal fun WeatherTileGrid(
    snapshot: WeatherSnapshot,
    speedUnit: SpeedUnit,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
        ) {
            SunTile(snapshot, is24Hour, Modifier.weight(1f))
            WindTile(snapshot, speedUnit, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
        ) {
            UvTile(snapshot, Modifier.weight(1f))
            HumidityTile(snapshot, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Column(
    modifier =
        modifier
            .heightIn(min = TileMinHeight)
            .clip(RoundedCornerShape(TileCorner))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TILE_FILL_ALPHA))
            .padding(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    Text(
        text = label,
        style = MaterialTheme.typography.sectionLabel(12),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        maxLines = 1,
    )
    content()
}

// Daylight arc: sunrise to sunset as a semicircle, the sun's current position
// riding it while daylight lasts, times at the feet.
@Composable
private fun SunTile(
    snapshot: WeatherSnapshot,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) = Tile(label = stringResource(R.string.weather_tile_sun), modifier = modifier) {
    val glyphs = weatherGlyphs()
    val arcColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val sunrise = snapshot.sunrise
    val sunset = snapshot.sunset
    if (sunrise == null || sunset == null) {
        EmDashBody()
        return@Tile
    }
    // The snapshot's hourly slice starts at "now"; its first entry anchors the
    // sun's position without a live clock (a refresh moves it).
    val now = snapshot.hourly.firstOrNull()?.time
    val dayFraction = now?.let { daylightFraction(sunrise, sunset, it) }
    Canvas(modifier = Modifier.fillMaxWidth().height(TileCanvasHeight)) {
        val radius = minOf(size.width / 2.4f, size.height.toFloat())
        val center = Offset(size.width / 2f, size.height)
        val stroke = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round)
        val arcPath = Path().apply {
            arcTo(
                rect =
                    androidx.compose.ui.geometry.Rect(
                        center.x - radius,
                        center.y - radius,
                        center.x + radius,
                        center.y + radius,
                    ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = true,
            )
        }
        drawPath(arcPath, arcColor, style = stroke)
        dayFraction?.let { fraction ->
            val angle = PI * (1 - fraction)
            val sunCenter =
                Offset(
                    center.x + (radius * cos(angle)).toFloat(),
                    center.y - (radius * sin(angle)).toFloat(),
                )
            drawCircle(glyphs.sun.copy(alpha = 0.30f), radius = 7.dp.toPx(), center = sunCenter)
            drawCircle(glyphs.sun, radius = 3.5f.dp.toPx(), center = sunCenter)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val formatter = clockTimeFormatter(is24Hour)
        Text(
            text = sunrise.format(formatter),
            style = MaterialTheme.typography.cardMeta(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = sunset.format(formatter),
            style = MaterialTheme.typography.cardMeta(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// Wind compass: a cardinal ring with an arrow flying in the wind's TO
// direction (MET reports the FROM bearing), speed beneath.
@Composable
private fun WindTile(
    snapshot: WeatherSnapshot,
    speedUnit: SpeedUnit,
    modifier: Modifier = Modifier,
) = Tile(label = stringResource(R.string.weather_metric_wind), modifier = modifier) {
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val needleColor = MaterialTheme.colorScheme.onSurface
    val direction = snapshot.windDirectionDeg
    Canvas(modifier = Modifier.fillMaxWidth().height(TileCanvasHeight)) {
        val radius = size.height / 2f - 2.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(ringColor, radius = radius, center = center, style = Stroke(1.5f.dp.toPx()))
        // Cardinal ticks.
        listOf(0f, 90f, 180f, 270f).forEach { deg ->
            rotate(deg, pivot = center) {
                drawLine(
                    ringColor,
                    Offset(center.x, center.y - radius),
                    Offset(center.x, center.y - radius + 3.dp.toPx()),
                    strokeWidth = 1.5f.dp.toPx(),
                )
            }
        }
        direction?.let {
            // FROM bearing + 180 = the direction the air moves toward.
            rotate((it + 180).toFloat() % 360f, pivot = center) {
                val tip = Offset(center.x, center.y - radius + 4.dp.toPx())
                drawLine(
                    needleColor,
                    Offset(center.x, center.y + radius * 0.45f),
                    tip,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                val head = Path().apply {
                    moveTo(tip.x, tip.y - 1.dp.toPx())
                    lineTo(tip.x - 3.5f.dp.toPx(), tip.y + 5.dp.toPx())
                    lineTo(tip.x + 3.5f.dp.toPx(), tip.y + 5.dp.toPx())
                    close()
                }
                drawPath(head, needleColor)
            }
        }
    }
    Text(
        text = windLabel(snapshot.windKmh, speedUnit),
        style = MaterialTheme.typography.cardMeta(),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

@Composable
private fun UvTile(
    snapshot: WeatherSnapshot,
    modifier: Modifier = Modifier,
) = Tile(label = stringResource(R.string.weather_metric_uv), modifier = modifier) {
    val uv = snapshot.uvIndex
    if (uv == null) {
        EmDashBody()
    } else {
        val dataColors = weatherDataColors()
        val band = uvBandIndex(uv)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(dataColors.uvScale[band])
            }
            Text(
                text = "${uv.roundToInt()}",
                style = MaterialTheme.typography.panelMetric(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        UvScaleBar(activeBand = band, colors = dataColors.uvScale)
    }
}

// The five WHO bands as a segmented bar, the active band at full strength.
@Composable
private fun UvScaleBar(
    activeBand: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) = Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    colors.forEachIndexed { i, color ->
        Canvas(modifier = Modifier.size(width = 14.dp, height = 4.dp)) {
            drawRoundRect(
                color = color,
                alpha = if (i == activeBand) 1f else 0.25f,
                cornerRadius = androidx.compose.ui.geometry
                    .CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun HumidityTile(
    snapshot: WeatherSnapshot,
    modifier: Modifier = Modifier,
) = Tile(label = stringResource(R.string.weather_metric_humidity), modifier = modifier) {
    val humidity = snapshot.humidityPercent
    if (humidity == null) {
        EmDashBody()
    } else {
        Text(
            text = "$humidity%",
            style = MaterialTheme.typography.panelMetric(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmDashBody(modifier: Modifier = Modifier) =
    Text(
        text = "—",
        style = MaterialTheme.typography.panelMetric(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        maxLines = 1,
    )

/** Fraction of daylight elapsed at [now], or null outside the daylight window. */
internal fun daylightFraction(
    sunrise: LocalTime,
    sunset: LocalTime,
    now: LocalTime,
): Float? {
    val total = Duration.between(sunrise, sunset).toMinutes()
    if (total <= 0) return null
    val elapsed = Duration.between(sunrise, now).toMinutes()
    return (elapsed.toFloat() / total).takeIf { it in 0f..1f }
}

// WHO UV bands: 0-2 low, 3-5 moderate, 6-7 high, 8-10 very high, 11+ extreme.
internal fun uvBandIndex(uv: Double): Int =
    when {
        uv < 3 -> 0
        uv < 6 -> 1
        uv < 8 -> 2
        uv < 11 -> 3
        else -> 4
    }
