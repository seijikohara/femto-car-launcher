package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.data.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

class MusicCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_track_artist_and_transport_when_playing() {
        rule.setContent {
            FemtoTheme {
                MusicCard(
                    state = MusicCardState.Playing(fakeNowPlaying()),
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
    fun renders_connect_cta_and_dispatches_when_permission_missing() {
        var tapped = false
        rule.setContent {
            FemtoTheme {
                MusicCard(
                    state = MusicCardState.NeedsPermission,
                    onCommand = {},
                    onConnect = { tapped = true },
                )
            }
        }
        rule.onNodeWithText("Connect a player").assertIsDisplayed().performClick()
        assert(tapped)
    }

    @Test
    fun renders_nothing_playing_placeholder_when_no_active_session() {
        rule.setContent {
            FemtoTheme {
                MusicCard(
                    state = MusicCardState.NoActiveSession,
                    onCommand = {},
                    onConnect = {},
                )
            }
        }
        rule.onNodeWithText("Nothing is playing").assertIsDisplayed()
    }
}
