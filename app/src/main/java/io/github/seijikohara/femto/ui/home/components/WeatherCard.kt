package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudSun
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Sun
import io.github.seijikohara.femto.data.HourlyForecast
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.data.WeatherSnapshot
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.locale.fromCelsius
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures
import io.github.seijikohara.femto.ui.theme.WeatherGlyphColors
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import java.time.Instant
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Weather card. Three vertical sections stacked on a 12dp rhythm
 * ([Arrangement.spacedBy], matching the mockup `.weather-card`
 * `row-gap: 12px`); content stays top-aligned and any spare height in the
 * fixed-height card falls to the bottom, so the forecast divider keeps even
 * 12dp breathing room instead of being stretched apart:
 *
 *  1. Head — 56sp temperature + glyph (per-condition colour) + city + short conditions.
 *  2. Metrics — Feels / Wind / Humid row at 14sp / 700 weight.
 *  3. Forecast — three hourly chips at 13sp temperatures.
 *
 * Typography and spacing follow `docs/design/dashboard-v2-mockup.html`
 * (`.weather-card` rules) verbatim — the same intentional relaxation of
 * the dashboard's 18sp body-size floor as [CalendarCard] applies here.
 */
@Composable
internal fun WeatherCard(
    snapshot: WeatherSnapshot?,
    city: String?,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = RoundedCornerShape(FemtoDimens.CardCorner),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(FemtoDimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (snapshot != null) {
            Head(snapshot, city, temperatureUnit)
            Metrics(snapshot, temperatureUnit)
            Forecast(snapshot.hourly, temperatureUnit)
        } else {
            EmptyState()
        }
    }
}

@Composable
private fun Head(
    snapshot: WeatherSnapshot,
    city: String?,
    temperatureUnit: TemperatureUnit,
) {
    val tempLabel = "${temperatureUnit.fromCelsius(snapshot.tempC).roundToInt()}"
    val glyphs = weatherGlyphs()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = tempLabel,
                style = MaterialTheme.typography.bigNumber(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = temperatureUnit.label(),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = glyphIconFor(snapshot.code, snapshot.isDay),
                    contentDescription = null,
                    tint = glyphTintFor(snapshot.code, snapshot.isDay, glyphs),
                    modifier = Modifier.size(FemtoDimens.WeatherGlyphLarge),
                )
                Text(
                    text = city.orEmpty(),
                    modifier = Modifier.weight(1f),
                    // The mockup specs 18px, sized for a short Latin city. The
                    // weather head slot (narrowed by the wide big-temperature)
                    // truncates 8+ char names like "Shinjuku" at 18sp, so the
                    // city stays at 16sp — within the card-metadata relaxation of
                    // the 18sp glance floor — to fit more multi-region city names.
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.01f).em,
                            lineHeight = 16.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = labelFor(snapshot.code).uppercase(),
                style = MaterialTheme.typography.sectionLabel(11, 0.14f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Metrics(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    Metric(label = "FEELS", value = "${temperatureUnit.fromCelsius(snapshot.apparentTempC).roundToInt()}°")
    val windMs = (snapshot.windKmh / 3.6).roundToInt()
    Metric(label = "WIND", value = "$windMs m/s")
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
        style = MaterialTheme.typography.sectionLabel(10, 0.1f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    Text(
        text = value,
        style =
            MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp,
                fontFeatureSettings = TabularFigures,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

@Composable
private fun Forecast(
    hourly: List<HourlyForecast>,
    temperatureUnit: TemperatureUnit,
) {
    val next = hourly.take(3)
    if (next.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            next.forEach { hour ->
                ForecastChip(hour, temperatureUnit, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ForecastChip(
    forecast: HourlyForecast,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val glyphs = weatherGlyphs()
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(FemtoDimens.ChipCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "%02dh".format(forecast.time.hour),
            style = MaterialTheme.typography.sectionLabel(10, 0.08f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Icon(
            imageVector = glyphIconFor(forecast.code, isDay = forecast.time.hour in 6..18),
            contentDescription = null,
            tint = glyphTintFor(forecast.code, isDay = forecast.time.hour in 6..18, glyphs),
            modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
        )
        Text(
            text = "${temperatureUnit.fromCelsius(forecast.tempC).roundToInt()}°",
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp,
                    fontFeatureSettings = TabularFigures,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyState() =
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
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
        WeatherCode.CLEAR -> if (isDay) Lucide.Sun else Lucide.Moon

        WeatherCode.PARTLY_CLOUDY -> Lucide.CloudSun

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
        -> Lucide.Cloud
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
        WeatherCode.CLEAR -> "Sunny"
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
            temperatureUnit = TemperatureUnit.CELSIUS,
        )
    }
}
