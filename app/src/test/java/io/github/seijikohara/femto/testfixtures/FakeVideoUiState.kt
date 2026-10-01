package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.ui.video.VideoUiState

/** A video window that is on, defaulting to a loaded file with its picture showing, paused. */
internal fun fakeVideoUiState(
    fileReady: Boolean = true,
    pictureVisible: Boolean = true,
    playing: Boolean = false,
): VideoUiState =
    VideoUiState(
        windowEnabled = true,
        fileReady = fileReady,
        pictureVisible = pictureVisible,
        playing = playing,
    )
