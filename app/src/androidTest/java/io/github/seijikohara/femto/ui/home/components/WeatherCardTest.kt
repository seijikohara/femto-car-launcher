package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.assertTrue

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
                    onExpand = {},
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
                    onExpand = {},
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
                    onExpand = {},
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
                    onExpand = {},
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
                    onExpand = {},
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
                    onExpand = {},
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
    fun shows_as_of_caption_when_data_is_stale() {
        // Aged against the card's own clock, not the wall clock: two hours past
        // the fetch is over the staleness threshold, so the eyebrow turns into
        // the fetch time, printed in that clock's zone.
        val fetchedAt = Instant.parse("2026-05-01T05:32:00Z")
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(fetchedAt = fetchedAt),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                    onExpand = {},
                    clock = Clock.fixed(fetchedAt.plus(Duration.ofHours(2)), ZoneOffset.UTC),
                )
            }
        }
        rule.onNodeWithText("as of 05:32", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun hides_as_of_caption_when_data_is_fresh() {
        val fetchedAt = Instant.parse("2026-05-01T05:32:00Z")
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(fetchedAt = fetchedAt),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                    onExpand = {},
                    clock = Clock.fixed(fetchedAt, ZoneOffset.UTC),
                )
            }
        }
        rule.onNodeWithText("as of ", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun card_tap_invokes_onExpand() {
        var expanded = false
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                    onExpand = { expanded = true },
                )
            }
        }
        rule.onNodeWithContentDescription("Open full-screen weather").performClick()
        assertTrue(expanded)
    }

    @Test
    fun tapping_the_forecast_also_invokes_onExpand() {
        // The maximize tap sits on the whole populated card, not just the head,
        // so a tap on a forecast chip (which carries no click of its own) still
        // opens the full-screen panel.
        var expanded = false
        rule.setContent {
            FemtoTheme {
                WeatherCard(
                    snapshot = fakeWeatherSnapshot(),
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                    onExpand = { expanded = true },
                )
            }
        }
        rule.onNodeWithText("12:00").performClick()
        assertTrue(expanded)
    }
}
