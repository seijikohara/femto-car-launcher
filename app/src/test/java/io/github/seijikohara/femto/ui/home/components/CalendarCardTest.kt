package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
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
 * The calendar card's agenda: today is the card's head (eyebrow, the first
 * event's time as the hero, its title), never a gutter row — the date itself
 * is the dashboard header's — while the coming days keep their date gutter.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class CalendarCardTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setCard(snapshot: CalendarSnapshot) =
        rule.setContent {
            FemtoTheme {
                // The head-unit card's footprint: tall enough for the head and the
                // rows the assertions look for.
                CalendarCard(
                    snapshot = snapshot,
                    is24Hour = true,
                    onExpand = {},
                    modifier = Modifier.size(159.dp, 300.dp),
                )
            }
        }

    @Test
    fun `today opens the card as a head with its first event as the hero`() {
        setCard(fakeCalendarSnapshot())

        rule.onNodeWithText("TODAY").assertIsDisplayed()
        rule.onNodeWithText("10:30").assertIsDisplayed()
        rule.onNodeWithText("Team standup").assertIsDisplayed()
        // Today's further events follow in the agenda's row form.
        rule.onNodeWithText("14:00").assertIsDisplayed()
        rule.onNodeWithText("Pick up kids").assertIsDisplayed()
        // No "FRI 1" gutter for today: the header above the cluster carries the date.
        rule.onNodeWithText("FRI").assertDoesNotExist()
    }

    @Test
    fun `coming days keep their date gutter`() {
        setCard(fakeCalendarSnapshot())

        rule.onNodeWithText("SUN").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
        rule.onNodeWithText("Brunch").assertIsDisplayed()
    }

    @Test
    fun `a free today keeps the eyebrow over the no-events line`() {
        setCard(
            fakeCalendarSnapshot(
                days =
                    listOf(
                        DayCell(LocalDate.of(2026, 5, 1), "Fri", emptyList()),
                        DayCell(LocalDate.of(2026, 5, 3), "Sun", listOf(EventItem(LocalTime.of(9, 0), "Brunch"))),
                    ),
            ),
        )

        rule.onNodeWithText("TODAY").assertIsDisplayed()
        rule.onNodeWithText("No events").assertIsDisplayed()
        rule.onNodeWithText("Brunch").assertIsDisplayed()
    }

    @Test
    fun `an all-day first event takes the hero slot with its label`() {
        setCard(
            fakeCalendarSnapshot(
                days = listOf(DayCell(LocalDate.of(2026, 5, 1), "Fri", listOf(EventItem(null, "Holiday")))),
            ),
        )

        rule.onNodeWithText("All day").assertIsDisplayed()
        rule.onNodeWithText("Holiday").assertIsDisplayed()
    }
}
