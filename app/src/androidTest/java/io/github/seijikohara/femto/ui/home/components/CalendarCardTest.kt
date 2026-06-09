package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
    fun renders_each_days_events_in_the_agenda_list() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(), is24Hour = true)
            }
        }
        // The agenda lists every day, so events from different days all render
        // simultaneously (no per-day selection).
        rule.onNodeWithText("Team standup").assertIsDisplayed()
        rule.onNodeWithText("Brunch").assertIsDisplayed()
    }

    @Test
    fun shows_a_placeholder_for_days_without_events() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(), is24Hour = true)
            }
        }
        // The fixture has free days (e.g. 2026-05-02), each shown with the em-dash
        // placeholder, so at least one renders.
        val placeholders = rule.onAllNodesWithText("—").fetchSemanticsNodes()
        assert(placeholders.isNotEmpty()) { "expected at least one free-day placeholder" }
    }

    @Test
    fun shows_today_number_for_granted_snapshot() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(), is24Hour = true)
            }
        }
        // The hero head renders the day-of-month of the fixture's `today`
        // (2026-05-01). Compose has no assertCountIsAtLeast, so fetch the matching
        // nodes and assert the day-of-month rendered at least once.
        val dayNodes = rule.onAllNodesWithText("1").fetchSemanticsNodes()
        assert(dayNodes.isNotEmpty()) { "expected the day-of-month '1' to render at least once" }
    }

    @Test
    fun shows_permission_denied_message_when_access_is_denied() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(hasCalendarAccess = false), is24Hour = true)
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
