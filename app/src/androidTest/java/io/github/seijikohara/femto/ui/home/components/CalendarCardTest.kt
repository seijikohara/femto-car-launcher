package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

class CalendarCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_selected_day_event_title_for_granted_snapshot() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot())
            }
        }
        // Selection defaults to today (the first strip cell), so today's first
        // event title appears in the events section.
        rule.onNodeWithText("Team standup").assertIsDisplayed()
    }

    @Test
    fun selecting_a_day_shows_that_days_events_and_keeps_the_head_on_today() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot())
            }
        }
        // Strip cells expose their ISO date as the content description.
        rule.onNodeWithContentDescription("2026-05-03").performClick()

        // The events section switches to the selected day; today's events leave.
        rule.onNodeWithText("Brunch").assertIsDisplayed()
        rule.onAllNodesWithText("Team standup").assertCountEquals(0)
        // The hero head stays on today regardless of the selected day.
        rule.onNodeWithText("Friday").assertIsDisplayed()
    }

    @Test
    fun selecting_a_day_without_events_shows_the_empty_message() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot())
            }
        }
        rule.onNodeWithContentDescription("2026-05-02").performClick()

        val noEvents =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.calendar_no_events)
        rule.onNodeWithText(noEvents).assertIsDisplayed()
    }

    @Test
    fun shows_today_number_for_granted_snapshot() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot())
            }
        }
        // The hero head renders the day-of-month of the fixture's `today`
        // (2026-05-01); the strip also leads with the same "1" cell. Compose has
        // no assertCountIsAtLeast, so fetch the matching nodes and assert the
        // day-of-month rendered at least once.
        val dayNodes = rule.onAllNodesWithText("1").fetchSemanticsNodes()
        assert(dayNodes.isNotEmpty()) { "expected the day-of-month '1' to render at least once" }
    }

    @Test
    fun shows_permission_denied_message_when_access_is_denied() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(hasCalendarAccess = false))
            }
        }
        // Resolve the copy from resources so the literal stays the SSOT in
        // strings.xml ("Calendar access not granted").
        val denied =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.calendar_permission_denied)
        rule.onNodeWithText(denied).assertIsDisplayed()
    }
}
