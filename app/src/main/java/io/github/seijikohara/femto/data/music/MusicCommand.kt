package io.github.seijikohara.femto.data.music

/**
 * Transport commands the dashboard can dispatch to the music session.
 *
 * Lives in the data layer because [MusicSessionRepository] consumes it;
 * `data/` never imports `ui/`.
 */
internal sealed interface MusicCommand {
    data object PlayPause : MusicCommand

    data object SkipNext : MusicCommand

    data object SkipPrevious : MusicCommand

    /** Jump to an absolute position; only offered when [NowPlaying.canSeek]. */
    data class SeekTo(
        val positionMs: Long,
    ) : MusicCommand

    /** Flip shuffle on/off; only offered when [NowPlaying.canShuffle]. */
    data object ToggleShuffle : MusicCommand

    /** Step repeat none -> all -> one -> none; only offered when [NowPlaying.canRepeat]. */
    data object CycleRepeat : MusicCommand

    /** Play a specific queue entry; only offered when [NowPlaying.canSkipToQueueItem]. */
    data class SkipToQueueItem(
        val queueItemId: Long,
    ) : MusicCommand
}
