package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The map control rail reads compass / locate / zoom from the top and yields
 * from the top when its host is short: the compass goes first, then locate,
 * while the zoom pair — the head unit's only zoom affordance — always stays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class MapControlRailTest {
    @get:Rule
    val rule = createComposeRule()

    // mdpi, as the qualifiers above put the composition: one dp is one px.
    private val density = Density(1f)

    private fun setRail(
        budget: Dp,
        showLocate: Boolean = true,
    ) = rule.setContent {
        FemtoTheme {
            // The host bounds the rail's height, as the dashboard does with its
            // reserve above the speed overlay.
            Box(modifier = Modifier.height(budget)) {
                MapControlRail(
                    bearingDeg = { 0f },
                    onCompassTap = {},
                    showLocate = showLocate,
                    following = true,
                    onLocate = {},
                    onZoomIn = {},
                    onZoomOut = {},
                    hazeState = rememberHazeState(),
                    glassConfig = GlassConfig(),
                )
            }
        }
    }

    @Test
    fun `a tall host keeps every segment, compass first`() {
        setRail(budget = 240.dp)

        val tops =
            listOf(COMPASS, LOCATE, ZOOM_IN, ZOOM_OUT).map { label ->
                rule
                    .onNodeWithContentDescription(label)
                    .assertExists()
                    .getBoundsInRoot()
                    .top
            }
        // Reading order top to bottom: compass, locate, zoom in, zoom out.
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun `a short host drops the compass before locate`() {
        setRail(budget = railHeight(3))

        rule.onNodeWithContentDescription(COMPASS).assertDoesNotExist()
        rule.onNodeWithContentDescription(LOCATE).assertExists()
        rule.onNodeWithContentDescription(ZOOM_IN).assertExists()
        rule.onNodeWithContentDescription(ZOOM_OUT).assertExists()
    }

    @Test
    fun `the shortest host keeps only the zoom pair`() {
        setRail(budget = railHeight(3) - 1.dp)

        rule.onNodeWithContentDescription(COMPASS).assertDoesNotExist()
        rule.onNodeWithContentDescription(LOCATE).assertDoesNotExist()
        rule.onNodeWithContentDescription(ZOOM_IN).assertExists()
        rule.onNodeWithContentDescription(ZOOM_OUT).assertExists()
    }

    @Test
    fun `without a detachable camera the compass rides directly on the zoom pair`() {
        setRail(budget = railHeight(3), showLocate = false)

        rule.onNodeWithContentDescription(COMPASS).assertExists()
        rule.onNodeWithContentDescription(LOCATE).assertDoesNotExist()
    }

    @Test
    fun `exactly four segments' height, dividers included, holds all four`() {
        assertEquals(4, with(density) { railSegmentCount(budgetPx = railHeightPx(4), wanted = 4) })
    }

    @Test
    fun `one px short of four segments holds three`() {
        assertEquals(3, with(density) { railSegmentCount(budgetPx = railHeightPx(4) - 1, wanted = 4) })
    }

    @Test
    fun `a budget too short for the zoom pair still keeps it`() {
        assertEquals(2, with(density) { railSegmentCount(budgetPx = 0, wanted = 4) })
    }

    @Test
    fun `an unbounded budget holds every wanted segment`() {
        assertEquals(3, with(density) { railSegmentCount(budgetPx = Constraints.Infinity, wanted = 3) })
    }

    // The rail's laid-out height for [segments] segments at the test's mdpi density.
    private fun railHeight(segments: Int): Dp = with(density) { railHeightPx(segments).toDp() }

    private companion object {
        const val COMPASS = "Toggle north-up orientation"
        const val LOCATE = "Return to current position"
        const val ZOOM_IN = "Zoom in"
        const val ZOOM_OUT = "Zoom out"
    }
}
