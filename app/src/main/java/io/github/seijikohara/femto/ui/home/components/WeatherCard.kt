package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.seijikohara.femto.data.HourlyForecast
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.data.WeatherSnapshot
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.WeatherGlyphColors
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import java.time.Instant
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Weather card. Three vertical sections:
 *
 *  1. Head — large temperature + glyph (per-condition colour) + city + short conditions
 *  2. Metrics — Feels / Wind / UV row
 *  3. Forecast — three hourly chips (border-separated)
 *
 * The glyph colour comes from [LocalWeatherGlyphs] (curated warm/cool
 * palette) rather than the dynamic colour scheme — sunny / cloudy / moon
 * need to read differently regardless of the user's accent.
 */
@Composable
internal fun WeatherCard(
    snapshot: WeatherSnapshot?,
    city: String?,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = RoundedCornerShape(FemtoDimens.OverlayCorner),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (snapshot != null) {
            Head(snapshot, city)
            Metrics(snapshot)
            Forecast(snapshot.hourly)
        } else {
            EmptyState()
        }
    }
}

@Composable
private fun Head(
    snapshot: WeatherSnapshot,
    city: String?,
) {
    val tempLabel = "${snapshot.tempC.roundToInt()}"
    val glyphs = weatherGlyphs()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = tempLabel,
                style =
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = FemtoDimens.BigNumberFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.045f).em,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "°C",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = glyphIconFor(snapshot.code, snapshot.isDay),
                    contentDescription = null,
                    tint = glyphTintFor(snapshot.code, snapshot.isDay, glyphs),
                    modifier = Modifier.size(FemtoDimens.WeatherGlyphLarge),
                )
                Text(
                    text = city.orEmpty(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = labelFor(snapshot.code).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metrics(snapshot: WeatherSnapshot) =
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Metric(label = "FEELS", value = "${snapshot.apparentTempC.roundToInt()}°")
        Metric(label = "WIND", value = "${snapshot.windKmh.roundToInt()} km/h")
        val humidityLabel = snapshot.humidityPercent?.let { "$it%" } ?: "—"
        Metric(label = "HUMID.", value = humidityLabel)
    }

@Composable
private fun Metric(
    label: String,
    value: String,
) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Forecast(hourly: List<HourlyForecast>) {
    val next = hourly.take(3)
    if (next.isEmpty()) return
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            next.forEach { hour ->
                ForecastChip(hour, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ForecastChip(
    forecast: HourlyForecast,
    modifier: Modifier = Modifier,
) {
    val glyphs = weatherGlyphs()
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = String.format("%02dh", forecast.time.hour),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = glyphIconFor(forecast.code, isDay = forecast.time.hour in 6..18),
            contentDescription = null,
            tint = glyphTintFor(forecast.code, isDay = forecast.time.hour in 6..18, glyphs),
            modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
        )
        Text(
            text = "${forecast.tempC.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyState() =
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Weather unavailable",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

private fun glyphIconFor(
    code: WeatherCode,
    isDay: Boolean,
): ImageVector =
    when (code) {
        WeatherCode.CLEAR -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.DarkMode

        WeatherCode.PARTLY_CLOUDY -> Icons.Outlined.WbSunny

        WeatherCode.CLOUDY,
        WeatherCode.FOG,
        WeatherCode.DRIZZLE,
        WeatherCode.RAIN,
        WeatherCode.FREEZING_RAIN,
        WeatherCode.SNOW,
        WeatherCode.SNOW_GRAINS,
        WeatherCode.RAIN_SHOWERS,
        WeatherCode.SNOW_SHOWERS,
        WeatherCode.THUNDERSTORM,
        WeatherCode.UNKNOWN,
        -> Icons.Outlined.Cloud
    }

private fun glyphTintFor(
    code: WeatherCode,
    isDay: Boolean,
    glyphs: WeatherGlyphColors,
): Color =
    when (code) {
        WeatherCode.CLEAR -> if (isDay) glyphs.sun else glyphs.moon
        WeatherCode.PARTLY_CLOUDY -> glyphs.cloudSun
        else -> glyphs.cloud
    }

private fun labelFor(code: WeatherCode): String =
    when (code) {
        WeatherCode.CLEAR -> "Clear"
        WeatherCode.PARTLY_CLOUDY -> "Partly cloudy"
        WeatherCode.CLOUDY -> "Cloudy"
        WeatherCode.FOG -> "Fog"
        WeatherCode.DRIZZLE -> "Drizzle"
        WeatherCode.RAIN -> "Rain"
        WeatherCode.FREEZING_RAIN -> "Freezing rain"
        WeatherCode.SNOW -> "Snow"
        WeatherCode.SNOW_GRAINS -> "Snow grains"
        WeatherCode.RAIN_SHOWERS -> "Rain showers"
        WeatherCode.SNOW_SHOWERS -> "Snow showers"
        WeatherCode.THUNDERSTORM -> "Thunderstorm"
        WeatherCode.UNKNOWN -> ""
    }

@PreviewLightDark
@Preview(name = "Weather card", widthDp = 240, heightDp = 224)
@Composable
private fun WeatherCardPreview() {
    FemtoTheme {
        WeatherCard(
            snapshot =
                WeatherSnapshot(
                    tempC = 13.0,
                    apparentTempC = 11.0,
                    code = WeatherCode.CLEAR,
                    windKmh = 11.0,
                    humidityPercent = 58,
                    uvIndex = 3.0,
                    isDay = true,
                    sunrise = LocalTime.of(6, 0),
                    sunset = LocalTime.of(18, 0),
                    hourly =
                        listOf(
                            HourlyForecast(LocalTime.of(9, 0), 14.0, WeatherCode.CLEAR),
                            HourlyForecast(LocalTime.of(10, 0), 16.0, WeatherCode.CLEAR),
                            HourlyForecast(LocalTime.of(11, 0), 17.0, WeatherCode.PARTLY_CLOUDY),
                        ),
                    daily = emptyList(),
                    fetchedAt = Instant.now(),
                ),
            city = "Košice",
        )
    }
}
