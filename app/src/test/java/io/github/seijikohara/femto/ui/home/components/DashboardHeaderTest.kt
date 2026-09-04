package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * The dashboard header carries the clock AND today's date on one band, so the
 * date no longer depends on the calendar card (or on calendar permission):
 * both come from the injected clock alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class DashboardHeaderTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `shows the time and today's date from the clock alone`() {
        rule.setContent {
            FemtoTheme {
                DashboardHeader(is24Hour = true, showSeconds = false, clock = FixedClock, locale = Locale.US)
            }
        }

        rule.onNodeWithText("10:08").assertIsDisplayed()
        rule.onNodeWithText("1").assertIsDisplayed()
        rule.onNodeWithText("Friday").assertIsDisplayed()
        rule.onNodeWithText("MAY 2026").assertIsDisplayed()
    }

    @Test
    fun `shows seconds only when asked`() {
        rule.setContent {
            FemtoTheme {
                DashboardHeader(is24Hour = true, showSeconds = true, clock = FixedClock, locale = Locale.US)
            }
        }

        rule.onNodeWithText("10:08:00").assertIsDisplayed()
    }

    private companion object {
        // Friday 1 May 2026, 10:08 UTC — the dashboard goldens' fixed instant.
        val FixedClock: Clock = Clock.fixed(Instant.parse("2026-05-01T10:08:00Z"), ZoneOffset.UTC)
    }
}
