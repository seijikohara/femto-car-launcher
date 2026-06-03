package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

class WeatherCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_rounded_temperature_for_celsius() {
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(tempC = 18.0),
                    city = "Shibuya",
                    temperatureUnit = TemperatureUnit.CELSIUS,
                )
            }
        }
        // The big temperature label is the rounded integer with no unit suffix.
        rule.onNodeWithText("18").assertIsDisplayed()
    }

    @Test
    fun renders_placeholder_when_snapshot_is_null() {
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = null,
                    city = "Shibuya",
                    temperatureUnit = TemperatureUnit.CELSIUS,
                )
            }
        }
        rule.onNodeWithText("Weather unavailable").assertIsDisplayed()
    }

    @Test
    fun renders_condition_label_for_partly_cloudy() {
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(code = WeatherCode.PARTLY_CLOUDY),
                    city = "Shibuya",
                    temperatureUnit = TemperatureUnit.CELSIUS,
                )
            }
        }
        // The condition label is uppercased for layout, so match case-insensitively.
        rule.onNodeWithText("Partly cloudy", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun renders_metric_labels() {
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(),
                    city = "Shibuya",
                    temperatureUnit = TemperatureUnit.CELSIUS,
                )
            }
        }
        rule.onNodeWithText("FEELS").assertIsDisplayed()
        rule.onNodeWithText("WIND").assertIsDisplayed()
        rule.onNodeWithText("HUMID.").assertIsDisplayed()
    }

    @Test
    fun renders_hourly_forecast_chip() {
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(),
                    city = "Shibuya",
                    temperatureUnit = TemperatureUnit.CELSIUS,
                )
            }
        }
        // The fixture's first hourly entry is 12:00, rendered via "%02dh".
        rule.onNodeWithText("12h").assertIsDisplayed()
    }
}
