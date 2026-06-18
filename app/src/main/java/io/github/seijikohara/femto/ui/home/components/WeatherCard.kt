package io.github.seijikohara.femto.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudSun
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Thermometer
import com.composables.icons.lucide.Wind
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.weather.HourlyForecast
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.data.weather.isStale
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.locale.fromCelsius
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.locale.windLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures
import io.github.seijikohara.femto.ui.theme.WeatherGlyphColors
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Weather card. Three vertical sections stacked on the
 * [FemtoDimens.CardSectionGap] rhythm ([Arrangement.spacedBy]); the content is
 * top-aligned and scrolls if the card is too short to fit all three:
 *
 *  1. Head — big temperature + a hero per-condition glyph.
 *  2. Metrics — Feels / Wind / Humid row.
 *  3. Forecast — hourly chips on a horizontal timeline (three on the
 *     head-unit card, more when the card is wide enough).
 *
 * Typography and spacing originated in the `.weather-card` rules of the
 * retired dashboard-v2 design mockup — the same intentional relaxation of
 * the dashboard's 18sp body-size floor as [CalendarCard] applies here.
 */
@Composable
internal fun WeatherCard(
    snapshot: WeatherSnapshot?,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
    is24Hour: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = Surface(
    modifier = modifier
        // The whole card opens the default weather app (CATEGORY_APP_WEATHER,
        // available exactly from the minSdk). glassChrome clips to the rounded
        // shape (keeping the ripple inside) and paints the frosted-glass backdrop
        // over the map; the inner Column scrolls, so the clickable lives on the
        // Surface rather than competing with the scroll.
        .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
        .clickable(onClickLabel = stringResource(R.string.weather_open_app)) { onOpen() },
    shape = MaterialTheme.shapes.large,
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
) {
    if (snapshot != null) {
        // During a refresh outage the repository serves the same cached snapshot
        // (identical fetchedAt, conflated by the StateFlow), so the card ages it
        // locally: past WEATHER_STALE_THRESHOLD it surfaces an "as of HH:mm"
        // caption rather than presenting hours-old data as current.
        val asOf =
            if (rememberWeatherFresh(snapshot)) {
                null
            } else {
                stringResource(R.string.weather_as_of, asOfTimeLabel(snapshot.fetchedAt, is24Hour))
            }
        // verticalScroll is a safety net: fillMaxWidth (not fillMaxSize) lets the
        // content keep its intrinsic height, so on a card too short for the full
        // head + metrics + forecast it scrolls instead of clipping; on a tall
        // enough card it simply sits top-aligned.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // Compact padding/gap so head + metrics + forecast pack into the
                    // short head-unit info-pane card without needing to scroll/clip.
                    .padding(FemtoDimens.CardPaddingCompact),
            verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGapCompact),
        ) {
            Head(snapshot, temperatureUnit, asOf)
            Metrics(snapshot, temperatureUnit, speedUnit)
            Forecast(snapshot.hourly, snapshot.sunrise, snapshot.sunset, temperatureUnit, is24Hour)
        }
    } else {
        EmptyState()
    }
}

/**
 * Re-evaluate [snapshot] freshness on a tick so the card surfaces an "as of"
 * caption once the data ages past [WEATHER_STALE_THRESHOLD] during an outage.
 * The cached snapshot's `fetchedAt` does not change while the repository keeps
 * serving it (and the StateFlow conflates the identical value), so without this
 * local tick the card would show hours-old data as current. Mirrors
 * [rememberLocationFresh]; once stale, the loop stops until a new snapshot.
 */
@Composable
private fun rememberWeatherFresh(snapshot: WeatherSnapshot): Boolean =
    produceState(initialValue = !snapshot.isStale(Instant.now()), snapshot) {
        while (value) {
            delay(STALE_RECHECK_INTERVAL_MS)
            value = !snapshot.isStale(Instant.now())
        }
    }.value

