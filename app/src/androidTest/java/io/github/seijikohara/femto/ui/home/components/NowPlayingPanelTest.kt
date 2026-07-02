package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NowPlayingPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_full_metadata() {
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = fakeNowPlaying(),
                    onCommand = {},
                    onLaunchSource = {},
                    onClose = {},
                )
            }
        }
        rule.onNodeWithText("Strobe").assertIsDisplayed()
        rule.onNodeWithText("deadmau5").assertIsDisplayed()
        rule.onNodeWithText("For Lack of a Better Name").assertIsDisplayed()
    }

    @Test
    fun collapse_button_invokes_onClose() {
        var closed = false
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = fakeNowPlaying(),
                    onCommand = {},
                    onLaunchSource = {},
                    onClose = { closed = true },
                )
            }
        }
        rule.onNodeWithContentDescription("Collapse player").performClick()
        assertTrue(closed)
    }

    @Test
    fun open_source_button_launches_the_session_package() {
        var launched: String? = null
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = fakeNowPlaying(),
                    onCommand = {},
                    onLaunchSource = { launched = it },
                    onClose = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Open Spotify").performClick()
        assertEquals("com.spotify.music", launched)
    }
}
