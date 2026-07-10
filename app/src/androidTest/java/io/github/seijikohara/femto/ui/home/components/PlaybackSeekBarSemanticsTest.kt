package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlaybackSeekBarSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun seekable_bar_exposes_progress_range_and_set_progress_semantics() {
        rule.setContent {
            FemtoTheme {
                PlaybackSeekBar(
                    positionMs = 50_000L,
                    durationMs = 200_000L,
                    positionUpdateTimeMs = 0L,
                    isPlaying = false,
                    playbackSpeed = 1f,
                    canSeek = true,
                    onSeek = {},
                )
            }
        }
        rule
            .onNodeWithContentDescription("Seek")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
    }

    // The setProgress action must map its 0f..1f fraction through the same
    // seekTargetMs the tap/drag gestures use, onto the exact millis unit onSeek
    // expects — not a fraction, not a different rounding.
    @Test
    fun set_progress_action_maps_the_target_fraction_to_the_seek_position_in_millis() {
        var seekedToMs: Long? = null
        rule.setContent {
            FemtoTheme {
                PlaybackSeekBar(
                    positionMs = 0L,
                    durationMs = 200_000L,
                    positionUpdateTimeMs = 0L,
                    isPlaying = false,
                    playbackSpeed = 1f,
                    canSeek = true,
                    onSeek = { seekedToMs = it },
                )
            }
        }
        rule
            .onNodeWithContentDescription("Seek")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(0.5f) }
        assertEquals(100_000L, seekedToMs)
    }

    // Mirrors the fraction computation's own durationMs > 0L guard: with no
    // known duration there is nothing to seek within, so the action reports
    // failure instead of dispatching a meaningless onSeek(0).
    @Test
    fun set_progress_action_reports_failure_and_does_not_seek_when_duration_is_unknown() {
        var seekCalled = false
        rule.setContent {
            FemtoTheme {
                PlaybackSeekBar(
                    positionMs = 0L,
                    durationMs = 0L,
                    positionUpdateTimeMs = 0L,
                    isPlaying = false,
                    playbackSpeed = 1f,
                    canSeek = true,
                    onSeek = { seekCalled = true },
                )
            }
        }
        var handled = true
        rule
            .onNodeWithContentDescription("Seek")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> handled = setProgress(0.5f) }
        assertFalse(handled)
        assertFalse(seekCalled)
    }
}
