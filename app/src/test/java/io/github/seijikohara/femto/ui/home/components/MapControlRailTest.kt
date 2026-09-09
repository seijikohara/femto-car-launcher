package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun `the segment count fits the budget whole and never drops the zoom pair`() {
        // Exactly the four segments' height (with the three dividers) holds all
        // four; one dp less drops to three; a budget too short for the zoom pair
        // still keeps it; an unbounded budget holds every wanted segment.
        assertEquals(4, railSegmentCount(budget = railHeight(4), wanted = 4))
        assertEquals(3, railSegmentCount(budget = railHeight(4) - 1.dp, wanted = 4))
        assertEquals(2, railSegmentCount(budget = 0.dp, wanted = 4))
        assertEquals(3, railSegmentCount(budget = Dp.Infinity, wanted = 3))
    }

    private companion object {
        const val COMPASS = "Toggle north-up orientation"
        const val LOCATE = "Return to current position"
        const val ZOOM_IN = "Zoom in"
        const val ZOOM_OUT = "Zoom out"
    }
}
