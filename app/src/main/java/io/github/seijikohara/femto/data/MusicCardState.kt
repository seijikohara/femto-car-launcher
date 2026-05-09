package io.github.seijikohara.femto.data

/**
 * Discrete music card states. The dashboard renders different content per
 * variant rather than collapsing all empty cases to a single null `NowPlaying`,
 * so we can show a "needs permission" CTA, a "nothing playing" placeholder, or
 * the live track without conflating the three empty states.
 */
internal sealed interface MusicCardState {
    data object NeedsPermission : MusicCardState

    data object NoActiveSession : MusicCardState

    data class Playing(
        val nowPlaying: NowPlaying,
    ) : MusicCardState
}
