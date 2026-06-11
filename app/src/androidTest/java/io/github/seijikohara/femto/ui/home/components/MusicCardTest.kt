package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
                    onLaunchSource = {},
                )
            }
        }
        rule.onNodeWithText("Strobe").assertIsDisplayed()
        rule.onNodeWithText("deadmau5", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Play / pause").assertIsDisplayed()
    }

    @Test
    fun renders_album_when_present() {
        rule.setContent {
            FemtoTheme {
                MusicCard(
                    state = MusicCardState.Playing(fakeNowPlaying(album = "For Lack of a Better Name")),
                    onCommand = {},
                    onConnect = {},
                    onLaunchSource = {},
                )
            }
        }
        rule.onNodeWithText("For Lack of a Better Name").assertIsDisplayed()
    }

    @Test
    fun keeps_paused_session_on_screen_instead_of_collapsing_to_empty() {
        rule.setContent {
            FemtoTheme {
                MusicCard(
                    state = MusicCardState.Playing(fakeNowPlaying(isPlaying = false)),
                    onCommand = {},
                    onConnect = {},
                    onLaunchSource = {},
                )
            }
        }
        rule.onNodeWithText("Strobe").assertIsDisplayed()
        rule.onNodeWithContentDescription("Play / pause").assertIsDisplayed()
    }

    @Test
    fun tapping_the_card_dispatches_launch_with_the_source_package() {
        var launched: String? = null
        rule.setContent {
            FemtoTheme {
                MusicCard(
                    state = MusicCardState.Playing(fakeNowPlaying()),
                    onCommand = {},
                    onConnect = {},
                    onLaunchSource = { launched = it },
                )
            }
        }
        // The whole card (outside the transport controls) opens the source app.
        // Tapping the title — a non-interactive child — reaches the card's clickable.
        rule.onNodeWithText("Strobe").performClick()
        assertEquals("com.spotify.music", launched)
    }

    @Test
    fun tapping_a_transport_control_does_not_launch_the_source() {
        var launched: String? = null
        var command: MusicCommand? = null
        rule.setContent {
            FemtoTheme {
                MusicCard(
                    state = MusicCardState.Playing(fakeNowPlaying()),
                    onCommand = { command = it },
                    onConnect = {},
                    onLaunchSource = { launched = it },
                )
            }
        }
        // The transport button consumes its tap, so the card-wide launch must not fire.
        rule.onNodeWithContentDescription("Play / pause").performClick()
        assertEquals(MusicCommand.PlayPause, command)
        assertEquals(null, launched)
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
                    onLaunchSource = {},
                )
            }
        }
        rule.onNodeWithText("Tap to connect a player").assertIsDisplayed().performClick()
        assertTrue(tapped)
    }

    @Test
    fun renders_nothing_playing_placeholder_when_no_active_session() {
        rule.setContent {
            FemtoTheme {
                MusicCard(
                    state = MusicCardState.NoActiveSession,
                    onCommand = {},
                    onConnect = {},
                    onLaunchSource = {},
                )
            }
        }
        rule.onNodeWithText("Nothing is playing").assertIsDisplayed()
    }
}
