package io.github.seijikohara.femto.ui.video

/**
 * State for the dashboard's video window and its full panel (issue #390).
 *
 * [fileReady] is false with no picked file, a file whose read grant is gone,
 * or one the player could not open: the window then asks for a file.
 * [playing] is the audio's state. [pickFailed] says the last file picked
 * could not be kept, because its provider refused a lasting read grant; the
 * file before it, if any, stays. The motion gate's verdict is not part of
 * this state: it travels on its own flow ([VideoViewModel.pictureVisible]),
 * shared so that it never replays a stale "visible".
 */
internal data class VideoUiState(
    val windowEnabled: Boolean,
    val fileReady: Boolean,
    val playing: Boolean,
    val pickFailed: Boolean = false,
) {
    companion object {
        val Off =
            VideoUiState(
                windowEnabled = false,
                fileReady = false,
                playing = false,
            )
    }
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
