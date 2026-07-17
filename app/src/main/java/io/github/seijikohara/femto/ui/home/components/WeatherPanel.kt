package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SunMedium
import com.composables.icons.lucide.Sunrise
import com.composables.icons.lucide.Sunset
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
import io.github.seijikohara.femto.ui.locale.windLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.FitText
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.WeatherGlyphColors
import io.github.seijikohara.femto.ui.theme.attributionCredit
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.panelMetric
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import kotlin.math.roundToInt

/**
 * Full-screen weather panel: current conditions, a full-width hourly timeline,
 * the daily forecast, and the UV / sun-time details the compact card omits.
 *
 * Landscape (wider than tall — the reference head unit and beyond) puts the
 * daily forecast and the UV/sunrise/sunset details side by side as two
 * columns so the panel's right half carries content instead of sitting empty;
 * portrait keeps them stacked full-width. Either way the hourly strip spans
 * the panel's full width — a single row when there is room for every hour,
 * wrapped into even rows otherwise — so no forecast hour is ever silently cut.
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
    // portrait (the phone-mount case, 412x915) keeps the daily/detail columns
    // stacked full-width instead of squeezing two columns into a narrow panel.
    val portrait = maxHeight > maxWidth
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        // Each region dissolves on a data refresh — keyed on the whole snapshot,
        // so a genuinely new fetch (not a per-frame value) drives the fade,
        // mirroring the compact WeatherCard. The regions fade individually rather
        // than wrapping the scrolling Column, so a refresh keeps the user's scroll
        // position instead of snapping back to the top.
        Motion.ContentCrossfade(targetState = snapshot, tier = motionTier, label = "weatherPanelHero") {
            Hero(it, temperatureUnit, speedUnit)
        }
        if (snapshot.hourly.isNotEmpty()) {
            Motion.ContentCrossfade(targetState = snapshot, tier = motionTier, label = "weatherPanelHourly") {
                HourlyStrip(it, temperatureUnit, is24Hour, portrait)
            }
        }
        if (portrait) {
            Motion.ContentCrossfade(targetState = snapshot, tier = motionTier, label = "weatherPanelDetails") {
                Column(verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap)) {
                    if (it.daily.isNotEmpty()) DailyList(it.daily, temperatureUnit)
                    Details(it, is24Hour)
                }
            }
        } else {
            Motion.ContentCrossfade(targetState = snapshot, tier = motionTier, label = "weatherPanelDetails") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
                ) {
                    if (it.daily.isNotEmpty()) {
                        DailyList(it.daily, temperatureUnit, modifier = Modifier.weight(1f))
                    }
                    Details(it, is24Hour, modifier = Modifier.weight(1f))
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
    speedUnit: SpeedUnit,
) {
    val glyphs = weatherGlyphs()
    // Single top-level Column so the composable emits from one root (the
    // current-temperature group + the Feels/Wind/Humidity row beneath it).
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Temperature and the condition glyph read as one tight group instead
        // of bookending the row — a wide panel otherwise opens a dead gap
        // between them (see the audit note on the pre-redesign layout).
        Row(
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
                    // accessible label, so the icon itself carries none (avoids
                    // TalkBack announcing the same condition twice).
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
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroMetric(
                stringResource(R.string.weather_metric_feels),
                "${temperatureUnit.fromCelsius(snapshot.apparentTempC).roundToInt()}°",
                modifier = Modifier.weight(1f),
            )
            HeroMetric(
                stringResource(R.string.weather_metric_wind),
                windLabel(snapshot.windKmh, speedUnit),
                modifier = Modifier.weight(1f),
            )
            HeroMetric(
                stringResource(R.string.weather_metric_humidity),
                snapshot.humidityPercent?.let { "$it%" } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// One Feels/Wind/Humidity cell. Equal-weight columns (set by the caller) turn
// this into a real grid — fixed column widths rather than content-width
// spacing, which used to leave uneven gaps between the shorter and longer
// values.
@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    Text(
        text = label,
        style = MaterialTheme.typography.sectionLabel(12),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.panelMetric(),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

// Portrait wraps the hourly strip into rows of this many columns instead of
// scrolling, so every forecast hour stays reachable without a hidden-overflow
// affordance; landscape has enough width to lay every hour out in one row
// (see the `columns` computation in [HourlyStrip]).
private const val HOURLY_PORTRAIT_COLUMNS = 6

@Composable
private fun HourlyStrip(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    is24Hour: Boolean,
    portrait: Boolean,
) {
    val glyphs = weatherGlyphs()
    // Landscape: one full-width row holding every hour (there is room for all
    // of them at the reference head-unit width). Portrait: wrap into fixed
    // columns instead — a horizontal scroll silently dropped the trailing
    // hours off the right edge with no affordance hinting more existed.
    val columns = if (portrait) HOURLY_PORTRAIT_COLUMNS else snapshot.hourly.size
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        snapshot.hourly.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { hour ->
                    HourlyColumn(
                        hour = hour,
                        sunrise = snapshot.sunrise,
                        sunset = snapshot.sunset,
                        temperatureUnit = temperatureUnit,
                        is24Hour = is24Hour,
                        glyphs = glyphs,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pads a short trailing row so its columns still line up under
                // the full rows above it instead of stretching wider.
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HourlyColumn(
    hour: HourlyForecast,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    temperatureUnit: TemperatureUnit,
    is24Hour: Boolean,
    glyphs: WeatherGlyphColors,
    modifier: Modifier = Modifier,
) {
    val isDay = isDaylight(hour.time, sunrise, sunset)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FitText(
            // The landscape strip divides one row across every forecast hour, so
            // a narrow projection, the wider 12-hour "12 PM" form, or a raised
            // font scale can starve the column; shrink the label to fit instead
            // of letting the centered text clip at both edges.
            text = forecastHourLabel(hour.time, is24Hour),
            style = MaterialTheme.typography.sectionLabel(12),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minFontSize = FemtoDimens.TextXs,
        )
        FemtoIcon(
            imageVector = glyphIconFor(hour.code, isDay),
            contentDescription = null,
            tint = glyphTintFor(hour.code, isDay, glyphs),
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

@Composable
private fun DailyList(
    daily: List<DailyForecast>,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val glyphs = weatherGlyphs()
    // Read the platform Locale through LocalLocale rather than Locale.getDefault():
    // the latter does not read observable Compose state, so the weekday labels
    // would not recompose if the user changes the system locale mid-session.
    val locale = LocalLocale.current.platformLocale
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

// UV index + sunrise + sunset as a small vertical detail list (icon + full
// localized sentence per row) rather than one joined caption line, so the
// wide panel's daily/detail column reads as a real content block instead of
// a stray line of text at the bottom.
@Composable
private fun Details(
    snapshot: WeatherSnapshot,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) {
    val formatter = clockTimeFormatter(is24Hour)
    val rows =
        buildList {
            snapshot.uvIndex?.let {
                add(Lucide.SunMedium to stringResource(R.string.weather_uv, it.roundToInt().toString()))
            }
            snapshot.sunrise?.let {
                add(
                    Lucide.Sunrise to stringResource(R.string.weather_sunrise, it.format(formatter)),
                )
            }
            snapshot.sunset?.let { add(Lucide.Sunset to stringResource(R.string.weather_sunset, it.format(formatter))) }
        }
    if (rows.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { (icon, text) -> DetailRow(icon, text) }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
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
