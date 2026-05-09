package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.core.text.util.LocalePreferences
import io.github.seijikohara.femto.data.DailyForecast
import io.github.seijikohara.femto.data.HourlyForecast
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
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
    fun renders_condition_apparent_temperature_and_uv_in_hero_secondary() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot =
                        fakeWeatherSnapshot(
                            apparentTempC = 16.0,
                            uvIndex = 5.0,
                            code = WeatherCode.PARTLY_CLOUDY,
                        ),
                    unit = LocalePreferences.TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        rule.onNodeWithText("Partly cloudy", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Feels 16°C", substring = true).assertIsDisplayed()
        rule.onNodeWithText("UV 5", substring = true).assertIsDisplayed()
    }

    @Test
    fun renders_sunrise_sunset_and_wind_chips_with_icons() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot =
                        fakeWeatherSnapshot(
                            windKmh = 16.0,
                            sunrise = LocalTime.of(5, 42),
                            sunset = LocalTime.of(19, 14),
                        ),
                    unit = LocalePreferences.TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.MILES_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        rule.onNodeWithContentDescription("Sunrise").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sunset").assertIsDisplayed()
        rule.onNodeWithContentDescription("Wind").assertIsDisplayed()
        rule.onNodeWithText("05:42").assertIsDisplayed()
        rule.onNodeWithText("19:14").assertIsDisplayed()
        // 16 km/h ≈ 10 mph
        rule.onNodeWithText("10 mph").assertIsDisplayed()
    }

    @Test
    fun renders_5_day_outlook_with_today_label_and_high_low_temperatures() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot =
                        fakeWeatherSnapshot(
                            daily =
                                listOf(
                                    DailyForecast(LocalDate.of(2026, 5, 9), 22.0, 14.0, WeatherCode.CLEAR),
                                    DailyForecast(LocalDate.of(2026, 5, 10), 25.0, 16.0, WeatherCode.CLEAR),
                                ),
                        ),
                    unit = LocalePreferences.TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("22° / 14°").assertIsDisplayed()
        rule.onNodeWithText("25° / 16°").assertIsDisplayed()
    }

    @Test
    fun renders_hourly_strip_entries_with_hour_and_compact_temperature() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot =
                        fakeWeatherSnapshot(
                            hourly =
                                listOf(
                                    HourlyForecast(LocalTime.of(9, 0), 12.0, WeatherCode.CLOUDY),
                                    HourlyForecast(LocalTime.of(10, 0), 14.0, WeatherCode.CLOUDY),
                                ),
                        ),
                    unit = LocalePreferences.TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        rule.onNodeWithText("09").assertIsDisplayed()
        rule.onNodeWithText("10").assertIsDisplayed()
        rule.onNodeWithText("12°").assertIsDisplayed()
        rule.onNodeWithText("14°").assertIsDisplayed()
    }
}
