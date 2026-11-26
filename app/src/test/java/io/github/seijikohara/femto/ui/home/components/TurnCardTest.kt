package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
                    form = TurnCardForm.FULL,
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
    // clipped at its top or bottom. At its widest, where the full card still fits the larger text.
    @Test
    fun `a larger system font grows the card and keeps its text inside it`() {
        setCard({ TurnCardFixtures.turnThen }, width = TurnCardMaxWidth, fontScale = LARGE_FONT_SCALE)

        val card = rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        val inside =
            listOf("300", "Turn left", "Then").map { text ->
                rule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().let { box ->
                    box.top >= card.top && box.bottom <= card.bottom
                }
            }
        assertEquals(true to listOf(true, true, true), (card.height > TurnCardHeight) to inside)
    }

    // --- The compact form ----------------------------------------------------------------------------------------

    // Below the full card's height the card keeps the arrow and the distance on one line.
    @Test
    fun `under the full card's height the compact card shows, 56 dp tall and 64 with the turn after next`() {
        var state by mutableStateOf(TurnCardFixtures.turn)
        setCard({ state }, width = TurnCardMinWidth, maxHeight = BELOW_FULL)
        val alone = rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().height
        val aloneText = rule.onAllNodesWithText("Turn left").fetchSemanticsNodes().size

        state = TurnCardFixtures.turnThen
        rule.waitForIdle()

        assertEquals(
            Triple(56.dp, 64.dp, 0),
            Triple(alone, rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().height, aloneText),
        )
        rule.onNodeWithText("Then", useUnmergedTree = true).assertExists()
    }

    // The sentence is not drawn, but a screen reader hears the same as from the full card.
    @Test
    fun `the compact card reads as the same sentence`() {
        setCard({ TurnCardFixtures.turnThen }, width = TurnCardMinWidth, maxHeight = BELOW_FULL)

        rule.onNodeWithContentDescription("Turn left in 300 m. Then: Turn right.").assertExists()
    }

    @Test
    fun `a slot narrower than the full card's minimum gets the compact card`() {
        setCard({ TurnCardFixtures.turn }, width = TurnCardMinWidth - 1.dp)

        assertEquals(
            1 to 0,
            rule.onAllNodesWithText("300").fetchSemanticsNodes().size to
                rule.onAllNodesWithText("Turn left").fetchSemanticsNodes().size,
        )
    }

    // At the compact card's narrowest the turn after next does not fit beside the distance, and is left out.
    @Test
    fun `at the compact card's minimum width the turn after next is left out`() {
        setCard({ TurnCardFixtures.turnThen }, width = TurnCardCompactMinWidth)

        assertEquals(
            1 to 0,
            rule.onAllNodesWithText("300").fetchSemanticsNodes().size to
                rule.onAllNodesWithText("Then", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `below the compact card's minimum width the card shows nothing`() {
        setCard({ TurnCardFixtures.turnThen }, width = TurnCardCompactMinWidth - 1.dp)

        assertEquals(
            0 to 0.dp,
            rule.onAllNodes(hasContentDescription("Turn left", substring = true)).fetchSemanticsNodes().size to
                rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().height,
        )
    }

    @Test
    fun `under the compact card's height the card shows nothing`() {
        setCard({ TurnCardFixtures.turn }, width = TurnCardMinWidth, maxHeight = 55.dp)

        assertEquals(0.dp, rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().height)
    }

    // The longest distances in metres, kilometres, feet and miles, at the compact card's narrowest.
    @Test
    fun `the compact card cuts no distance short at its minimum width`() {
        var reading by mutableStateOf(LONG_DISTANCES.first())
        setCard(
            { TurnCardFixtures.turn.copy(distanceMeters = reading.meters) },
            width = TurnCardCompactMinWidth,
            speedUnit = { reading.unit },
        )
        val cutShort =
            LONG_DISTANCES.filter { candidate ->
                reading = candidate
                rule.waitForIdle()
                val card = rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
                listOf(candidate.value, candidate.label).any { text ->
                    rule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().let { box ->
                        box.left < card.left || box.right > card.right
                    } ||
                        textLayout(text).isLineEllipsized(0)
                }
            }
        assertEquals(emptyList(), cutShort.map { "${it.value} ${it.label}" })
    }

    // --- What the forms measure ----------------------------------------------------------------------------------

    // Between the compact card's minimum and the width its turn after next needs, the form must not follow the
    // digits: "90 m" and "100 m" at a red light would switch the small arrow on and off.
    @Test
    fun `a slot between the two compact widths keeps its form at 94 m and at 104 m`() {
        var meters by mutableDoubleStateOf(94.0)
        setCard({ TurnCardFixtures.turnThen.copy(distanceMeters = meters) }, width = BETWEEN_COMPACT_WIDTHS)
        val near = countOf("90") to countOf("Then")

        meters = 104.0
        rule.waitForIdle()

        assertEquals(1 to near.second, near.first to near.second, "the card shows 90 m")
        assertEquals(1 to near.second, countOf("100") to countOf("Then"))
    }

    // The widest distances at a larger font, at the card's widest: whichever form shows cuts nothing short.
    @Test
    fun `at a larger font the longest distances are cut short in no form`() {
        var reading by mutableStateOf(LONG_DISTANCES.first())
        setCard(
            { TurnCardFixtures.turnThen.copy(distanceMeters = reading.meters) },
            width = TurnCardMaxWidth,
            fontScale = LARGER_FONT_SCALE,
            speedUnit = { reading.unit },
        )
        val cutShort =
            LONG_DISTANCES.filter { candidate ->
                reading = candidate
                rule.waitForIdle()
                val card = rule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
                listOf(candidate.value, candidate.label, "Turn left").any { text ->
                    rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                        (
                            rule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().let { box ->
                                box.left < card.left || box.right > card.right
                            } ||
                                textLayout(text).isLineEllipsized(0)
                        )
                }
            }
        assertEquals(emptyList(), cutShort.map { "${it.value} ${it.label}" })
    }

    private fun setCard(
        state: TurnCardState,
        fontScale: Float = 1f,
    ) = setCard({ state }, fontScale = fontScale)

    private fun countOf(text: String): Int =
        rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun setCard(
        state: () -> TurnCardState,
        width: Dp = TurnCardMinWidth,
        maxHeight: Dp = Dp.Infinity,
        fontScale: Float = 1f,
        speedUnit: () -> SpeedUnit = { SpeedUnit.KILOMETERS_PER_HOUR },
    ) {
        // The card in the form the one decision picks for [width] by [maxHeight], as the dashboard draws it; nothing
        // where no form fits, which leaves the slot [width] wide and no height.
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FemtoTheme {
                    val shown = state()
                    val unit = speedUnit()
                    Box(modifier = Modifier.width(width).testTag(CARD)) {
                        rememberTurnCardSizes(shown, unit).formOrNull(width, maxHeight)?.let { form ->
                            TurnCard(state = shown, speedUnit = unit, form = form, modifier = Modifier.fillMaxWidth())
                        }
                    }
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

    private data class LongDistance(
        val meters: Double,
        val unit: SpeedUnit,
        val value: String,
        val label: String,
    )

    private companion object {
        const val CARD = "turn-card"

        // The largest step Android's own font-size setting offers on many devices.
        const val LARGE_FONT_SCALE = 1.5f

        // Just under the full card's height, so the card falls back to the compact one.
        val BELOW_FULL = TurnCardHeight - 1.dp

        // Wider than the compact card's minimum, narrower than its turn after next needs beside three digits.
        val BETWEEN_COMPACT_WIDTHS = 210.dp

        // A larger step of Android's own font-size setting.
        const val LARGER_FONT_SCALE = 1.3f

        // The widest distance each unit family shows: three digits below 1,000 m or ft, three whole digits above.
        val LONG_DISTANCES =
            listOf(
                LongDistance(950.0, SpeedUnit.KILOMETERS_PER_HOUR, "950", "m"),
                LongDistance(999_000.0, SpeedUnit.KILOMETERS_PER_HOUR, "999", "km"),
                LongDistance(289.56, SpeedUnit.MILES_PER_HOUR, "950", "ft"),
                LongDistance(1_607_733.0, SpeedUnit.MILES_PER_HOUR, "999", "mi"),
            )

        val LONG_TEXTS =
            listOf(
                TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE) to "Enter the roundabout",
                TurnManeuver.Turn(TurnKind.EXIT_RIGHT) to "Take the exit",
                TurnManeuver.Turn(TurnKind.SLIGHT_RIGHT) to "Turn slightly right",
                TurnManeuver.Turn(TurnKind.SHARP_RIGHT) to "Turn sharply right",
            )
    }
}
