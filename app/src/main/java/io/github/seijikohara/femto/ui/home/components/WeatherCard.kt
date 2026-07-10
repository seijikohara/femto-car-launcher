package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Thermometer
import com.composables.icons.lucide.Wind
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MotionTier
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
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.glanceCaption
import io.github.seijikohara.femto.ui.theme.sectionLabel
import io.github.seijikohara.femto.ui.theme.singleLineBox
import io.github.seijikohara.femto.ui.theme.weatherGlyphs
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Weather card. Three vertical sections stacked on the
 * [FemtoDimens.CardSectionGapCompact] rhythm, sized to fit the card's capped
 * height via [FitWholeRows]:
 *
 *  1. Head — big temperature + a hero per-condition glyph. Always shown.
 *  2. Metrics — Feels / Wind / Humid row. Always shown.
 *  3. Forecast — hourly chips in a 3-column grid, one row per [FitWholeRows]
 *     child; only whole rows that fit the remaining height render; a row
 *     that would clip mid-glyph (a time label with no icon, or an icon with
 *     no temperature) is dropped instead, so a taller card simply shows more
 *     hours rather than capping the timeline at a fixed count.
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
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    motionTier: MotionTier = MotionTier.STANDARD,
) = Surface(
    // glassChrome clips to the rounded shape (keeping the ripple inside) and
    // paints the frosted-glass backdrop over the map; the maximize tap lives on
    // the whole populated card (see the FitWholeRows modifier below).
    modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
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
        // clickable + an explicit contentDescription (the AlbumArt idiom in
        // MusicCardMeta): onClickLabel alone sets only the OnClick action label, not
        // the node's content description, so the maximize entry stays discoverable.
        // Hoisted out of the semantics lambda, which is not @Composable. Applied to
        // the whole populated card (not just the head) so tapping anywhere opens the
        // full-screen panel; the content below has no other clickable children, so
        // there is no nested-click conflict.
        val weatherExpandLabel = stringResource(R.string.weather_expand)
        // FitWholeRows (not a scrolling column): Head + Metrics always show, and
        // only as many forecast rows as fully fit the remaining height render —
        // a row that would clip mid-glyph is dropped instead, which is what used
        // to leave a label-less trailing icon row (or, on the shortest card, a
        // temperature row sliced off entirely).
        FitWholeRows(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clickable { onExpand() }
                    .semantics { contentDescription = weatherExpandLabel }
                    // Compact padding/gap so head + metrics + forecast pack into the
                    // short head-unit info-pane card without needing to clip.
                    .padding(FemtoDimens.CardPaddingCompact),
            verticalGap = FemtoDimens.CardSectionGapCompact,
            mandatoryCount = 2,
        ) {
            // Head and Metrics each dissolve independently on a data refresh
            // (keyed on the whole snapshot, so a genuinely new fetch — not a
            // per-frame value — drives the fade); kept as two separate
            // Crossfade nodes rather than one wrapping both, so FitWholeRows
            // still sees them as the same two mandatory children it did before.
            Motion.ContentCrossfade(targetState = snapshot, tier = motionTier, label = "weatherHead") { current ->
                Head(current, temperatureUnit, asOf)
            }
            Motion.ContentCrossfade(targetState = snapshot, tier = motionTier, label = "weatherMetrics") { current ->
                Metrics(current, temperatureUnit, speedUnit)
            }
            snapshot.hourly.chunked(FORECAST_COLUMNS).forEach { rowHours ->
                ForecastRow(rowHours, snapshot.sunrise, snapshot.sunset, temperatureUnit, is24Hour)
            }
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
    // Big temperature on the left, the hero condition glyph beside it on the right;
    // SpaceBetween balances the two across the card width.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
                // Clamped to its own lineHeight: without this, the platform's default
                // font padding inflates the hero numeral's measured height well past
                // its nominal line box (55px vs. 42px at this size), which is exactly
                // the slack the forecast grid below needs to fit its first whole row
                // inside the card's capped height.
                val tempStyle = MaterialTheme.typography.bigNumber(size = 46.sp)
                Text(
                    text = tempLabel,
                    style = tempStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.singleLineBox(tempStyle),
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
        FemtoIcon(
            imageVector = glyphIconFor(snapshot.code, snapshot.isDay),
            contentDescription = stringResource(labelResFor(snapshot.code)),
            tint = glyphTintFor(snapshot.code, snapshot.isDay, glyphs),
            modifier = Modifier.size(FemtoDimens.WeatherGlyphHero),
        )
    }
}

// Feels / Wind / Humidity below the head, as three centred icon-over-value
// columns sharing the row evenly (weight) so each reading gets a full cell rather
// than crowding inline — the wind value needs the width on the narrow card. Each
// label rides its glyph's content description.
@Composable
private fun Metrics(
    snapshot: WeatherSnapshot,
    temperatureUnit: TemperatureUnit,
    speedUnit: SpeedUnit,
) {
    val humidityLabel = snapshot.humidityPercent?.let { "$it%" } ?: "—"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Metric(
            icon = Lucide.Thermometer,
            label = stringResource(R.string.weather_metric_feels),
            value = "${temperatureUnit.fromCelsius(snapshot.apparentTempC).roundToInt()}°",
            modifier = Modifier.weight(1f),
        )
        Metric(
            icon = Lucide.Wind,
            label = stringResource(R.string.weather_metric_wind),
            value = windLabel(snapshot.windKmh, speedUnit),
            modifier = Modifier.weight(1f),
        )
        Metric(
            icon = Lucide.Droplet,
            label = stringResource(R.string.weather_metric_humidity),
            value = humidityLabel,
            modifier = Modifier.weight(1f),
        )
    }
}

