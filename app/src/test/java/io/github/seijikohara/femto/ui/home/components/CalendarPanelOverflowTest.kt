package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.github.seijikohara.femto.data.calendar.CalendarRepository.Companion.WINDOW_DAYS
import io.github.seijikohara.femto.data.calendar.DayCell
import io.github.seijikohara.femto.data.calendar.EventItem
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * The maximize panel must render every day it is handed, however far past the
 * viewport that runs — the overflow scrolls.
 *
 * It used to cap the agenda to whatever fit the panel's height, so a busy month
 * lost its trailing days outright: maximizing the compact card made real events
 * disappear. The panel goldens cannot catch that, because their fixture is short
 * enough to fit either way; this test hands the panel far more days than the
 * viewport can hold and asserts the last one still composed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class CalendarPanelOverflowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setPanel() {
        rule.setContent {
            FemtoTheme {
                CalendarPanel(
                    snapshot =
                        fakeCalendarSnapshot(
                            today = TODAY,
                            days =
                                (0 until DAYS).map { offset ->
                                    DayCell(
                                        date = TODAY.plusDays(offset.toLong()),
                                        weekdayLetter = "D",
                                        events = listOf(EventItem(LocalTime.of(9, 0), "Event $offset")),
                                    )
                                },
                        ),
                    is24Hour = true,
                    onOpenExternal = {},
                    onClose = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `scrolls to the last day of an agenda far taller than the panel`() {
        setPanel()

        // Composed *and* reachable: performScrollTo pins the scroll modifier too,
        // so a column that merely emitted every child and then clipped them —
        // the same "events disappear" symptom — still fails here. The
        // height-capped layout this replaced never emitted the day at all.
        rule.onNodeWithText("Event ${DAYS - 1}").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `still shows the nearest day at the top`() {
        setPanel()

        // Anti-vacuous guard: the assertion above would also pass on a panel that
        // rendered nothing but a scroll container.
        rule.onNodeWithText("Event 0").assertIsDisplayed()
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 5, 1)

        // The repository's full window — the agenda the panel now covers, and
        // several times what an 853x512 head-unit panel can show at once.
        const val DAYS = WINDOW_DAYS
    }
}
