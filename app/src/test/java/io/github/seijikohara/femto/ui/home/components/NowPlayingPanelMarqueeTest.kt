package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import io.github.seijikohara.femto.testfixtures.fakeOverflowingNowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The full-screen player honours the motion gate on every metadata line.
 *
 * The panel's whole purpose over the compact card is showing metadata the card
 * has to truncate, and a headline-length title arrived truncated anyway. These
 * cases pin the wiring: parked, each line is one scrolling, clipped line; moving,
 * each keeps its resting line budget and ellipsizes — the same rule the card
 * follows, so the two surfaces cannot drift apart.
 *
 * Portrait on purpose: it is the geometry where the title's resting budget is two
 * lines, so the parked case has to collapse it rather than merely swap overflow.
 * The clock is paused because a parked line scrolls forever and the composition
 * would never go idle.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w412dp-h915dp-mdpi")
class NowPlayingPanelMarqueeTest {
    @get:Rule
    val rule = createComposeRule()

    private val track = fakeOverflowingNowPlaying()

    @Test
    fun parked_lines_scroll_as_one_clipped_line() {
        setPanel(stationary = true)

        listOf(track.title, track.artist, track.album).forEach { line ->
            val layout = layoutOf(assertNotNull(line))
            assertEquals(1, layout.layoutInput.maxLines, "\"$line\" is not a single line")
            assertEquals(TextOverflow.Clip, layout.layoutInput.overflow, "\"$line\" still ellipsizes")
        }
    }

    @Test
    fun moving_title_keeps_its_two_resting_lines_and_ellipsizes() {
        setPanel(stationary = false)

        val layout = layoutOf(track.title)
        // Portrait's headline column is narrow enough to need the second line
        // before truncating; the parked case above collapses it to one.
        assertEquals(2, layout.layoutInput.maxLines)
        assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow)
    }

    @Test
    fun moving_meta_lines_ellipsize() {
        setPanel(stationary = false)

        listOf(track.artist, track.album).forEach { line ->
            val layout = layoutOf(assertNotNull(line))
            assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow, "\"$line\" still scrolls while moving")
        }
    }

    private fun setPanel(stationary: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = track,
                    onCommand = {},
                    onLaunchSource = {},
                    onClose = {},
                    modifier = Modifier.fillMaxSize(),
                    stationary = stationary,
                )
            }
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode("\"$text\" did not render")
        val layouts = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        return assertNotNull(layouts.firstOrNull(), "\"$text\" exposed no text layout")
    }
}
