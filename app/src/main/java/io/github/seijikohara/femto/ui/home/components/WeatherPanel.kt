package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.text.util.LocalePreferences
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.data.WeatherSnapshot
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import kotlin.math.roundToInt

@Composable
internal fun WeatherPanel(
    snapshot: WeatherSnapshot?,
    unit: String,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Column(modifier = Modifier.padding(FemtoDimens.GridGutter)) {
        Text(
            text = "WEATHER",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (snapshot == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = iconFor(snapshot.code),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = formatTemperature(snapshot.tempC, unit),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun iconFor(code: WeatherCode): String =
    when (code) {
        WeatherCode.CLEAR -> "☀"
        WeatherCode.PARTLY_CLOUDY -> "⛅"
        WeatherCode.CLOUDY, WeatherCode.FOG -> "☁"
        WeatherCode.DRIZZLE, WeatherCode.RAIN, WeatherCode.RAIN_SHOWERS, WeatherCode.FREEZING_RAIN -> "🌧"
        WeatherCode.SNOW, WeatherCode.SNOW_SHOWERS, WeatherCode.SNOW_GRAINS -> "❄"
        WeatherCode.THUNDERSTORM -> "⚡"
        WeatherCode.UNKNOWN -> "·"
    }

private fun formatTemperature(
    tempC: Double,
    unit: String,
): String =
    when (unit) {
        LocalePreferences.TemperatureUnit.FAHRENHEIT -> "${(tempC * 9 / 5 + 32).roundToInt()}°F"
        LocalePreferences.TemperatureUnit.KELVIN -> "${(tempC + 273.15).roundToInt()}K"
        else -> "${tempC.roundToInt()}°C"
    }
