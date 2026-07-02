package io.github.seijikohara.femto.ui.home.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/**
 * Interpolate the live playback position from the PlaybackState basis while
 * playing, so progress UIs advance smoothly between session callbacks. Held
 * at [positionMs] when paused. Shared by the music card's progress bar and
 * the Now Playing panel's seek bar.
 */
@Composable
internal fun rememberInterpolatedPositionMs(
    positionMs: Long,
    durationMs: Long,
    positionUpdateTimeMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
): State<Long> =
    produceState(
        initialValue = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)),
        positionMs,
        positionUpdateTimeMs,
        isPlaying,
        playbackSpeed,
        durationMs,
    ) {
        val maxMs = durationMs.coerceAtLeast(0L)
        // Hold the reported position when paused, or when the session gives no
        // valid update-time basis (a 0 basis would interpolate from boot and
        // snap the bar to the end).
        if (!isPlaying || positionUpdateTimeMs <= 0L) {
            value = positionMs.coerceIn(0L, maxMs)
            return@produceState
        }
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - positionUpdateTimeMs
            value = (positionMs + (elapsed * playbackSpeed)).toLong().coerceIn(0L, maxMs)
            delay(500L)
        }
    }

/** Format milliseconds as m:ss for progress labels. */
internal fun formatMillis(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
