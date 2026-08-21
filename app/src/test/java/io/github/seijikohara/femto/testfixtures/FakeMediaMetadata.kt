package io.github.seijikohara.femto.testfixtures

import android.graphics.Bitmap
import android.media.MediaMetadata

/**
 * Build a real platform [MediaMetadata] carrying only the keys the caller
 * names. An omitted key stays absent rather than empty, which is how a sparse
 * session — a podcast or a radio stream — publishes it.
 */
internal fun fakeMediaMetadata(
    title: String? = null,
    displayTitle: String? = null,
    artist: String? = null,
    album: String? = null,
    durationMs: Long = 0L,
    albumArt: Bitmap? = null,
    art: Bitmap? = null,
): MediaMetadata =
    MediaMetadata
        .Builder()
        .apply {
            title?.let { putString(MediaMetadata.METADATA_KEY_TITLE, it) }
            displayTitle?.let { putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, it) }
            artist?.let { putString(MediaMetadata.METADATA_KEY_ARTIST, it) }
            album?.let { putString(MediaMetadata.METADATA_KEY_ALBUM, it) }
            putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            albumArt?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
            art?.let { putBitmap(MediaMetadata.METADATA_KEY_ART, it) }
        }.build()
