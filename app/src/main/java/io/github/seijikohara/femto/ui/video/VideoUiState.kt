package io.github.seijikohara.femto.ui.video

/**
 * State for the dashboard's video window and its full panel (issue #390).
 *
 * [fileReady] is false with no picked file, a file whose read grant is gone,
 * or one the player could not open: the window then asks for a file.
 * [pictureVisible] is the motion gate's verdict (videoPictureVisibleFlow);
 * while it is false the window draws no frame at all, and [playing] (the
 * audio) is unaffected.
 */
internal data class VideoUiState(
    val windowEnabled: Boolean,
    val fileReady: Boolean,
    val pictureVisible: Boolean,
    val playing: Boolean,
) {
    companion object {
        val Off =
            VideoUiState(
                windowEnabled = false,
                fileReady = false,
                pictureVisible = false,
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
