package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun renders_upcoming_event_title_for_granted_snapshot() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot())
            }
        }
        // The fixture's first event title appears in the events section.
        rule.onNodeWithText("Team standup").assertIsDisplayed()
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
