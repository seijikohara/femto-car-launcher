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
