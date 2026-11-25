package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.testfixtures.TurnCardFixtures
import io.github.seijikohara.femto.ui.home.TurnCardState
import io.github.seijikohara.femto.ui.home.TurnManeuver
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// Native graphics, so text measures with real font metrics: the line of text must fit the card's narrowest width.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class TurnCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a turn shows its distance and text, and no turn after next`() {
        setCard(TurnCardFixtures.turn)

        assertEquals(
            listOf(1, 1, 1, 0),
            listOf("300", "m", "Turn left", "Then").map { rule.onAllNodesWithText(it).fetchSemanticsNodes().size },
        )
    }

    @Test
    fun `a turn followed closely shows the turn after next under Then`() {
        setCard(TurnCardFixtures.turnThen)

        rule.onNodeWithText("Then").assertExists()
    }

    @Test
    fun `the destination shows the distance left and says so`() {
        setCard(TurnCardFixtures.arrive)

        assertEquals(
            listOf(1, 1, 1),
            listOf("1.2", "km", "Destination").map { rule.onAllNodesWithText(it).fetchSemanticsNodes().size },
        )
    }

    @Test
    fun `the card reads as one sentence`() {
        setCard(TurnCardFixtures.turnThen)

        assertEquals(
            1,
            rule
                .onAllNodes(hasContentDescription("Turn left in 300 m. Then: Turn right."))
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun `the card offers nothing to tap`() {
        setCard(TurnCardFixtures.turnThen)

        rule.onNodeWithContentDescription("Turn left in 300 m. Then: Turn right.").assertHasNoClickAction()
    }

    // The narrowest card with the turn after next leaves its text the least room: every text still fits on its line,
    // shrunk at most to the 16 sp floor, never cut short.
    @Test
    fun `no text is cut short at the card's minimum width`() {
        var state by mutableStateOf(TurnCardFixtures.turnThen)
        rule.setContent {
            FemtoTheme {
                TurnCard(
                    state = state,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    modifier = Modifier.width(TurnCardMinWidth),
                )
            }
        }
        val cutShort =
            LONG_TEXTS.filter { (maneuver, text) ->
                state = state.copy(maneuver = maneuver)
                rule.waitForIdle()
                textLayout(text).isLineEllipsized(0)
            }
        assertEquals(emptyList(), cutShort.map { it.second })
    }

    private fun setCard(state: TurnCardState) {
        rule.setContent {
            FemtoTheme {
                TurnCard(
                    state = state,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    modifier = Modifier.width(TurnCardMinWidth),
                )
            }
        }
    }

    private fun textLayout(text: String): TextLayoutResult =
        assertNotNull(
            mutableListOf<TextLayoutResult>()
                .also {
                    rule
                        .onNodeWithText(text, useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .config
                        .getOrNull(SemanticsActions.GetTextLayoutResult)
                        ?.action
                        ?.invoke(it)
                }.firstOrNull(),
        )

    private companion object {
        val LONG_TEXTS =
            listOf(
                TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE) to "Enter the roundabout",
                TurnManeuver.Turn(TurnKind.EXIT_RIGHT) to "Take the exit",
                TurnManeuver.Turn(TurnKind.SLIGHT_RIGHT) to "Turn slightly right",
                TurnManeuver.Turn(TurnKind.SHARP_RIGHT) to "Turn sharply right",
            )
    }
}
