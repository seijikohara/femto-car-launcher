package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.music.NowPlaying

internal fun fakeNowPlaying(
    title: String = "Strobe",
    artist: String? = "deadmau5",
    album: String? = "For Lack of a Better Name",
    isPlaying: Boolean = true,
    positionMs: Long = 232_000L,
    durationMs: Long = 632_000L,
    packageName: String = "com.spotify.music",
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
    )
