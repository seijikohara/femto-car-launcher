package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class CalendarPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_events_with_titles_and_location() {
        rule.setContent {
            FemtoTheme {
                CalendarPanel(
                    snapshot = fakeCalendarSnapshot(),
                    is24Hour = true,
                    onOpenExternal = {},
                    onClose = {},
                )
            }
        }
        rule.onNodeWithText("Team standup").assertIsDisplayed()
        rule.onNodeWithText("Room 4", substring = true).assertIsDisplayed()
    }

    @Test
    fun collapse_button_invokes_onClose() {
        var closed = false
        rule.setContent {
            FemtoTheme {
                CalendarPanel(
                    snapshot = fakeCalendarSnapshot(),
                    is24Hour = true,
                    onOpenExternal = {},
                    onClose = { closed = true },
                )
            }
        }
        rule.onNodeWithContentDescription("Collapse").performClick()
        assertTrue(closed)
    }

    @Test
    fun open_external_button_invokes_callback() {
        var opened = false
        rule.setContent {
            FemtoTheme {
                CalendarPanel(
                    snapshot = fakeCalendarSnapshot(),
                    is24Hour = true,
                    onOpenExternal = { opened = true },
                    onClose = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Open calendar").performClick()
        assertTrue(opened)
    }
}
