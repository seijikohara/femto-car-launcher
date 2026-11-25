package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
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
    // shrunk at most to the 16 sp floor, never cut short. FitText cuts a line short only once even the floor would
    // overflow, so a line not cut short is one at 16 sp or more.
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

    @Test
    fun `at the default text size the card is its least height`() {
        setCard(TurnCardFixtures.turnThen)

        assertEquals(TurnCardHeight, rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().height)
    }

    // A larger system font grows the distance and the text past the arrow: the card grows with them, so nothing is
    // clipped at its top or bottom.
    @Test
    fun `a larger system font grows the card and keeps its text inside it`() {
        setCard(TurnCardFixtures.turnThen, fontScale = LARGE_FONT_SCALE)

        val card = rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        val inside =
            listOf("300", "Turn left", "Then").map { text ->
                rule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().let { box ->
                    box.top >= card.top && box.bottom <= card.bottom
                }
            }
        assertEquals(true to listOf(true, true, true), (card.height > TurnCardHeight) to inside)
    }

    private fun setCard(
        state: TurnCardState,
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FemtoTheme {
                    TurnCard(
                        state = state,
                        speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                        modifier = Modifier.width(TurnCardMinWidth).testTag(CARD),
                    )
                }
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
        const val CARD = "turn-card"

        // The largest step Android's own font-size setting offers on many devices.
        const val LARGE_FONT_SCALE = 1.5f

        val LONG_TEXTS =
            listOf(
                TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE) to "Enter the roundabout",
                TurnManeuver.Turn(TurnKind.EXIT_RIGHT) to "Take the exit",
                TurnManeuver.Turn(TurnKind.SLIGHT_RIGHT) to "Turn slightly right",
                TurnManeuver.Turn(TurnKind.SHARP_RIGHT) to "Turn sharply right",
            )
    }
}
