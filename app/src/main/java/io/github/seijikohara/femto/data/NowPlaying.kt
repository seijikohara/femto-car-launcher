package io.github.seijikohara.femto.data

import androidx.compose.ui.graphics.ImageBitmap

internal data class NowPlaying(
    val title: String,
    val artist: String?,
    val albumArt: ImageBitmap?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val packageName: String,
)
