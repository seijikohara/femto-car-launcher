package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.testfixtures.FixedDashboardClock
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The dashboard header carries the clock AND today's date on one band, so the
 * date no longer depends on the calendar card (or on calendar permission):
 * both come from the injected clock alone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class DashboardHeaderTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `shows the time and today's date from the clock alone`() {
        rule.setContent {
            FemtoTheme {
                DashboardHeader(is24Hour = true, showSeconds = false, clock = FixedDashboardClock, locale = Locale.US)
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
                DashboardHeader(is24Hour = true, showSeconds = true, clock = FixedDashboardClock, locale = Locale.US)
            }
        }

        rule.onNodeWithText("10:08:00").assertIsDisplayed()
    }

    @Test
    fun `folds the whole date away rather than ellipsizing it on a narrow band`() {
        // 250 dp holds the seconds-bearing time (~150 dp of ink) but not the date
        // beside it even at the weekday / month floor sizes: the date folds away
        // whole — no "..." beside a "...", and no lone day numeral either.
        rule.setContent {
            FemtoTheme {
                DashboardHeader(
                    modifier = Modifier.width(250.dp),
                    is24Hour = true,
                    showSeconds = true,
                    clock = FixedDashboardClock,
                    locale = Locale.US,
                )
            }
        }

        rule.onNodeWithText("10:08:00").assertIsDisplayed()
        rule.onNodeWithText("1").assertDoesNotExist()
        rule.onNodeWithText("Friday").assertDoesNotExist()
        rule.onNodeWithText("MAY 2026").assertDoesNotExist()
    }
}
