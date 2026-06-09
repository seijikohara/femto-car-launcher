package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
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
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
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
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        // The empty state is now an icon-only placeholder; the unavailable
        // string is its contentDescription so TalkBack still announces it.
        rule.onNodeWithContentDescription("Weather unavailable").assertIsDisplayed()
    }

    @Test
    fun renders_condition_glyph_with_description_for_partly_cloudy() {
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(code = WeatherCode.PARTLY_CLOUDY),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        // The condition is now conveyed by the hero glyph; its label survives as the
        // icon's content description for TalkBack.
        rule.onNodeWithContentDescription("Partly cloudy", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun renders_metric_labels() {
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        // Each metric is now a Lucide glyph; its text label survives as the icon's
        // content description for TalkBack.
        rule.onNodeWithContentDescription("FEELS").assertIsDisplayed()
        rule.onNodeWithContentDescription("WIND").assertIsDisplayed()
        rule.onNodeWithContentDescription("HUMID.").assertIsDisplayed()
    }

    @Test
    fun renders_hourly_forecast_chip() {
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                )
            }
        }
        // The fixture's first hourly entry is 12:00, rendered via "%02dh".
        rule.onNodeWithText("12h").assertIsDisplayed()
    }
}
