package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.home.components.MUSIC_ART_TAG
import io.github.seijikohara.femto.ui.home.components.MUSIC_META_TAG
import io.github.seijikohara.femto.ui.home.components.MusicCard
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The compact card's album art is a square that must match the meta column's
 * height, so the card padding reads evenly on all four sides.
 *
 * It is width-driven in practice: the art gets whatever the row can spare after
 * [io.github.seijikohara.femto.ui.theme.FemtoDimens.MusicMetaMinWidth], and when
 * that budget falls short the art shrinks and centres — opening a band above and
 * below it that the eye reads as a misaligned top inset. That is exactly what a
 * 190 dp floor produced on the head unit (a 95 dp square against a 133 dp meta
 * block, 19 dp of dead space at each end), so this test pins the relationship
 * rather than leaving it to a screenshot nobody measures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class MusicCardArtHeightTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setCard(
        cardWidth: Dp,
        showAlbum: Boolean = true,
    ) {
        composeTestRule.setContent {
            FemtoTheme {
                Box(modifier = Modifier.width(cardWidth)) {
                    MusicCard(
                        // isPlaying = false keeps the progress interpolation static so
                        // the composition settles instead of ticking every frame.
                        state = MusicCardState.Playing(fakeNowPlaying(isPlaying = false)),
                        onCommand = {},
                        onConnect = {},
                        onLaunchSource = {},
                        onExpand = {},
                        onPlay = {},
                        showAlbum = showAlbum,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    // The card merges its descendants' semantics (the whole card is one clickable
    // node), so the tagged children exist only in the unmerged tree.
    private fun bounds(tag: String) =
        composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun artHeight(): Dp = bounds(MUSIC_ART_TAG).height

    private fun artWidth(): Dp = bounds(MUSIC_ART_TAG).width

    private fun metaHeight(): Dp = bounds(MUSIC_META_TAG).height

    private fun assertMatchesMeta(tolerance: Dp = 2.dp) {
        val art = artHeight()
        val meta = metaHeight()
        // Anti-vacuous guard: a zero-height render would satisfy any comparison.
        assertTrue(art > 0.dp && meta > 0.dp, "card did not render (art=$art meta=$meta)")
        assertTrue(
            abs((art - meta).value) <= tolerance.value,
            "album art $art does not match the meta column $meta — the difference " +
                "opens as uneven padding above and below the art",
        )
    }

    @Test
    fun `the art matches the meta column height on the head-unit card`() {
        // The width the dashboard's floating column gives the card on an 853 dp
        // head unit, minus the column's own inset.
        setCard(cardWidth = 300.dp)

        assertMatchesMeta()
    }

    @Test
    fun `the art stays square`() {
        setCard(cardWidth = 300.dp)

        assertTrue(
            abs((artWidth() - artHeight()).value) <= 1f,
            "album art ${artWidth()} x ${artHeight()} is not square",
        )
    }

    @Test
    fun `the art still matches when the album line is hidden`() {
        // Hiding the album shortens the meta column, so the square must shrink with
        // it rather than keeping a taller art and re-opening the band.
        setCard(cardWidth = 300.dp, showAlbum = false)

        assertMatchesMeta()
    }
}
