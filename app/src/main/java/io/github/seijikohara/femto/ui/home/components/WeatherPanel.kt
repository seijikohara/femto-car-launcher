package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.weather.DailyForecast
import io.github.seijikohara.femto.data.weather.HourlyForecast
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.locale.fromCelsius
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.attributionCredit
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.weatherDataColors
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * Full-screen weather panel, data-graphics first: a hero row (temperature,
 * condition, today's high/low, and a precipitation nowcast), the 24 h
 * temperature curve as the centerpiece, a 7-day outlook drawn as shared-scale
 * temperature range bars, and a 2x2 grid of visual metric tiles (sun arc, wind
 * compass, UV, humidity).
 *
 * Landscape (wider than tall — the reference head unit) puts the daily bars and
 * the tile grid side by side; portrait stacks them. The curve always spans the
 * panel's full width.
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
    motionTier: MotionTier = MotionTier.STANDARD,
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
    // Landscape reads wider than tall (the reference head unit is 853x512);
    // portrait (the phone-mount case, 412x915) stacks the two lower sections.
    val portrait = maxHeight > maxWidth
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        // Each region dissolves on a data refresh — keyed on the whole snapshot,
        // so a genuinely new fetch (not a per-frame value) drives the fade. The
        // regions fade individually rather than wrapping the scrolling Column,
        // so a refresh keeps the user's scroll position.
        Motion.ContentCrossfade(targetState = snapshot, tier = motionTier, label = "weatherPanelHero") {
            Hero(it, temperatureUnit, is24Hour)
        }
        if (snapshot.hourly.size >= 2) {
            Motion.ContentCrossfade(targetState = snapshot, tier = motionTier, label = "weatherPanelCurve") {
                WeatherTempCurve(
                    hourly = it.hourly,
                    sunrise = it.sunrise,
                    sunset = it.sunset,
                    is24Hour = is24Hour,
                    motionTier = motionTier,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Motion.ContentCrossfade(targetState = snapshot, tier = motionTier, label = "weatherPanelDetails") {
            if (portrait) {
                Column(verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap)) {
                    if (it.daily.isNotEmpty()) DailyRangeList(it, temperatureUnit)
                    WeatherTileGrid(it, speedUnit, is24Hour)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
                ) {
                    if (it.daily.isNotEmpty()) {
                        DailyRangeList(it, temperatureUnit, modifier = Modifier.weight(1f))
                    }
                    WeatherTileGrid(it, speedUnit, is24Hour, modifier = Modifier.weight(1f))
                }
            }
        }
        // CC BY 4.0 credit for the forecast data — MET's terms require visible
        // attribution wherever the data is presented; the licenses screen
        // carries the full license entry. Static legal text, not glance
        // content, so attributionCredit's sub-floor size applies (see Type.kt).
        Text(
            text = stringResource(R.string.weather_attribution),
            style = MaterialTheme.typography.attributionCredit(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Hero(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    is24Hour: Boolean,
) {
    val glyphs = weatherGlyphs()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FemtoIcon(
                imageVector = glyphIconFor(snapshot.code, snapshot.isDay),
                // Decorative: the condition name beneath the glyph is the
                // accessible label, so the icon itself carries none.
                contentDescription = null,
                tint = glyphTintFor(snapshot.code, snapshot.isDay, glyphs),
                modifier = Modifier.size(FemtoDimens.WeatherGlyphHero),
            )
            Text(
                text = stringResource(labelResFor(snapshot.code)),
                style = MaterialTheme.typography.cardMeta(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            snapshot.daily.firstOrNull()?.let { today ->
                Text(
                    text =
                        stringResource(
                            R.string.weather_high_low,
                            temperatureUnit.fromCelsius(today.tempMaxC).roundToInt(),
                            temperatureUnit.fromCelsius(today.tempMinC).roundToInt(),
                        ),
                    style = MaterialTheme.typography.sectionLabel(14),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            NowcastLine(snapshot, is24Hour)
        }
    }
}

// The hero's precipitation callout ("Rain around 14:00 · 60%"), tinted with the
// precipitation blue. Absent entirely on a dry outlook — no placeholder.
@Composable
private fun NowcastLine(
    snapshot: WeatherSnapshot,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) {
    val outlook = precipOutlookOrNull(snapshot.hourly) ?: return
    val base =
        when (outlook) {
            is PrecipOutlook.Now -> {
                stringResource(
                    if (outlook.snow) R.string.weather_nowcast_snow_now else R.string.weather_nowcast_rain_now,
                )
            }

            is PrecipOutlook.Upcoming -> {
                stringResource(
                    if (outlook.snow) R.string.weather_nowcast_snow_at else R.string.weather_nowcast_rain_at,
                    forecastHourLabel(outlook.at, is24Hour),
                )
            }
        }
    val text =
        outlook.probabilityPercent
            ?.let { "$base · $it%" }
            ?: base
    Text(
        text = text,
        style = MaterialTheme.typography.cardMeta(),
        color = weatherDataColors().precipitation,
        modifier = modifier,
        maxLines = 1,
    )
}

// Fixed label columns in the daily rows so the bars align into one shared
// chart: weekday / glyph / low / bar / high / probability badge.
private val DailyTempLabelWidth = 40.dp
private val DailyBadgeWidth = 40.dp
private val DailyBarHeight = 6.dp

// Probability below this is noise at a glance; the curve carries the detail.
private const val PRECIP_BADGE_MIN_PERCENT = 30

/**
 * The 7-day outlook as shared-scale temperature range bars: each day's min→max
 * spans a bar coloured by the temperature ramp, all rows on one week-wide
 * scale so relative warmth is comparable down the column. The current day
 * carries a dot at the present temperature; wet days a probability badge.
 */
@Composable
private fun DailyRangeList(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val daily = snapshot.daily
    val glyphs = weatherGlyphs()
    val dataColors = weatherDataColors()
    // Read the platform Locale through LocalLocale rather than Locale.getDefault():
    // the latter does not read observable Compose state, so the weekday labels
    // would not recompose if the user changes the system locale mid-session.
    val locale = LocalLocale.current.platformLocale
    val weekMin = daily.minOf { it.tempMinC }
    val weekMax = daily.maxOf { it.tempMaxC }
    val span = (weekMax - weekMin).coerceAtLeast(1.0)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        daily.forEachIndexed { index, day ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(44.dp),
                    maxLines = 1,
                )
                FemtoIcon(
                    imageVector = glyphIconFor(day.code, isDay = true),
                    contentDescription = stringResource(labelResFor(day.code)),
                    tint = glyphTintFor(day.code, isDay = true, glyphs),
                    modifier = Modifier.size(FemtoDimens.WeatherGlyphLarge),
                )
                Text(
                    text = "${temperatureUnit.fromCelsius(day.tempMinC).roundToInt()}°",
                    style = MaterialTheme.typography.cardMeta(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(DailyTempLabelWidth),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                DailyRangeBar(
                    day = day,
                    weekMin = weekMin,
                    span = span,
                    // The current day anchors "you are here" on the shared scale.
                    currentTempC = snapshot.tempC.takeIf { index == 0 },
                    modifier = Modifier.weight(1f).height(DailyBarHeight),
                )
                Text(
                    text = "${temperatureUnit.fromCelsius(day.tempMaxC).roundToInt()}°",
                    style = MaterialTheme.typography.cardMeta(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(DailyTempLabelWidth),
                    maxLines = 1,
                )
                val probability = day.precipitationProbabilityPercent
                if (probability != null && probability >= PRECIP_BADGE_MIN_PERCENT) {
                    Text(
                        text = "$probability%",
                        style = MaterialTheme.typography.cardMeta(),
                        color = dataColors.precipitation,
                        modifier = Modifier.width(DailyBadgeWidth),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                } else {
                    Spacer(modifier = Modifier.width(DailyBadgeWidth))
                }
            }
        }
    }
}

@Composable
private fun DailyRangeBar(
    day: DailyForecast,
    weekMin: Double,
    span: Double,
    currentTempC: Double?,
    modifier: Modifier = Modifier,
) {
    val dataColors = weatherDataColors()
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val dotColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(trackColor, cornerRadius = radius)
        val startFraction = ((day.tempMinC - weekMin) / span).toFloat().coerceIn(0f, 1f)
        val endFraction = ((day.tempMaxC - weekMin) / span).toFloat().coerceIn(0f, 1f)
        val startX = startFraction * size.width
        val endX = (endFraction * size.width).coerceAtLeast(startX + size.height)
        drawRoundRect(
            brush =
                Brush.horizontalGradient(
                    colors =
                        listOf(
                            tempColorAt(dataColors.tempStops, day.tempMinC.toFloat()),
                            tempColorAt(dataColors.tempStops, day.tempMaxC.toFloat()),
                        ),
                    startX = startX,
                    endX = endX,
                ),
            topLeft = Offset(startX, 0f),
            size = Size(endX - startX, size.height),
            cornerRadius = radius,
        )
        currentTempC?.let { current ->
            val fraction = ((current - weekMin) / span).toFloat().coerceIn(0f, 1f)
            drawCircle(dotColor, radius = size.height * 0.66f, center = Offset(fraction * size.width, size.height / 2f))
        }
    }
}

@PreviewLightDark
@androidx.compose.ui.tooling.preview.Preview(name = "Weather panel · head unit", widthDp = 805, heightDp = 400)
@androidx.compose.ui.tooling.preview.Preview(name = "Weather panel · portrait", widthDp = 364, heightDp = 700)
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
                    windDirectionDeg = 225.0,
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
                            HourlyForecast(LocalTime.of(15, 0), 21.0, WeatherCode.PARTLY_CLOUDY),
                            HourlyForecast(LocalTime.of(16, 0), 20.0, WeatherCode.CLOUDY),
                            HourlyForecast(LocalTime.of(17, 0), 18.0, WeatherCode.CLOUDY, 0.4, 45),
                            HourlyForecast(LocalTime.of(18, 0), 16.0, WeatherCode.RAIN, 1.2, 70),
                            HourlyForecast(LocalTime.of(19, 0), 15.0, WeatherCode.RAIN, 0.8, 60),
                            HourlyForecast(LocalTime.of(20, 0), 14.0, WeatherCode.CLOUDY),
                            HourlyForecast(LocalTime.of(21, 0), 13.0, WeatherCode.CLEAR),
                            HourlyForecast(LocalTime.of(22, 0), 13.0, WeatherCode.CLEAR),
                            HourlyForecast(LocalTime.of(23, 0), 12.0, WeatherCode.CLEAR),
                        ),
                    daily =
                        listOf(
                            DailyForecast(LocalDate.of(2026, 5, 1), 22.0, 14.0, WeatherCode.CLEAR),
                            DailyForecast(LocalDate.of(2026, 5, 2), 23.0, 15.0, WeatherCode.PARTLY_CLOUDY),
                            DailyForecast(LocalDate.of(2026, 5, 3), 21.0, 14.0, WeatherCode.RAIN, 65),
                            DailyForecast(LocalDate.of(2026, 5, 4), 20.0, 13.0, WeatherCode.CLOUDY),
                            DailyForecast(LocalDate.of(2026, 5, 5), 22.0, 14.0, WeatherCode.CLEAR),
                            DailyForecast(LocalDate.of(2026, 5, 6), 18.0, 12.0, WeatherCode.RAIN_SHOWERS, 75),
                            DailyForecast(LocalDate.of(2026, 5, 7), 21.0, 13.0, WeatherCode.PARTLY_CLOUDY),
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
