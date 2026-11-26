package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The one decision the dashboard draws the turn card and the video window from ([turnCardAndWindow]), on sizes and
 * slots like the 853x512 head unit's: what shows before the first measure, where both fit, where they do not, and where
 * no card fits at all.
 */
class TurnCardAndWindowTest {
    // After a recreation with a route on, the clock and the speed readout are not measured in the first frame. A
    // window drawn then would attach its picture and go one frame later, when the card arrives: it waits instead.
    @Test
    fun `before the first measure a turn card keeps the window back and shows nothing itself`() {
        assertEquals(
            TurnCardAndWindow(card = null, windowShown = false),
            turnCardAndWindow(sizes = SIZES, slots = null, window = WINDOW_BELOW_CLOCK, gap = GAP),
        )
    }

    @Test
    fun `without a turn card the window shows, measured or not`() {
        assertEquals(
            listOf(true, true),
            listOf(null, SLOTS).map { slots ->
                turnCardAndWindow(sizes = null, slots = slots, window = WINDOW_BELOW_CLOCK, gap = GAP).windowShown
            },
        )
    }

    // The window below the clock leaves the top row 65 dp above it: the compact card with the turn after next.
    @Test
    fun `where both fit both show, the card in the top row above the window`() {
        assertEquals(
            TurnCardAndWindow(
                card = TurnCardPlace(SLOTS.topRow.copy(maxHeight = 65.dp), TurnCardForm.COMPACT_THEN),
                windowShown = true,
            ),
            turnCardAndWindow(sizes = SIZES, slots = SLOTS, window = WINDOW_BELOW_CLOCK, gap = GAP),
        )
    }

    // A window in the top row reaches below the clock: the card takes its place as if the window were not there.
    @Test
    fun `where both do not fit the card shows as without the window and the window does not`() {
        assertEquals(
            TurnCardAndWindow(card = TurnCardPlace(SLOTS.belowClock, TurnCardForm.FULL), windowShown = false),
            turnCardAndWindow(sizes = SIZES, slots = SLOTS, window = WINDOW_IN_TOP_ROW, gap = GAP),
        )
    }

    @Test
    fun `where no card fits even without the window the window shows`() {
        val cramped =
            RouteCardSlots(
                topRow = placement(top = 12.dp, width = 150.dp, maxHeight = 40.dp),
                belowClock = placement(top = 89.dp, width = 150.dp, maxHeight = 40.dp),
                topRowRoom = 150.dp,
            )

        assertEquals(
            TurnCardAndWindow(card = null, windowShown = true),
            turnCardAndWindow(sizes = SIZES, slots = cramped, window = WINDOW_IN_TOP_ROW, gap = GAP),
        )
    }

    private companion object {
        val GAP = 12.dp

        // The full card needs 300 dp by 88, the compact one 190 by 56, or 230 by 64 with the turn after next.
        val SIZES =
            TurnCardSizes(
                hasThen = true,
                fullWidth = 300.dp,
                fullHeight = 88.dp,
                compactThenWidth = 230.dp,
                compactThenHeight = 64.dp,
                compactWidth = 190.dp,
                compactHeight = 56.dp,
            )

        // The top row beside the clock, 231 dp wide, and below the clock, 420 dp wide.
        val SLOTS =
            RouteCardSlots(
                topRow = placement(top = 12.dp, width = 231.dp, maxHeight = 300.dp),
                belowClock = placement(top = 89.dp, width = 420.dp, maxHeight = 200.dp),
                topRowRoom = 231.dp,
            )

        // The window's top and bottom edges, below the clock and in the top row.
        val WINDOW_BELOW_CLOCK = 89.dp to 224.dp
        val WINDOW_IN_TOP_ROW = 12.dp to 182.dp

        fun placement(
            top: Dp,
            width: Dp,
            maxHeight: Dp,
        ) = RouteCardPlacement(start = 72.dp, top = top, width = width, maxHeight = maxHeight)
    }
}
