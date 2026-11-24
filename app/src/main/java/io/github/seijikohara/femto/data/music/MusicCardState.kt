package io.github.seijikohara.femto.data.music

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Discrete music card states. The dashboard renders different content per
 * variant rather than collapsing all empty cases to a single null `NowPlaying`,
 * so we can show a "needs permission" CTA, a "nothing playing" placeholder, the
 * track that played last, or the live track without conflating them.
 */
internal sealed interface MusicCardState {
    data object NeedsPermission : MusicCardState

    data object NoActiveSession : MusicCardState

    /**
     * No session is playing or paused, and [track] is the one that played last: the card offers to resume it in its
     * app, named [appName] as installed. [appIcon] is that app's icon, null until resolved or when it cannot be.
     * [resuming] while an attempt to resume it runs.
     */
    data class LastPlayed(
        val track: LastPlayedTrack,
        val appName: String,
        val appIcon: ImageBitmap? = null,
        val resuming: Boolean = false,
    ) : MusicCardState

    data class Playing(
        val nowPlaying: NowPlaying,
    ) : MusicCardState
}
