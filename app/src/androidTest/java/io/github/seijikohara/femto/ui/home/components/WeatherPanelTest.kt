package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.text.util.LocalePreferences
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

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
                )
            }
        }
        rule.onNodeWithText("18", substring = true).assertIsDisplayed()
        rule.onNodeWithText("°C", substring = true).assertIsDisplayed()
    }

    @Test
    fun renders_fahrenheit_when_unit_is_fahrenheit() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(
                    snapshot = fakeWeatherSnapshot(tempC = 0.0),
                    unit = LocalePreferences.TemperatureUnit.FAHRENHEIT,
                )
            }
        }
        rule.onNodeWithText("32", substring = true).assertIsDisplayed()
        rule.onNodeWithText("°F", substring = true).assertIsDisplayed()
    }

    @Test
    fun renders_placeholder_when_snapshot_is_null() {
        rule.setContent {
            FemtoTheme {
                WeatherPanel(snapshot = null, unit = LocalePreferences.TemperatureUnit.CELSIUS)
            }
        }
        rule.onNodeWithText("—").assertIsDisplayed()
    }
}
