package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class WeatherPanelTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setPanel(
        onOpenExternal: () -> Unit = {},
        onClose: () -> Unit = {},
    ) = rule.setContent {
        FemtoTheme {
            WeatherPanel(
                snapshot = fakeWeatherSnapshot(),
                temperatureUnit = TemperatureUnit.CELSIUS,
                speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                is24Hour = true,
                onOpenExternal = onOpenExternal,
                onClose = onClose,
            )
        }
    }

    @Test
    fun renders_humidity_tile() {
        setPanel()
        // assertExists (not assertIsDisplayed): the tile grid sits below the
        // temperature curve in a scrolling column, so on a short test window
        // the node is composed but off-screen.
        rule.onNodeWithText("58%").assertExists()
    }

    @Test
    fun collapse_button_invokes_onClose() {
        var closed = false
        setPanel(onClose = { closed = true })
        rule.onNodeWithContentDescription("Collapse").performClick()
        assertTrue(closed)
    }

    @Test
    fun open_external_button_invokes_callback() {
        var opened = false
        setPanel(onOpenExternal = { opened = true })
        rule.onNodeWithContentDescription("Open weather").performClick()
        assertTrue(opened)
    }
}
