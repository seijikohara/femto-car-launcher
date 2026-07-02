package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.weather.DailyForecast
import io.github.seijikohara.femto.data.weather.HourlyForecast
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.locale.fromCelsius
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.locale.windLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * Full-screen weather panel: current conditions, a longer hourly timeline, the
 * 5-day daily forecast, and the UV / sun-time details the compact card omits.
 */
@Composable
internal fun WeatherPanel(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
    is24Hour: Boolean,
    onOpenExternal: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = MaximizePanel(
    title = stringResource(R.string.weather_title),
    onClose = onClose,
    onOpenExternal = onOpenExternal,
    openExternalLabel = stringResource(R.string.weather_open_app),
    modifier = modifier,
    hazeState = hazeState,
    glassConfig = glassConfig,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        Hero(snapshot, temperatureUnit, speedUnit)
        if (snapshot.hourly.isNotEmpty()) HourlyStrip(snapshot, temperatureUnit, is24Hour)
        if (snapshot.daily.isNotEmpty()) DailyList(snapshot.daily, temperatureUnit)
        Details(snapshot, is24Hour)
    }
}

@Composable
private fun Hero(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
) {
    val glyphs = weatherGlyphs()
    // Single top-level Column so the composable emits from one root (the current
    // temperature row + the Feels/Wind/Humidity row beneath it).
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${temperatureUnit.fromCelsius(snapshot.tempC).roundToInt()}",
                    style = MaterialTheme.typography.bigNumber(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = temperatureUnit.label(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                )
            }
            FemtoIcon(
                imageVector = glyphIconFor(snapshot.code, snapshot.isDay),
                contentDescription = stringResource(labelResFor(snapshot.code)),
                tint = glyphTintFor(snapshot.code, snapshot.isDay, glyphs),
                modifier = Modifier.size(FemtoDimens.WeatherGlyphHero),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroMetric(
                stringResource(R.string.weather_metric_feels),
                "${temperatureUnit.fromCelsius(snapshot.apparentTempC).roundToInt()}°",
            )
            HeroMetric(stringResource(R.string.weather_metric_wind), windLabel(snapshot.windKmh, speedUnit))
            HeroMetric(
                stringResource(R.string.weather_metric_humidity),
                snapshot.humidityPercent?.let { "$it%" } ?: "—",
            )
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        text = label,
        style = MaterialTheme.typography.sectionLabel(10, 0.08f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

@Composable
private fun HourlyStrip(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    is24Hour: Boolean,
) {
    val glyphs = weatherGlyphs()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        snapshot.hourly.forEach { hour ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = forecastHourLabel(hour.time, is24Hour),
                    style = MaterialTheme.typography.sectionLabel(10, 0.08f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                FemtoIcon(
                    imageVector = glyphIconFor(hour.code, isDaylight(hour.time, snapshot.sunrise, snapshot.sunset)),
                    contentDescription = null,
                    tint = glyphTintFor(hour.code, isDaylight(hour.time, snapshot.sunrise, snapshot.sunset), glyphs),
                    modifier = Modifier.size(FemtoDimens.WeatherGlyphLarge),
                )
                Text(
                    text = "${temperatureUnit.fromCelsius(hour.tempC).roundToInt()}°",
                    style = MaterialTheme.typography.cardMeta(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DailyList(
    daily: List<DailyForecast>,
    temperatureUnit: TemperatureUnit,
) {
    val glyphs = weatherGlyphs()
    // Read the platform Locale through LocalLocale rather than Locale.getDefault():
    // the latter does not read observable Compose state, so the weekday labels
    // would not recompose if the user changes the system locale mid-session.
    val locale = LocalLocale.current.platformLocale
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        daily.forEach { day ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(64.dp),
                    maxLines = 1,
                )
                FemtoIcon(
                    imageVector = glyphIconFor(day.code, isDay = true),
                    contentDescription = stringResource(labelResFor(day.code)),
                    tint = glyphTintFor(day.code, isDay = true, glyphs),
                    modifier = Modifier.size(FemtoDimens.WeatherGlyphLarge),
                )
                Text(
                    text = "${temperatureUnit.fromCelsius(
                        day.tempMaxC,
                    ).roundToInt()}° / ${temperatureUnit.fromCelsius(day.tempMinC).roundToInt()}°",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private val DetailTimeFormatter24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DetailTimeFormatter12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun Details(
    snapshot: WeatherSnapshot,
    is24Hour: Boolean,
) {
    val formatter = if (is24Hour) DetailTimeFormatter24 else DetailTimeFormatter12
    val parts =
        buildList {
            snapshot.uvIndex?.let { add(stringResource(R.string.weather_uv, it.roundToInt().toString())) }
            snapshot.sunrise?.let { add(stringResource(R.string.weather_sunrise, it.format(formatter))) }
            snapshot.sunset?.let { add(stringResource(R.string.weather_sunset, it.format(formatter))) }
        }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("   ·   "),
        style = MaterialTheme.typography.cardMeta(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@PreviewLightDark
@androidx.compose.ui.tooling.preview.Preview(name = "Weather panel · head unit", widthDp = 805, heightDp = 400)
@Composable
private fun WeatherPanelPreview() {
    FemtoTheme {
        WeatherPanel(
            snapshot =
                WeatherSnapshot(
                    tempC = 18.0,
                    apparentTempC = 17.0,
                    code = WeatherCode.CLEAR,
                    windKmh = 9.6,
                    humidityPercent = 58,
                    uvIndex = 4.0,
                    isDay = true,
                    sunrise = LocalTime.of(5, 42),
                    sunset = LocalTime.of(19, 14),
                    hourly =
                        listOf(
                            HourlyForecast(LocalTime.of(12, 0), 19.0, WeatherCode.CLEAR),
                            HourlyForecast(LocalTime.of(13, 0), 20.0, WeatherCode.CLEAR),
                            HourlyForecast(LocalTime.of(14, 0), 21.0, WeatherCode.PARTLY_CLOUDY),
                        ),
                    daily =
                        listOf(
                            DailyForecast(LocalDate.of(2026, 5, 1), 22.0, 14.0, WeatherCode.CLEAR),
                            DailyForecast(LocalDate.of(2026, 5, 2), 23.0, 15.0, WeatherCode.PARTLY_CLOUDY),
                            DailyForecast(LocalDate.of(2026, 5, 3), 21.0, 14.0, WeatherCode.RAIN),
                        ),
                    fetchedAt = Instant.now(),
                ),
            temperatureUnit = TemperatureUnit.CELSIUS,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            is24Hour = true,
            onOpenExternal = {},
            onClose = {},
        )
    }
}
