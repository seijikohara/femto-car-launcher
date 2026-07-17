package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeLocation
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SpeedOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_em_dash_for_live_speed_when_no_fix() {
        rule.setContent {
            FemtoTheme {
                SpeedOverlay(
                    location = null,
                    address = fakeAddress(),
                    tripState = fakeTripState(currentSpeedMs = 18.0),
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                    onReset = {},
                )
            }
        }
        // With no fix the hero cell shows the em-dash placeholder instead of "0",
        // so a denied/missing location reads as "unknown", not "standstill".
        rule.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun renders_metric_distance_and_speed_labels_for_metric_unit() {
        rule.setContent {
            FemtoTheme {
                SpeedOverlay(
                    location = fakeLocation(),
                    address = fakeAddress(),
                    tripState = fakeTripState(currentSpeedMs = 18.0),
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                    onReset = {},
                )
            }
        }
        // Assert the unit labels rather than the rounded numerals so the test
        // stays robust against EMA smoothing and rounding. The metric set is
        // "km/h" on the hero + avg cells and "km" on the distance cell, with no
        // imperial "mi" / "mph" labels present.
        rule.onAllNodesWithText("km/h", substring = true).assertCountEquals(2)
        rule.onAllNodesWithText("mi", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("mph", substring = true).assertCountEquals(0)
    }

    @Test
    fun renders_imperial_distance_and_speed_labels_for_imperial_unit() {
        rule.setContent {
            FemtoTheme {
                SpeedOverlay(
                    location = fakeLocation(),
                    address = fakeAddress(),
                    tripState = fakeTripState(currentSpeedMs = 18.0),
                    speedUnit = SpeedUnit.MILES_PER_HOUR,
                    is24Hour = true,
                    onReset = {},
                )
            }
        }
        // The imperial set is "mph" on the hero + avg cells and "mi" on the
        // distance cell, with no metric "km" label present. "mi" matches only
        // the distance cell ("mph" does not contain "mi").
        rule.onAllNodesWithText("mph", substring = true).assertCountEquals(2)
        rule.onAllNodesWithText("mi", substring = true).assertCountEquals(1)
        rule.onAllNodesWithText("km", substring = true).assertCountEquals(0)
    }

    @Test
    fun reset_button_invokes_on_reset_callback() {
        var resetCount = 0
        rule.setContent {
            FemtoTheme {
                SpeedOverlay(
                    location = fakeLocation(),
                    address = fakeAddress(),
                    tripState = fakeTripState(currentSpeedMs = 18.0),
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    is24Hour = true,
                    onReset = { resetCount++ },
                )
            }
        }
        // The trip-reset control is labelled by its content description (see
        // R.string.speed_reset_trip) and tapping it raises the reset callback.
        rule.onNodeWithContentDescription("Reset trip").performClick()
        assertEquals(1, resetCount)
    }
}
