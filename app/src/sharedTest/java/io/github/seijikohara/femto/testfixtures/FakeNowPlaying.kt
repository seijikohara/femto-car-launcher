package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.QueueEntry
import io.github.seijikohara.femto.data.music.RepeatMode

internal fun fakeNowPlaying(
    title: String = "Strobe",
    artist: String? = "deadmau5",
    album: String? = "For Lack of a Better Name",
    isPlaying: Boolean = true,
    positionMs: Long = 232_000L,
    durationMs: Long = 632_000L,
    packageName: String = "com.spotify.music",
    canSeek: Boolean = false,
    canShuffle: Boolean = false,
    canRepeat: Boolean = false,
    canSkipToQueueItem: Boolean = false,
    shuffleOn: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.NONE,
    queue: List<QueueEntry> = emptyList(),
): NowPlaying =
    NowPlaying(
        title = title,
        artist = artist,
        album = album,
        albumArt = null,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        packageName = packageName,
        canSeek = canSeek,
        canShuffle = canShuffle,
        canRepeat = canRepeat,
        canSkipToQueueItem = canSkipToQueueItem,
        shuffleOn = shuffleOn,
        repeatMode = repeatMode,
        queue = queue,
    )

/**
 * A track whose title, artist and album each outrun the column they land in, on
 * the compact card and in the full-screen player alike. The fixture for the
 * scroll-vs-ellipsis gate: with the default [fakeNowPlaying] strings nothing
 * overflows, so both branches would render identically and prove nothing.
 */
internal fun fakeOverflowingNowPlaying(): NowPlaying =
    fakeNowPlaying(
        title = "The Song With An Extremely Long Title That Overflows",
        artist = "A Very Long Featured Artist Collaboration",
        album = "An Album Title That Runs Well Past The Card Edge",
    )
