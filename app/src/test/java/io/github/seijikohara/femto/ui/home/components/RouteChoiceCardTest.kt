package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.ui.home.CandidateSummary
import io.github.seijikohara.femto.ui.home.RouteChoiceState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Native graphics, so text measures with real font metrics: the travel time's units follow its measured width.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class RouteChoiceCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val selections = mutableListOf<Int>()

    private fun setCard(
        choice: RouteChoiceState = checkNotNull(DashboardFixtures.routeChoiceState.activeRoute?.choice),
    ) {
        rule.setContent {
            FemtoTheme {
                RouteChoiceCard(
                    choice = choice,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    onSelect = { selections += it },
                    onClose = {},
                    modifier = Modifier.width(360.dp),
                )
            }
        }
    }

    private fun firstRouteAlone(
        finding: Boolean,
        searchCompleted: Boolean,
    ) = RouteChoiceState(
        session = 1L,
        candidates = listOf(CandidateSummary(distanceMeters = 12_400, durationSeconds = 1_080)),
        selected = 0,
        finding = finding,
        searchCompleted = searchCompleted,
    )

    @Test
    fun `a tap on a tile selects its route`() {
        setCard()

        rule.onNodeWithText("Route 3").performClick()

        assertEquals(listOf(2), selections)
    }

    @Test
    fun `each tile is a radio button, the chosen one selected`() {
        setCard()

        rule.onNodeWithText("Route 2").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        rule.onNodeWithText("Route 2").assertIsSelected()
        rule.onNodeWithText("Route 1").assertIsNotSelected()
    }

    @Test
    fun `while another route is searched for, the next slot says so and offers nothing to tap`() {
        setCard(firstRouteAlone(finding = true, searchCompleted = false))

        rule.onNodeWithText("Finding another route…").assertHasNoClickAction()
    }

    @Test
    fun `no other route found shows after a search that ran to its end, never after one that was stopped`() {
        var choice by mutableStateOf(firstRouteAlone(finding = false, searchCompleted = false))
        rule.setContent {
            FemtoTheme {
                RouteChoiceCard(choice = choice, speedUnit = SpeedUnit.KILOMETERS_PER_HOUR, onSelect = {}, onClose = {})
            }
        }
        val afterAStop = rule.onAllNodesWithText("No other route found").fetchSemanticsNodes().size

        choice = firstRouteAlone(finding = false, searchCompleted = true)

        assertEquals(0 to 1, afterAStop to rule.onAllNodesWithText("No other route found").fetchSemanticsNodes().size)
    }

    // The dashboard stays composed under the Settings sheet, so a text size picked there reaches an open card. At the
    // default size "1 hr, 5 min" fits a tile on one line; at one and a half times it does not, and the tile takes the
    // narrow units.
    @Test
    fun `a text size change on an open card picks the travel time's units again`() {
        var baseSizeSp by mutableStateOf(FemtoDimens.BaseTextSize.value.toInt())
        val choice = checkNotNull(DashboardFixtures.routeChoiceState.activeRoute?.choice)
        rule.setContent {
            FemtoTheme(fontBaseSizeSp = baseSizeSp) {
                RouteChoiceCard(
                    choice = choice,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    onSelect = {},
                    onClose = {},
                    modifier = Modifier.width(360.dp),
                )
            }
        }
        val atDefault = rule.onAllNodesWithText("1 hr, 5 min").fetchSemanticsNodes().size

        baseSizeSp = baseSizeSp * 3 / 2

        assertEquals(1 to 1, atDefault to rule.onAllNodesWithText("1h 5m").fetchSemanticsNodes().size)
    }

    // "Done" alone is narrower than the floor; the button must not be.
    @Test
    fun `Done is a full tap target both ways`() {
        setCard()

        val done = rule.onNodeWithText("Done", useUnmergedTree = false).getUnclippedBoundsInRoot()

        assertTrue(
            done.width >= FemtoDimens.MinTouchTarget && done.height >= FemtoDimens.MinTouchTarget,
            "Done measures $done",
        )
    }
}
