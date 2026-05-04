package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

class MusicPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_track_artist_and_transport() {
        rule.setContent {
            FemtoTheme {
                MusicPanel(
                    nowPlaying = fakeNowPlaying(),
                    onCommand = {},
                    onConnect = {},
                )
            }
        }
        rule.onNodeWithText("Strobe").assertIsDisplayed()
        rule.onNodeWithText("deadmau5", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Play / pause").assertIsDisplayed()
    }

    @Test
    fun renders_connect_placeholder_when_null_and_dispatches_on_tap() {
        var tapped = false
        rule.setContent {
            FemtoTheme {
                MusicPanel(
                    nowPlaying = null,
                    onCommand = {},
                    onConnect = { tapped = true },
                )
            }
        }
        rule.onNodeWithText("Connect a player").assertIsDisplayed().performClick()
        assert(tapped)
    }
}