// Cadence of the staleness re-check; the threshold is an hour, so a minute's
// granularity flips the caption within a minute of crossing it.
private const val STALE_RECHECK_INTERVAL_MS = 60_000L

private val AsOfFormatter12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

// "as of" needs minute precision, so it cannot reuse the forecast's hour-only
// 12h formatter; the 24h "HH:mm" formatter already carries minutes.
private fun asOfTimeLabel(
    fetchedAt: Instant,
    is24Hour: Boolean,
): String =
    fetchedAt
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(if (is24Hour) ForecastHourFormatter24 else AsOfFormatter12)

@Composable
private fun Head(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    asOfLabel: String?,
) {
    val tempLabel = "${temperatureUnit.fromCelsius(snapshot.tempC).roundToInt()}"
    val glyphs = weatherGlyphs()
    // Big temperature on the left, a hero condition glyph on the right. The city
    // and the textual condition were removed (the city clipped on the narrow card),
    // leaving the icon as the single condition cue, sized to balance the
    // temperature; the condition label survives as the icon's content description.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            // Stale-data eyebrow: only present once the snapshot ages past the
            // staleness threshold, so fresh readings carry no extra chrome.
            if (asOfLabel != null) {
                Text(
                    text = asOfLabel,
                    style = MaterialTheme.typography.sectionLabel(10, 0.08f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = tempLabel,
                    style = MaterialTheme.typography.bigNumber(size = 46.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = temperatureUnit.label(),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                )
            }
        }
        Icon(
            imageVector = glyphIconFor(snapshot.code, snapshot.isDay),
            contentDescription = stringResource(labelResFor(snapshot.code)),
            tint = glyphTintFor(snapshot.code, snapshot.isDay, glyphs),
            modifier = Modifier.size(FemtoDimens.WeatherGlyphHero),
        )
    }
}

@Composable
private fun Metrics(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    Metric(
        modifier = Modifier.weight(1f),
        icon = Lucide.Thermometer,
        label = stringResource(R.string.weather_metric_feels),
        value = "${temperatureUnit.fromCelsius(snapshot.apparentTempC).roundToInt()}°",
    )
    Metric(
        modifier = Modifier.weight(1f),
        icon = Lucide.Wind,
        label = stringResource(R.string.weather_metric_wind),
        value = windLabel(snapshot.windKmh, speedUnit),
    )
    val humidityLabel = snapshot.humidityPercent?.let { "$it%" } ?: "—"
    Metric(
        modifier = Modifier.weight(1f),
        icon = Lucide.Droplet,
        label = stringResource(R.string.weather_metric_humidity),
        value = humidityLabel,
    )
}

