package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.ui.video.VideoFileState
import io.github.seijikohara.femto.ui.video.VideoUiState

/** A video window that is on, defaulting to a loaded file, paused. */
internal fun fakeVideoUiState(
    file: VideoFileState = VideoFileState.READY,
    playing: Boolean = false,
    pickFailed: Boolean = false,
): VideoUiState =
    VideoUiState(
        windowEnabled = true,
        file = file,
        playing = playing,
        pickFailed = pickFailed,
    )