// One metric as a centred icon-over-value column.
@Composable
private fun Metric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(3.dp),
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = label,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
    Text(
        text = value,
        style = MaterialTheme.typography.glanceCaption(base = MaterialTheme.typography.cardMeta()),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

// The forecast lays its hours out three to a row (left-to-right, then
// top-to-bottom). Three keeps the chips legible even on the narrow head-unit
// card; [FitWholeRows] (the WeatherCard body) decides how many of these rows
// actually fit the card's remaining height, so the source hourly list is
// never pre-truncated here — a taller card simply gets more rows.
private const val FORECAST_COLUMNS = 3

// One forecast row: up to [FORECAST_COLUMNS] hour chips, padded with spacers on
// a short final row so the columns stay aligned. One top-level child of the
// WeatherCard body's [FitWholeRows] — accepted or dropped as a whole row, never
// mid-glyph.
@Composable
private fun ForecastRow(
    rowHours: List<HourlyForecast>,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    temperatureUnit: TemperatureUnit,
    is24Hour: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ForecastChipGap),
    ) {
        rowHours.forEach { hour ->
            ForecastChip(
                hour,
                sunrise,
                sunset,
                temperatureUnit,
                is24Hour,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(FORECAST_COLUMNS - rowHours.size) {
            Spacer(modifier = Modifier.weight(1f))
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
    // No cell background — the forecast reads as clean translucent columns on the
    // card's glass, consistent with the frameless overlays (an opaque chip box
    // popped as a solid panel inside the glass).
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = forecastHourLabel(forecast.time, is24Hour),
            style = MaterialTheme.typography.sectionLabel(10, 0.08f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        FemtoIcon(
            imageVector = glyphIconFor(forecast.code, isDay),
            contentDescription = null,
            tint = glyphTintFor(forecast.code, isDay, glyphs),
            modifier = Modifier.size(FemtoDimens.WeatherGlyphSmall),
        )
        Text(
            text = "${temperatureUnit.fromCelsius(forecast.tempC).roundToInt()}°",
            style =
                MaterialTheme.typography.glanceCaption(
                    base = MaterialTheme.typography.bodyMedium,
                    lineHeight = 15.sp,
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
        FemtoIcon(
            imageVector = Lucide.Cloud,
            contentDescription = stringResource(R.string.weather_unavailable),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
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
            onExpand = {},
        )
    }
}
