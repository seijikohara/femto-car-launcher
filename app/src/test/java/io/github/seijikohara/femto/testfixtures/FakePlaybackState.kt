package io.github.seijikohara.femto.testfixtures

import android.media.session.PlaybackState

/**
 * Build a real platform [PlaybackState]. Robolectric supplies the genuine
 * builder, so session tests read the same fields the launcher reads off a live
 * session instead of a mock's answers.
 */
internal fun fakePlaybackState(
    state: Int,
    positionMs: Long = 0L,
    speed: Float = 1f,
    updateTimeMs: Long = 0L,
    actions: Long = 0L,
): PlaybackState =
    PlaybackState
        .Builder()
        .setState(state, positionMs, speed, updateTimeMs)
        .setActions(actions)
        .build()
