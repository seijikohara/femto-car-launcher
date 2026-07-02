package io.github.seijikohara.femto.data.music

import androidx.compose.ui.graphics.ImageBitmap

internal data class NowPlaying(
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArt: ImageBitmap?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val packageName: String,
    val playbackSpeed: Float = 1f,
    /**
     * `SystemClock.elapsedRealtime()` basis captured from
     * [android.media.session.PlaybackState.getLastPositionUpdateTime]. The UI
     * interpolates the live position by adding the elapsed wall-clock time since
     * this basis (scaled by [playbackSpeed]) to [positionMs] while playing.
     */
    val positionUpdateTimeMs: Long = 0L,
    /**
     * The source app's launcher icon, resolved from [packageName]; null when it
     * cannot be resolved (uninstalled / restricted). The card shows it top-right
     * as a tap-to-open affordance.
     */
    val sourceIcon: ImageBitmap? = null,
    /** True when the session advertises ACTION_SEEK_TO; gates drag-to-seek. */
    val canSeek: Boolean = false,
    /** True when the media3 controller reports COMMAND_SET_SHUFFLE_MODE available. */
    val canShuffle: Boolean = false,
    /** True when the media3 controller reports COMMAND_SET_REPEAT_MODE available. */
    val canRepeat: Boolean = false,
    /** True when the session advertises ACTION_SKIP_TO_QUEUE_ITEM. */
    val canSkipToQueueItem: Boolean = false,
    /** Current shuffle toggle, read via the media3 controller (no platform getter exists). */
    val shuffleOn: Boolean = false,
    /** Current repeat mode, read via the media3 controller (no platform getter exists). */
    val repeatMode: RepeatMode = RepeatMode.NONE,
    /** Upcoming tracks after the active queue item, capped at [QUEUE_UPCOMING_LIMIT]. */
    val queue: List<QueueEntry> = emptyList(),
)

/**
 * Track identity for change-driven animation (the art dissolve and the
 * metadata cross-fade). Keys on the track fields, never the NowPlaying
 * instance, because the session re-wraps a fresh value on every playback tick.
 */
internal val NowPlaying.trackKey: String get() = "$packageName $title $album"

/**
 * Map a media-session package name to a human-readable source label. This is the
 * SSOT shared by the repository (title fallback for blank-title sessions) and the
 * music card eyebrow, so the two never drift apart.
 */
internal fun sourceLabel(packageName: String): String =
    when {
        packageName.contains("spotify", ignoreCase = true) -> "Spotify"
        packageName.contains("apple", ignoreCase = true) -> "Apple Music"
        packageName.contains("youtube", ignoreCase = true) -> "YouTube Music"
        packageName.contains("amazon", ignoreCase = true) -> "Amazon Music"
        else -> "Now playing"
    }
