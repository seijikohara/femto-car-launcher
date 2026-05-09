package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Brightness5
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightlightRound
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.util.LocalePreferences
import io.github.seijikohara.femto.data.DailyForecast
import io.github.seijikohara.femto.data.HourlyForecast
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.data.WeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.fromKilometersPerHour
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun WeatherPanel(
    snapshot: WeatherSnapshot?,
    unit: String,
    speedUnit: SpeedUnit,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Column(
        modifier = Modifier.padding(FemtoDimens.GridGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The hero icon is the role anchor; a "WEATHER" header label would only add
        // noise. Same convention applies to the clock and now-playing panels.
        if (snapshot == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HeroSection(snapshot, unit)
            if (snapshot.hourly.isNotEmpty()) {
                SectionDivider()
                HourlySection(snapshot.hourly, unit, is24Hour)
            }
            if (snapshot.daily.isNotEmpty()) {
                SectionDivider()
                DailySection(snapshot.daily, unit)
            }
            SectionDivider()
            AstroSection(snapshot, speedUnit, is24Hour)
        }
    }
}

@Composable
private fun SectionDivider() =
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )

@Composable
private fun HeroSection(
    snapshot: WeatherSnapshot,
    unit: String,
) {
    val secondary =
        listOf(
            conditionLabel(snapshot.code),
            "Feels ${formatTemperature(snapshot.apparentTempC, unit)}",
        ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = imageVectorFor(snapshot.code, snapshot.isDay),
            contentDescription = conditionLabel(snapshot.code),
            modifier = Modifier.size(FemtoDimens.HeroIconSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatTemperature(snapshot.tempC, unit),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HourlySection(
    hourly: List<HourlyForecast>,
    unit: String,
    is24Hour: Boolean,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    // The condition is communicated by the hero icon; the strip is a glance-only
    // temperature trend. Dropping the per-entry icon keeps four 12-hour entries
    // ("1 PM 70°") on a single row within the right-column width.
    hourly.forEach { entry ->
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.time.format(hourOnlyFormatter(is24Hour)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = compactTemperature(entry.tempC, unit),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DailySection(
    daily: List<DailyForecast>,
    unit: String,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Five-day outlook: weekday abbreviation, condition icon, and high/low.
    // Equal-weight columns keep entries aligned regardless of "Sun" vs "Wed".
    daily.take(5).forEachIndexed { index, entry ->
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (index == 0) "Today" else entry.date.format(weekdayFormatter()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = imageVectorFor(entry.code, isDay = true),
                contentDescription = null,
                modifier = Modifier.size(FemtoDimens.InlineIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${compactTemperature(entry.tempMaxC, unit)} / ${compactTemperature(entry.tempMinC, unit)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AstroSection(
    snapshot: WeatherSnapshot,
    speedUnit: SpeedUnit,
    is24Hour: Boolean,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    snapshot.sunrise?.let {
        AstroChip(
            icon = Icons.Outlined.WbTwilight,
            contentDescription = "Sunrise",
            label = it.format(clockFormatter(is24Hour)),
        )
    }
    snapshot.sunset?.let {
        AstroChip(
            icon = Icons.Outlined.Bedtime,
            contentDescription = "Sunset",
            label = it.format(clockFormatter(is24Hour)),
        )
    }
    AstroChip(
        icon = Icons.Outlined.Air,
        contentDescription = "Wind",
        label = formatWind(snapshot.windKmh, speedUnit),
    )
    snapshot.uvIndex?.let {
        AstroChip(
            icon = Icons.Outlined.Brightness5,
            contentDescription = "UV index",
            label = "UV ${it.roundToInt()}",
        )
    }
}

@Composable
private fun AstroChip(
    icon: ImageVector,
    contentDescription: String,
    label: String,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun clockFormatter(is24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())

private fun weekdayFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

// Compact form for the hourly strip — minutes are always 00 from the API, and
// dropping them keeps four entries on one line within the right-column width.
private fun hourOnlyFormatter(is24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH" else "h a", Locale.getDefault())

private fun formatTemperature(
    tempC: Double,
    unit: String,
): String =
    when (unit) {
        LocalePreferences.TemperatureUnit.FAHRENHEIT -> "${(tempC * 9 / 5 + 32).roundToInt()}°F"
        LocalePreferences.TemperatureUnit.KELVIN -> "${(tempC + 273.15).roundToInt()}K"
        else -> "${tempC.roundToInt()}°C"
    }

private fun compactTemperature(
    tempC: Double,
    unit: String,
): String =
    when (unit) {
        LocalePreferences.TemperatureUnit.FAHRENHEIT -> "${(tempC * 9 / 5 + 32).roundToInt()}°"
        LocalePreferences.TemperatureUnit.KELVIN -> "${(tempC + 273.15).roundToInt()}"
        else -> "${tempC.roundToInt()}°"
    }

private fun formatWind(
    kmh: Double,
    speedUnit: SpeedUnit,
): String = "${speedUnit.fromKilometersPerHour(kmh).roundToInt()} ${speedUnit.label()}"

private fun imageVectorFor(
    code: WeatherCode,
    isDay: Boolean,
): ImageVector =
    when (code) {
        WeatherCode.CLEAR -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightlightRound

        WeatherCode.PARTLY_CLOUDY -> if (isDay) Icons.Outlined.WbCloudy else Icons.Outlined.Cloud

        WeatherCode.CLOUDY -> Icons.Outlined.Cloud

        WeatherCode.FOG -> Icons.Outlined.Cloud

        WeatherCode.DRIZZLE,
        WeatherCode.RAIN,
        WeatherCode.RAIN_SHOWERS,
        WeatherCode.FREEZING_RAIN,
        -> Icons.Outlined.Grain

        WeatherCode.SNOW,
        WeatherCode.SNOW_SHOWERS,
        WeatherCode.SNOW_GRAINS,
        -> Icons.Outlined.AcUnit

        WeatherCode.THUNDERSTORM -> Icons.Outlined.Thunderstorm

        WeatherCode.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
    }

private fun conditionLabel(code: WeatherCode): String =
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
        WeatherCode.UNKNOWN -> "—"
    }
