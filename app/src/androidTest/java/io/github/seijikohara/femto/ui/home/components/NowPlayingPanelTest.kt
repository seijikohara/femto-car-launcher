package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.QueueEntry
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

    @Test
    fun seek_gesture_surface_is_absent_without_the_capability() {
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = fakeNowPlaying(canSeek = false),
                    onCommand = {},
                    onLaunchSource = {},
                    onClose = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Seek").assertDoesNotExist()
    }

    @Test
    fun seek_gesture_surface_is_present_with_the_capability() {
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = fakeNowPlaying(canSeek = true),
                    onCommand = {},
                    onLaunchSource = {},
                    onClose = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Seek").assertIsDisplayed()
    }

    @Test
    fun shuffle_and_repeat_are_hidden_without_capabilities() {
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = fakeNowPlaying(canShuffle = false, canRepeat = false),
                    onCommand = {},
                    onLaunchSource = {},
                    onClose = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Shuffle").assertDoesNotExist()
        rule.onNodeWithContentDescription("Repeat").assertDoesNotExist()
    }

    @Test
    fun shuffle_tap_dispatches_ToggleShuffle() {
        val commands = mutableListOf<MusicCommand>()
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = fakeNowPlaying(canShuffle = true, canRepeat = true),
                    onCommand = { commands += it },
                    onLaunchSource = {},
                    onClose = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Shuffle").performClick()
        rule.onNodeWithContentDescription("Repeat").performClick()
        assertEquals(listOf<MusicCommand>(MusicCommand.ToggleShuffle, MusicCommand.CycleRepeat), commands)
    }

    @Test
    fun queue_is_hidden_without_the_capability_even_when_populated() {
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying =
                        fakeNowPlaying(
                            canSkipToQueueItem = false,
                            queue = listOf(QueueEntry(1L, "Next Track", null)),
                        ),
                    onCommand = {},
                    onLaunchSource = {},
                    onClose = {},
                )
            }
        }
        rule.onNodeWithText("Next Track").assertDoesNotExist()
    }

    @Test
    fun queue_entry_tap_dispatches_SkipToQueueItem() {
        val commands = mutableListOf<MusicCommand>()
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying =
                        fakeNowPlaying(
                            canSkipToQueueItem = true,
                            queue = listOf(QueueEntry(42L, "Next Track", "The Wayfinders")),
                        ),
                    onCommand = { commands += it },
                    onLaunchSource = {},
                    onClose = {},
                )
            }
        }
        rule.onNodeWithText("Next Track").performClick()
        assertEquals(listOf<MusicCommand>(MusicCommand.SkipToQueueItem(42L)), commands)
    }
}
