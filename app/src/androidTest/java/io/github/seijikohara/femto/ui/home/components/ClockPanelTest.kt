package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ClockPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_24h_time_and_localized_date() {
        rule.setContent {
            FemtoTheme {
                ClockPanel(
                    tick = ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1)),
                    is24Hour = true,
                    sunrise = null,
                    sunset = null,
                )
            }
        }
        rule.onNodeWithText("14:32").assertIsDisplayed()
        rule.onNodeWithText("May 1, 2026", substring = true).assertIsDisplayed()
    }

    @Test
    fun renders_12h_time_when_not_24h() {
        rule.setContent {
            FemtoTheme {
                ClockPanel(
                    tick = ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1)),
                    is24Hour = false,
                    sunrise = null,
                    sunset = null,
                )
            }
        }
        rule.onNodeWithText("2:32", substring = true).assertIsDisplayed()
    }

    @Test
    fun renders_sunrise_and_sunset_chips_when_both_are_present() {
        rule.setContent {
            FemtoTheme {
                ClockPanel(
                    tick = ClockTick(LocalTime.of(7, 39), LocalDate.of(2026, 5, 11)),
                    is24Hour = true,
                    sunrise = LocalTime.of(4, 39),
                    sunset = LocalTime.of(18, 35),
                )
            }
        }
        rule.onNodeWithContentDescription("Sunrise").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sunset").assertIsDisplayed()
        rule.onNodeWithText("04:39").assertIsDisplayed()
        rule.onNodeWithText("18:35").assertIsDisplayed()
    }
}
