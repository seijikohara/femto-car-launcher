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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightlightRound
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (snapshot == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HeroSection(snapshot, unit, speedUnit)
            if (snapshot.hourly.isNotEmpty()) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                // Hourly outlook is the driving-relevant forecast: the next few
                // hours decide whether to expect rain en route. Daily highs/lows
                // are trip-planning data, less useful from the driver's seat.
                HourlySection(snapshot.hourly, unit, is24Hour)
            }
        }
    }
}

@Composable
private fun HeroSection(
    snapshot: WeatherSnapshot,
    unit: String,
    speedUnit: SpeedUnit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    Icon(
        imageVector = imageVectorFor(snapshot.code, snapshot.isDay),
        contentDescription = conditionLabel(snapshot.code),
        modifier = Modifier.size(FemtoDimens.HeroIconSize),
        tint = MaterialTheme.colorScheme.onSurface,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Primary line: temperature (glance value, accent-tinted) and condition.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = formatTemperature(snapshot.tempC, unit),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = conditionLabel(snapshot.code),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        // Secondary line: feels-like, wind, UV. Concentrating these here lets the
        // card stay at two sections (hero + 5-day) instead of the previous four.
        Text(
            text = secondaryLine(snapshot, unit, speedUnit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun secondaryLine(
    snapshot: WeatherSnapshot,
    unit: String,
    speedUnit: SpeedUnit,
): String =
    buildList {
        add("Feels ${formatTemperature(snapshot.apparentTempC, unit)}")
        add("Wind ${speedUnit.fromKilometersPerHour(snapshot.windKmh).roundToInt()} ${speedUnit.label()}")
        snapshot.uvIndex?.let { add("UV ${it.roundToInt()}") }
    }.joinToString(" · ")

@Composable
private fun HourlySection(
    hourly: List<HourlyForecast>,
    unit: String,
    is24Hour: Boolean,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Five equal columns: hour, condition icon, temperature. The first entry
    // is labelled "Now" so the strip reads as a near-term timeline starting
    // from the current moment.
    hourly.take(5).forEachIndexed { index, entry ->
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (index == 0) "Now" else entry.time.format(hourOnlyFormatter(is24Hour)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = imageVectorFor(entry.code, isDay = true),
                contentDescription = null,
                modifier = Modifier.size(FemtoDimens.HeroIconSize),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = compactTemperature(entry.tempC, unit),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Open-Meteo returns hourly entries on the hour boundary, so dropping the
// "00" minutes keeps each column to two or three characters and prevents the
// 5-column row from collapsing.
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
