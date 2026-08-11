package io.github.seijikohara.femto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [ScrollingText]'s contract: scrolling, the line is single and clipped; at rest
 * it keeps its caller's line budget and ellipsizes.
 *
 * The two properties are what make the scroll honest. A marquee that kept a
 * trailing ellipsis would claim text is missing while showing all of it, and one
 * that kept a multi-line budget would not be a marquee at all.
 *
 * Deliberately short text: an overflowing line scrolls forever, and a running
 * animation stops the composition ever going idle, which hangs every finder in
 * this file. The overflow behaviour itself is covered by the screenshot goldens
 * (MusicCardScreenshotTest, NowPlayingPanelMarqueeScreenshotTest), which pause
 * the clock to capture it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScrollingTextTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a scrolling line is single and clipped`() {
        setContent(scrolling = true, restingMaxLines = 2)

        val layout = layoutOfSample()
        assertEquals(1, layout.layoutInput.maxLines)
        assertEquals(TextOverflow.Clip, layout.layoutInput.overflow)
    }

    @Test
    fun `a resting line keeps its line budget and ellipsizes`() {
        setContent(scrolling = false, restingMaxLines = 2)

        val layout = layoutOfSample()
        assertEquals(2, layout.layoutInput.maxLines)
        assertEquals(TextOverflow.Ellipsis, layout.layoutInput.overflow)
    }

    private fun setContent(
        scrolling: Boolean,
        restingMaxLines: Int,
    ) = rule.setContent {
        FemtoTheme {
            ScrollingText(
                text = SAMPLE,
                style = MaterialTheme.typography.titleLarge,
                scrolling = scrolling,
                restingMaxLines = restingMaxLines,
            )
        }
    }

    private fun layoutOfSample(): TextLayoutResult {
        val node = rule.onNodeWithText(SAMPLE).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        return assertNotNull(layouts.firstOrNull(), "no text layout")
    }

    private companion object {
        const val SAMPLE = "Short"
    }
}
