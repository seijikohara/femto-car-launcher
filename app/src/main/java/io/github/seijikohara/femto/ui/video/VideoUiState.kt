package io.github.seijikohara.femto.ui.video

/**
 * State for the dashboard's video window and its full panel (issue #390).
 *
 * [file] says whether there is a file to play ([VideoFileState]); without one
 * the window asks for a file.
 * [playing] is the audio's state. [pickFailed] says the last file picked
 * could not be kept, because its provider refused a lasting read grant; the
 * file before it, if any, stays. The motion gate's verdict is not part of
 * this state: it travels on its own flow ([VideoViewModel.pictureVisible]),
 * shared so that it never replays a stale "visible".
 */
internal data class VideoUiState(
    val windowEnabled: Boolean,
    val file: VideoFileState,
    val playing: Boolean,
    val pickFailed: Boolean = false,
) {
    val fileReady: Boolean get() = file == VideoFileState.READY

    companion object {
        val Off =
            VideoUiState(
                windowEnabled = false,
                file = VideoFileState.NONE,
                playing = false,
            )
    }
}

/** Whether the video window has a file to play. */
internal enum class VideoFileState {
    /** No file picked, or the window is off. */
    NONE,

    /** The picked file is loaded. */
    READY,

    /**
     * A file is picked but cannot be opened: its read grant is gone (revoked,
     * or the storage it lives on removed), or the player failed on it (deleted,
     * unreadable, or a format the device cannot decode). The window says so
     * before it asks for another file.
     */
    UNAVAILABLE,
}

/** Events the video window and panel report up. */
internal sealed interface VideoAction {
    /** Play when paused, pause when playing. */
    data object TogglePlayback : VideoAction

    /**
     * Open the system file picker. The host handles it, since only the UI can
     * launch the picker; the result comes back as [FilePicked].
     */
    data object PickFile : VideoAction

    /** The user picked [uri] (a content URI) in the system file picker. */
    data class FilePicked(
        val uri: String,
    ) : VideoAction

    /** Close the window: turn "Video window" off and stop playback. */
    data object Close : VideoAction
}
