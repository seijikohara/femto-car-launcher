package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.home.components.MusicCard
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the music card's height across its states: the idle states (nothing
 * playing / connect CTA) must render at exactly the Playing card's height, so
 * a playback start or stop never resizes the card and reflows the calendar /
 * weather row above it. The reserve is measured from an unplaced sample of the
 * Playing layout (`PlayingHeightReserve`), so the equality must hold under the
 * album-line toggle and user font settings alike — the dimensions a static dp
 * reserve historically drifted from. Each test swaps states in one composition
 * (the same transition the live card goes through) rather than composing the
 * variants side by side.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class MusicCardHeightTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // isPlaying = false keeps the progress interpolation static, so the
    // composition settles instead of ticking the position every frame.
    private var cardState by mutableStateOf<MusicCardState>(MusicCardState.Playing(fakeNowPlaying(isPlaying = false)))

    private fun setCard(
        showAlbum: Boolean,
        stressFontSettings: Boolean = false,
    ) {
        composeTestRule.setContent {
            FemtoTheme(
                fontBaseSizeSp = if (stressFontSettings) STRESS_BASE_SIZE_SP else DEFAULT_BASE_SIZE_SP,
                fontWeightStep = if (stressFontSettings) STRESS_WEIGHT_STEP else 0,
            ) {
                Box(modifier = Modifier.testTag(CARD_TAG).width(CardWidth)) {
                    MusicCard(
                        state = cardState,
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
    }

    private fun cardHeight(): Dp =
        composeTestRule
            .onNodeWithTag(CARD_TAG)
            .getUnclippedBoundsInRoot()
            .height

    private fun assertIdleStatesKeepPlayingHeight() {
        composeTestRule.waitForIdle()
        val playingHeight = cardHeight()
        // Anti-vacuous guard: a zero-height render would equate every state.
        assertTrue(playingHeight > 0.dp, "playing card did not render ($playingHeight)")

        cardState = MusicCardState.NoActiveSession
        Snapshot.sendApplyNotifications()
        composeTestRule.waitForIdle()
        assertEquals(playingHeight, cardHeight(), "NoActiveSession height differs from Playing")

        cardState = MusicCardState.NeedsPermission
        Snapshot.sendApplyNotifications()
        composeTestRule.waitForIdle()
        assertEquals(playingHeight, cardHeight(), "NeedsPermission height differs from Playing")
    }

    @Test
    fun `idle states keep the playing card height`() {
        setCard(showAlbum = true)
        assertIdleStatesKeepPlayingHeight()
    }

    @Test
    fun `idle states keep the playing card height without the album line`() {
        setCard(showAlbum = false)
        assertIdleStatesKeepPlayingHeight()
    }

    @Test
    fun `idle states keep the playing card height under stressed font settings`() {
        setCard(showAlbum = true, stressFontSettings = true)
        assertIdleStatesKeepPlayingHeight()
    }

    private companion object {
        const val CARD_TAG = "musicCard"

        // The head-unit floating-card width class: wide enough that the meta
        // column keeps every line, the regime where the reserve must track the
        // meta block rather than any width-capped art.
        val CardWidth = 340.dp

        // A user font setting that grows the meta block without moving any dp
        // value — the de-calibration class a static dp reserve cannot track.
        const val DEFAULT_BASE_SIZE_SP = 16
        const val STRESS_BASE_SIZE_SP = 20
        const val STRESS_WEIGHT_STEP = 2
    }
}
