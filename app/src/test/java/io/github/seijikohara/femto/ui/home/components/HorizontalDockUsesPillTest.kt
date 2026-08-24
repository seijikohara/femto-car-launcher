package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DockWidth
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [horizontalDockUsesPill] is the horizontal bar's layout choice — the centred
 * wrap-content pill, or the weight-shared full-width bar. Pure dp geometry, so
 * these run without a composition.
 *
 * Every width below is the BAR's own, the figure `HorizontalDock` measures — a
 * viewport is wider by the dock's float margins, which is what
 * [MapCreditClearsDockTest] converts. The counts come from the enums, so adding
 * a dock button or a status indicator re-derives them; the widths do NOT
 * follow, and are picked to straddle the two thresholds today's seven buttons
 * put at 672 dp and 912 dp. An eighth button moves both, and the 690 dp case
 * below would have to be re-picked with them.
 */
class HorizontalDockUsesPillTest {
    private val navCount = DockNavId.entries.size
    private val statusCount = DockStatusId.entries.size

    @Test
    fun `COMPACT renders the centred pill on a dock wide enough for it`() =
        assertTrue(horizontalDockUsesPill(DockWidth.COMPACT, 1280.dp, navCount, statusCount))

    // The direction that is not merely a preference: the pill is a fixed
    // footprint, so forcing it onto a narrow bar clips the leading / trailing
    // buttons below the automotive floor (AGENTS.md#automotive-overrides). COMPACT
    // therefore means "pill where it fits", never "pill always".
    @Test
    fun `COMPACT still falls back to the extended bar where the pill would clip`() =
        assertFalse(horizontalDockUsesPill(DockWidth.COMPACT, 853.dp, navCount, statusCount))

    @Test
    fun `EXTENDED takes the weight-shared bar even where the pill would fit`() =
        assertFalse(horizontalDockUsesPill(DockWidth.EXTENDED, 1280.dp, navCount, statusCount))

    // Hiding every status indicator removes the cluster from the bar, so the
    // width it reserved goes with it — otherwise the pill test would keep
    // measuring a footprint that is no longer rendered, and the map attribution
    // would stay lifted to clear a bar that is no longer full-width.
    @Test
    fun `hiding the whole status cluster frees the width it reserved`() =
        assertTrue(horizontalDockUsesPill(DockWidth.COMPACT, 853.dp, navCount, statusCount = 0))

    // Two stages yield to a narrowing bar, and the cluster yields first: below
    // the status threshold the reserve is already gone, so the pill fits again.
    @Test
    fun `a dock too narrow to show the status cluster keeps the pill`() =
        assertTrue(horizontalDockUsesPill(DockWidth.COMPACT, 690.dp, navCount, statusCount))
}

/**
 * [mapCreditClearsDock] decides whether the map's bottom-start credit is lifted
 * off the corner to clear the dock. It asks the same [horizontalDockUsesPill]
 * predicate the bar does, but from outside the bar — so it takes the VIEWPORT
 * width plus the margin the bar floats by, and narrows the one to the other
 * before asking. These cases pin that conversion as much as the answer:
 * per-backend map attribution is an invariant in this project, and a credit
 * hidden under a full-width bar breaks it.
 */
class MapCreditClearsDockTest {
    private val navCount = DockNavId.entries.size
    private val statusCount = DockStatusId.entries.size

    @Test
    fun `a centred pill leaves the corner free, so the credit stays flush`() =
        assertFalse(
            mapCreditClearsDock(
                dockPosition = DockPosition.BOTTOM,
                dockWidth = DockWidth.COMPACT,
                viewportWidth = 1280.dp,
                dockMargin = FemtoDimens.ScreenPadding,
                navCount = navCount,
                statusCount = statusCount,
            ),
        )

    // The mirroring the setting exists for: EXTENDED spans the width at a size
    // where the pill would have fitted, so the credit has to lift with it.
    @Test
    fun `choosing EXTENDED lifts the credit even where the pill would have fitted`() =
        assertTrue(
            mapCreditClearsDock(
                dockPosition = DockPosition.BOTTOM,
                dockWidth = DockWidth.EXTENDED,
                viewportWidth = 1000.dp,
                dockMargin = FemtoDimens.ScreenPadding,
                navCount = navCount,
                statusCount = statusCount,
            ),
        )

    // TOP is the other horizontal bar, and the one a layout-only check would get
    // wrong: it spans the width too, but nowhere near the bottom-start corner.
    @Test
    fun `a top-hosted bar never covers the corner`() =
        assertFalse(
            mapCreditClearsDock(
                dockPosition = DockPosition.TOP,
                dockWidth = DockWidth.EXTENDED,
                viewportWidth = 1280.dp,
                dockMargin = FemtoDimens.ScreenPadding,
                navCount = navCount,
                statusCount = statusCount,
            ),
        )

    // Past the content cap the extended bar stops growing and centres, so the
    // bottom-start corner is map again and the credit belongs flush in it.
    // Before the cap existed this width kept the credit lifted over dead glass.
    @Test
    fun `a capped extended bar on an ultrawide leaves the corner to the credit`() =
        assertFalse(
            mapCreditClearsDock(
                dockPosition = DockPosition.BOTTOM,
                dockWidth = DockWidth.EXTENDED,
                viewportWidth = 1920.dp,
                dockMargin = FemtoDimens.ScreenPadding,
                navCount = navCount,
                statusCount = statusCount,
            ),
        )

    // The band where the viewport and the bar disagree, and the one that decides
    // whether this function may take a raw viewport width. A 940 dp viewport
    // leaves the bar 892 dp once both float margins come off — short of the 912 dp
    // the pill needs while the status cluster shows — so the bar draws full-width
    // and the credit must lift. Measuring the viewport instead reads 940, calls it
    // a pill, and leaves the OSM credit under the bar.
    @Test
    fun `the credit measures the bar, not the viewport it floats in`() =
        assertTrue(
            mapCreditClearsDock(
                dockPosition = DockPosition.BOTTOM,
                dockWidth = DockWidth.COMPACT,
                viewportWidth = 940.dp,
                dockMargin = FemtoDimens.ScreenPadding,
                navCount = navCount,
                statusCount = statusCount,
            ),
        )
}
