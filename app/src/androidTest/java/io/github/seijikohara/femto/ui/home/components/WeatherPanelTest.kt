package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.text.util.LocalePreferences
import io.github.seijikohara.femto.data.HourlyForecast
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class WeatherPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_celsius_when_unit_is_celsius() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot = fakeWeatherSnapshot(tempC = 18.0),
                    unit = LocalePreferences.TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        rule.onNodeWithText("18°C").assertIsDisplayed()
    }

    @Test
    fun renders_fahrenheit_when_unit_is_fahrenheit() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot = fakeWeatherSnapshot(tempC = 0.0),
                    unit = LocalePreferences.TemperatureUnit.FAHRENHEIT,
                    speedUnit = SpeedUnit.MILES_PER_HOUR,
                    is24Hour = false,
                )
            }
        }
        rule.onNodeWithText("32°F").assertIsDisplayed()
    }

    @Test
    fun renders_placeholder_when_snapshot_is_null() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot = null,
                    unit = LocalePreferences.TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        rule.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun renders_condition_and_secondary_line_with_feels_wind_and_uv() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot =
                        fakeWeatherSnapshot(
                            apparentTempC = 16.0,
                            windKmh = 16.0,
                            uvIndex = 5.0,
                            code = WeatherCode.PARTLY_CLOUDY,
                        ),
                    unit = LocalePreferences.TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.MILES_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        rule.onNodeWithText("Partly cloudy").assertIsDisplayed()
        // 16 km/h ≈ 10 mph; all three secondary metrics share one bodyMedium line.
        rule.onNodeWithText("Feels 16°C", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Wind 10 mph", substring = true).assertIsDisplayed()
        rule.onNodeWithText("UV 5", substring = true).assertIsDisplayed()
    }

    @Test
    fun renders_hourly_outlook_with_now_label_and_per_hour_temperatures() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot =
                        fakeWeatherSnapshot(
                            hourly =
                                listOf(
                                    HourlyForecast(LocalTime.of(9, 0), 21.0, WeatherCode.CLEAR),
                                    HourlyForecast(LocalTime.of(10, 0), 22.0, WeatherCode.PARTLY_CLOUDY),
                                ),
                        ),
                    unit = LocalePreferences.TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        rule.onNodeWithText("Now").assertIsDisplayed()
        rule.onNodeWithText("10").assertIsDisplayed()
        rule.onNodeWithText("21°").assertIsDisplayed()
        rule.onNodeWithText("22°").assertIsDisplayed()
    }
}
