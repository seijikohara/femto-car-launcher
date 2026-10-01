package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.ui.video.VideoUiState

/** A video window that is on, defaulting to a loaded file, paused. */
internal fun fakeVideoUiState(
    fileReady: Boolean = true,
    playing: Boolean = false,
    pickFailed: Boolean = false,
): VideoUiState =
    VideoUiState(
        windowEnabled = true,
        fileReady = fileReady,
        playing = playing,
        pickFailed = pickFailed,
    )
