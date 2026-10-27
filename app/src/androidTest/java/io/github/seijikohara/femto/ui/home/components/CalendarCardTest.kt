package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import kotlin.test.assertTrue

class CalendarCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_each_days_events_in_the_agenda_list() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(), is24Hour = true, onExpand = {})
            }
        }
        // The agenda lists every day, so events from different days all render
        // simultaneously (no per-day selection).
        rule.onNodeWithText("Team standup").assertIsDisplayed()
        rule.onNodeWithText("Brunch").assertIsDisplayed()
    }

    @Test
    fun omits_days_without_events_from_the_agenda() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(), is24Hour = true, onExpand = {})
            }
        }
        // The fixture's 2026-05-02 is a free day (and not today), so its gutter
        // day number must not render — free days no longer occupy agenda rows.
        val freeDayGutters = rule.onAllNodesWithText("2").fetchSemanticsNodes()
        assert(freeDayGutters.isEmpty()) { "expected the free day '2' to be omitted from the agenda" }
    }

    @Test
    fun shows_no_events_line_when_today_is_free() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(
                    snapshot =
                        fakeCalendarSnapshot(
                            days = fakeCalendarSnapshot().days.map { it.copy(events = emptyList()) },
                        ),
                    is24Hour = true,
                    onExpand = {},
                )
            }
        }
        // Today stays in the agenda even with no events, carrying an explicit
        // no-events line instead of silently vanishing.
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
                CalendarCard(snapshot = fakeCalendarSnapshot(), is24Hour = true, onExpand = {})
            }
        }
        // The hero head renders the day-of-month of the fixture's `today`
        // (2026-05-01). Compose has no assertCountIsAtLeast, so fetch the matching
        // nodes and assert the day-of-month rendered at least once.
        val dayNodes = rule.onAllNodesWithText("1").fetchSemanticsNodes()
        assert(dayNodes.isNotEmpty()) { "expected the day-of-month '1' to render at least once" }
    }

    @Test
    fun card_tap_invokes_onExpand() {
        var expanded = false
        rule.setContent {
            FemtoTheme {
                CalendarCard(
                    snapshot = fakeCalendarSnapshot(),
                    is24Hour = true,
                    onExpand = { expanded = true },
                )
            }
        }
        rule.onNodeWithContentDescription("Open full-screen calendar").performClick()
        assertTrue(expanded)
    }

    @Test
    fun tapping_an_agenda_row_also_invokes_onExpand() {
        // The maximize tap sits on the whole populated agenda, not just the head,
        // so a tap on an event row (which carries no click of its own) still opens
        // the full-screen panel.
        var expanded = false
        rule.setContent {
            FemtoTheme {
                CalendarCard(
                    snapshot = fakeCalendarSnapshot(),
                    is24Hour = true,
                    onExpand = { expanded = true },
                )
            }
        }
        rule.onNodeWithText("Team standup").performClick()
        assertTrue(expanded)
    }

    @Test
    fun shows_permission_denied_message_when_access_is_denied() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(hasCalendarAccess = false), is24Hour = true, onExpand = {})
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

    @Test
    fun shows_query_failed_message_when_the_provider_query_failed() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(queryFailed = true), is24Hour = true, onExpand = {})
            }
        }
        // Resolve the copy from resources so the literal stays the SSOT in
        // strings.xml ("Calendar couldn't be read").
        val failed =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.calendar_query_failed)
        rule.onNodeWithText(failed).assertIsDisplayed()
    }

    @Test
    fun date_only_shows_the_head_without_the_agenda() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(snapshot = fakeCalendarSnapshot(), is24Hour = true, onExpand = {}, dateOnly = true)
            }
        }
        // The head's month line ("May 2026", uppercased) renders; no event row does.
        rule.onNodeWithText("MAY 2026").assertIsDisplayed()
        rule.onNodeWithText("Team standup").assertDoesNotExist()
        rule.onNodeWithText("Brunch").assertDoesNotExist()
    }

    @Test
    fun date_only_card_tap_invokes_onExpand() {
        var expanded = false
        rule.setContent {
            FemtoTheme {
                CalendarCard(
                    snapshot = fakeCalendarSnapshot(),
                    is24Hour = true,
                    onExpand = { expanded = true },
                    dateOnly = true,
                )
            }
        }
        rule.onNodeWithContentDescription("Open full-screen calendar").performClick()
        assertTrue(expanded)
    }

    @Test
    fun date_only_shows_the_date_without_calendar_access_and_no_tap() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(
                    snapshot = fakeCalendarSnapshot(hasCalendarAccess = false),
                    is24Hour = true,
                    onExpand = {},
                    dateOnly = true,
                )
            }
        }
        // The date needs no calendar access, so it shows with no denial hint, and
        // the card carries no click action: the panel cannot open without access.
        rule.onNodeWithText("MAY 2026").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.calendar_permission_denied)).assertDoesNotExist()
        rule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun date_only_shows_the_date_after_a_query_failure_and_no_tap() {
        rule.setContent {
            FemtoTheme {
                CalendarCard(
                    snapshot = fakeCalendarSnapshot(queryFailed = true),
                    is24Hour = true,
                    onExpand = {},
                    dateOnly = true,
                )
            }
        }
        rule.onNodeWithText("MAY 2026").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.calendar_query_failed)).assertDoesNotExist()
        rule.onNode(hasClickAction()).assertDoesNotExist()
    }

    private fun string(id: Int): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(id)
}
