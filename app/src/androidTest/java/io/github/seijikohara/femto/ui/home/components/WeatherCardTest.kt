package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
                    onOpen = {},
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
                    onOpen = {},
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
                    onOpen = {},
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
                    onOpen = {},
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
                    onOpen = {},
                )
            }
        }
        // The fixture's first hourly entry is 12:00, rendered in the 24-hour
        // clock notation so the 12/24-hour setting is visibly honoured.
        rule.onNodeWithText("12:00").assertIsDisplayed()
    }

    @Test
    fun renders_forecast_hours_in_12_hour_notation_when_configured() {
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = false,
                    onOpen = {},
                )
            }
        }
        // The same 12:00 entry switches to the meridiem notation when the
        // clock setting is 12-hour. The expected text is built with the same
        // pattern because the meridiem word is locale-dependent ("PM" / "午後").
        val expected = LocalTime.of(12, 0).format(DateTimeFormatter.ofPattern("h a"))
        rule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun tapping_the_card_dispatches_open() {
        var opened = false
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                    onOpen = { opened = true },
                )
            }
        }
        rule.onRoot().performClick()
        assert(opened) { "expected the card tap to dispatch onOpen" }
    }
}
