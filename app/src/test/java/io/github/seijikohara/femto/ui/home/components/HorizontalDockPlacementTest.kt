package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.display.DockWidth
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Where a horizontal dock sits beside a landscape card column, on the recorded
 * geometries. Widths are the bar's: the viewport less the float margins
 * (12 dp compact, 24 dp otherwise), and for the strip also less the card column
 * (40 % of the width, clamped to 260..350 dp) and one card gap — the same
 * arithmetic DashboardContent feeds in.
 */
class HorizontalDockPlacementTest {
    private fun placement(
        fullBarWidth: Int,
        stripBarWidth: Int,
        statusCount: Int = 4,
        dockWidth: DockWidth = DockWidth.COMPACT,
    ) = horizontalDockPlacement(
        dockWidth = dockWidth,
        fullBarWidth = fullBarWidth.dp,
        stripBarWidth = stripBarWidth.dp,
        navCount = 7,
        statusCount = statusCount,
    )

    @Test
    fun `the 5x3 head unit keeps the full-width bar and its status cluster`() {
        // 853 dp: strip = 853 - 341 - 12 - 24; neither the pill nor the bar's
        // minimum fits, so the bar spans the width under the column.
        assertEquals(HorizontalDockPlacement.FULL_WIDTH, placement(fullBarWidth = 829, stripBarWidth = 476))
    }

    @Test
    fun `the 1024 dp budget panel takes the full-width bar rather than a straddling pill`() {
        // The pill (912 dp) fits the 976 dp bar but not the 602 dp strip, and the
        // bar's minimum (736 dp) does not fit the strip either.
        assertEquals(HorizontalDockPlacement.FULL_WIDTH, placement(fullBarWidth = 976, stripBarWidth = 602))
    }

    @Test
    fun `the 16x9 mainstream panel fills the strip with the bar`() {
        // The pill misses the 858 dp strip by a hair (912 dp); the bar fits it at
        // the tap-target floor with its status cluster.
        assertEquals(HorizontalDockPlacement.BAR_IN_STRIP, placement(fullBarWidth = 1232, stripBarWidth = 858))
    }

    @Test
    fun `the ultrawide panel centres the pill in the strip`() {
        assertEquals(HorizontalDockPlacement.PILL_IN_STRIP, placement(fullBarWidth = 1872, stripBarWidth = 1498))
    }

    @Test
    fun `with every status indicator hidden the bar fits a strip the pill misses`() {
        // 1024 dp with no status side: the pill (672 dp) still misses the 602 dp
        // strip, but the bar's minimum without the status reserve (496 dp) fits.
        assertEquals(
            HorizontalDockPlacement.BAR_IN_STRIP,
            placement(fullBarWidth = 976, stripBarWidth = 602, statusCount = 0),
        )
    }

    @Test
    fun `an extended dock preference never yields the pill`() {
        assertEquals(
            HorizontalDockPlacement.BAR_IN_STRIP,
            placement(fullBarWidth = 1872, stripBarWidth = 1498, dockWidth = DockWidth.EXTENDED),
        )
    }
}
