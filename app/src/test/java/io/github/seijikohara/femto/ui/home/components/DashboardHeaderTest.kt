package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.testfixtures.FixedDashboardClock
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.clockHero
import io.github.seijikohara.femto.ui.theme.dateLine
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
 * both come from the injected clock alone. The date is one localized line that
 * takes a shorter wording as the band narrows and folds away whole at the end.
 *
 * NATIVE graphics: the wording choice is measured through the real text
 * engine, and the legacy mode measures every glyph as one pixel, so nothing
 * would ever fold.
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
        rule.onNodeWithText("Friday, May 1").assertIsDisplayed()
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
    fun `takes the short wording when the full date does not fit beside the time`() {
        // The band is sized from the text engine's own widths so the case is exact
        // whatever face the test JVM renders with: room for "Fri, May 1" at its
        // design size but not for "Friday, May 1" — halfway between the two.
        rule.setContent {
            FemtoTheme {
                val typography = MaterialTheme.typography
                val timeStyle = typography.clockHero()
                val dateStyle = typography.dateLine()
                val measurer = rememberTextMeasurer()
                val timePx = measurer.measure("10:08:00", timeStyle).size.width
                val fullPx = measurer.measure("Friday, May 1", dateStyle).size.width
                val shortPx = measurer.measure("Fri, May 1", dateStyle).size.width
                val bandWidth =
                    with(LocalDensity.current) {
                        (timePx + (fullPx + shortPx) / 2).toDp() + HeaderGap + FemtoDimens.OverlayPaddingHorizontal * 2
                    }
                DashboardHeader(
                    modifier = Modifier.width(bandWidth),
                    is24Hour = true,
                    showSeconds = true,
                    clock = FixedDashboardClock,
                    locale = Locale.US,
                )
            }
        }

        rule.onNodeWithText("10:08:00").assertIsDisplayed()
        rule.onNodeWithText("Fri, May 1").assertIsDisplayed()
        rule.onNodeWithText("Friday, May 1").assertDoesNotExist()
    }

    @Test
    fun `keeps the weekday alone when no dated wording fits`() {
        // 250 dp holds the seconds-bearing time (~150 dp of ink) and little more:
        // neither dated wording fits even at the floor size, but the weekday does.
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
        rule.onNodeWithText("Friday").assertIsDisplayed()
        rule.onNodeWithText("Fri, May 1").assertDoesNotExist()
        rule.onNodeWithText("Friday, May 1").assertDoesNotExist()
    }

    @Test
    fun `folds the whole date away rather than ellipsizing it on a band the time fills`() {
        // 200 dp is the time alone: the date folds away whole — no "Fri…" beside
        // the time, and nothing left in the tree for TalkBack to read.
        rule.setContent {
            FemtoTheme {
                DashboardHeader(
                    modifier = Modifier.width(200.dp),
                    is24Hour = true,
                    showSeconds = true,
                    clock = FixedDashboardClock,
                    locale = Locale.US,
                )
            }
        }

        rule.onNodeWithText("10:08:00").assertIsDisplayed()
        rule.onNodeWithText("Friday").assertDoesNotExist()
        rule.onNodeWithText("Fri, May 1").assertDoesNotExist()
        rule.onNodeWithText("Friday, May 1").assertDoesNotExist()
    }
}