@Composable
private fun Metric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
    // The Lucide glyph stands in for the metric label (thermometer = feels-like,
    // wind, droplet = humidity); the text label moves to the icon's content
    // description so the value keeps the full column width and never clips on the
    // narrow head-unit card.
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
    Text(
        text = value,
        style =
            MaterialTheme.typography.cardMeta().copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TabularFigures,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

@Composable
private fun Forecast(
    hourly: List<HourlyForecast>,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    temperatureUnit: TemperatureUnit,
    is24Hour: Boolean,
) {
    if (hourly.isEmpty()) return
    // Hours read left-to-right as a timeline (deliberately not a vertical list
    // like the calendar agenda — the forecast answers "how does it trend", and
    // the horizontal axis carries that). The chip count derives from the card
    // width: never fewer than the three the head-unit card was designed
    // around, gaining hours on wider panels instead of stretching three chips.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val chipCount = (maxWidth / FemtoDimens.ForecastChipMinWidth).toInt().coerceIn(3, 6)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            hourly.take(chipCount).forEach { hour ->
                ForecastChip(hour, sunrise, sunset, temperatureUnit, is24Hour, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ForecastChip(
    forecast: HourlyForecast,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    temperatureUnit: TemperatureUnit,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) {
    val glyphs = weatherGlyphs()
    val isDay = isDaylight(forecast.time, sunrise, sunset)
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = forecastHourLabel(forecast.time, is24Hour),
            style = MaterialTheme.typography.sectionLabel(10, 0.08f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Icon(
            imageVector = glyphIconFor(forecast.code, isDay),
            contentDescription = null,
            tint = glyphTintFor(forecast.code, isDay, glyphs),
            modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
        )
        Text(
            text = "${temperatureUnit.fromCelsius(forecast.tempC).roundToInt()}°",
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = FemtoDimens.GlanceTextSize,
                    fontWeight = FontWeight.SemiBold,
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
        // The cold-start window reads as an error when shown as text. Per the
        // spec the empty state is an icon-only placeholder with no error copy;
        // the unavailable string moves to contentDescription so TalkBack still
        // announces the state.
        Icon(
            imageVector = Lucide.Cloud,
            contentDescription = stringResource(R.string.weather_unavailable),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }

// Forecast hour label in the same notation family as the clock and the
// calendar ("12:00" / "12 PM" via the locale's meridiem word), so the
// 12/24-hour setting is visibly honoured. The earlier compact "12h" / "12p"
// forms read as setting-agnostic shorthand.
private val ForecastHourFormatter24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ForecastHourFormatter12: DateTimeFormatter = DateTimeFormatter.ofPattern("h a")

private fun forecastHourLabel(
    time: LocalTime,
    is24Hour: Boolean,
): String = time.format(if (is24Hour) ForecastHourFormatter24 else ForecastHourFormatter12)

// Fixed daylight window used only when the snapshot carries no sunrise/sunset
// bounds, so a forecast hour past sunset still reads as night.
private val FallbackDaylightHours = 6..18

// Day/night drives the sun-vs-moon glyph for CLEAR. Use the snapshot's real
// sunrise/sunset when present; fall back to [FallbackDaylightHours] only when
// either bound is missing.
private fun isDaylight(
    time: LocalTime,
    sunrise: LocalTime?,
    sunset: LocalTime?,
): Boolean =
    if (sunrise != null && sunset != null) {
        time >= sunrise && time < sunset
    } else {
        time.hour in FallbackDaylightHours
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

@StringRes
private fun labelResFor(code: WeatherCode): Int =
    when (code) {
        WeatherCode.CLEAR -> R.string.weather_cond_sunny
        WeatherCode.PARTLY_CLOUDY -> R.string.weather_cond_partly_cloudy
        WeatherCode.CLOUDY -> R.string.weather_cond_cloudy
        WeatherCode.FOG -> R.string.weather_cond_fog
        WeatherCode.DRIZZLE -> R.string.weather_cond_drizzle
        WeatherCode.RAIN -> R.string.weather_cond_rain
        WeatherCode.FREEZING_RAIN -> R.string.weather_cond_freezing_rain
        WeatherCode.SNOW -> R.string.weather_cond_snow
        WeatherCode.SNOW_GRAINS -> R.string.weather_cond_snow_grains
        WeatherCode.RAIN_SHOWERS -> R.string.weather_cond_rain_showers
        WeatherCode.SNOW_SHOWERS -> R.string.weather_cond_snow_showers
        WeatherCode.THUNDERSTORM -> R.string.weather_cond_thunderstorm
        WeatherCode.UNKNOWN -> R.string.weather_cond_unknown
    }

// Sized to the head-unit binding: each top-row card is ~165 x 207 dp (half the
// info pane on the 853 x 512 dp / 5:3 projection).
@PreviewLightDark
@Preview(name = "Weather card", widthDp = 165, heightDp = 207)
@Composable
private fun WeatherCardPreview() {
    FemtoTheme {
        WeatherCard(
            snapshot =
                WeatherSnapshot(
                    tempC = 13.0,
                    apparentTempC = 11.0,
                    code = WeatherCode.CLOUDY,
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
            temperatureUnit = TemperatureUnit.CELSIUS,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            is24Hour = true,
            onOpen = {},
        )
    }
}
