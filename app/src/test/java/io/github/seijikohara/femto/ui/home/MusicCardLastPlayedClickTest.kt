package io.github.seijikohara.femto.ui.home

import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.FAKE_MUSIC_APP_NAME
import io.github.seijikohara.femto.testfixtures.FAKE_MUSIC_PACKAGE
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedState
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.home.components.MUSIC_ART_TAG
import io.github.seijikohara.femto.ui.home.components.MusicCard
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The taps on the music card. The last played card shares its layout with the Playing card, so a tap anywhere on it
 * opens the app except on the resume button; the art tile and the eyebrow carry no expand tap there, as there is no
 * player to expand. The Playing card's own taps are pinned for contrast: the art and the eyebrow expand the player,
 * the lines below them open the app. The nodes are read from the unmerged tree where a tag or a text sits inside a
 * merged clickable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h640dp-mdpi")
class MusicCardLastPlayedClickTest {
    @get:Rule
    val rule = createComposeRule()

    private val launched = mutableListOf<String>()
    private val resumed = mutableListOf<String>()
    private var expands = 0

    @Test
    fun `a tap on the last played card opens the remembered track's app`() {
        setCard(fakeLastPlayedState())

        rule.onNodeWithText(fakeLastPlayedState().track.title, useUnmergedTree = true).tap()

        assertEquals(listOf(FAKE_MUSIC_PACKAGE), launched)
    }

    @Test
    fun `a tap on the resume button resumes the track with its package`() {
        setCard(fakeLastPlayedState())

        rule.onNodeWithContentDescription(resumeLabel()).tap()

        assertEquals(listOf(FAKE_MUSIC_PACKAGE), resumed)
    }

    @Test
    fun `a tap on the resume button does not also open the app`() {
        setCard(fakeLastPlayedState())

        rule.onNodeWithContentDescription(resumeLabel()).tap()

        // Anti-vacuous guard: the tap reached the button, so the empty launch list is the button consuming it.
        assertEquals(1, resumed.size)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `a tap on the art tile of the last played card opens the app and does not expand`() {
        setCard(fakeLastPlayedState())

        rule.onNodeWithTag(MUSIC_ART_TAG, useUnmergedTree = true).tap()

        // Anti-vacuous guard: the tap reached the card, so no expand is the art having no tap of its own.
        assertEquals(listOf(FAKE_MUSIC_PACKAGE), launched)
        assertEquals(0, expands)
    }

    @Test
    fun `a tap on the eyebrow of the last played card opens the app and does not expand`() {
        setCard(fakeLastPlayedState())

        rule.onNodeWithText(FAKE_MUSIC_APP_NAME.uppercase(), useUnmergedTree = true).tap()

        // Anti-vacuous guard: the tap reached the card, so no expand is the eyebrow having no tap of its own.
        assertEquals(listOf(FAKE_MUSIC_PACKAGE), launched)
        assertEquals(0, expands)
    }

    // The resume button is disabled while an attempt runs, and it still takes the tap: a second tap neither starts
    // another attempt nor opens the app over the one that is running.
    @Test
    fun `a tap on the disabled resume button while resuming does nothing`() {
        setCard(fakeLastPlayedState(resuming = true))
        rule.onNodeWithContentDescription(resumeLabel()).assertIsNotEnabled()

        rule.onNodeWithContentDescription(resumeLabel()).tap()

        assertEquals(emptyList<String>() to emptyList<String>(), resumed.toList() to launched.toList())
    }

    @Test
    fun `a tap on the art of the playing card expands the player`() {
        setCard(MusicCardState.Playing(fakeNowPlaying(isPlaying = false)))

        rule.onNodeWithContentDescription(string(R.string.music_expand_player)).tap()

        assertEquals(1, expands)
        assertEquals(emptyList(), launched)
    }

    @Test
    fun `a tap on the eyebrow of the playing card expands the player`() {
        setCard(MusicCardState.Playing(fakeNowPlaying(isPlaying = false)))

        rule.onNodeWithContentDescription(string(R.string.music_expand_player_header)).tap()

        assertEquals(1, expands)
        assertEquals(emptyList(), launched)
    }

    // The artist line, not the title: the eyebrow's expand tap reaches the minimum touch target around its short
    // row, which covers the top half of the title below it.
    @Test
    fun `a tap on the artist of the playing card opens the app and does not expand`() {
        setCard(MusicCardState.Playing(fakeNowPlaying(isPlaying = false)))

        rule.onNodeWithText(checkNotNull(fakeNowPlaying().artist), useUnmergedTree = true).tap()

        assertEquals(listOf(fakeNowPlaying().packageName), launched)
        assertEquals(0, expands)
    }

    // Tap and let the composition settle, so the test ends with no frame requested (the tap starts a ripple).
    private fun SemanticsNodeInteraction.tap() {
        performClick()
        rule.waitForIdle()
    }

    private fun setCard(state: MusicCardState) {
        launched.clear()
        resumed.clear()
        expands = 0
        rule.setContent {
            FemtoTheme {
                MusicCard(
                    state = state,
                    onCommand = {},
                    onConnect = {},
                    onLaunchSource = { launched += it },
                    onExpand = { expands++ },
                    onPlay = {},
                    onResume = { resumed += it },
                    modifier = Modifier.width(320.dp),
                )
            }
        }
    }

    private fun resumeLabel(): String = string(R.string.music_resume, fakeLastPlayedState().track.title)

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)
}
