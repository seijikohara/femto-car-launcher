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
}
