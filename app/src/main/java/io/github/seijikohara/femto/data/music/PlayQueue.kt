package io.github.seijikohara.femto.data.music

import android.support.v4.media.session.PlaybackStateCompat

/**
 * Repeat mode of the active media session, projected from the compat layer's
 * int constants so `ui/` never touches framework session types. Shuffle and
 * repeat have no platform-API surface at all (verified via javap against the
 * API 37 android.jar) — the compat layer is the only route, hence the
 * mapping helpers below.
 */
internal enum class RepeatMode { NONE, ALL, ONE }

/** The cycle the panel's repeat button steps through on each tap. */
internal fun nextRepeatMode(mode: RepeatMode): RepeatMode =
    when (mode) {
        RepeatMode.NONE -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.NONE
    }

/**
 * One upcoming track in the session queue — a UI-facing projection of
 * [android.media.session.MediaSession.QueueItem].
 */
internal data class QueueEntry(
    val id: Long,
    val title: String,
    val subtitle: String?,
)

/**
 * Cap on the "Playing next" slice so an unbounded session queue (a
 * thousands-long library shuffle) never floods the state flow or the panel.
 * Deliberate and documented rather than silent: entries beyond the cap are
 * reachable by skipping forward, just not listed.
 */
internal const val QUEUE_UPCOMING_LIMIT = 12

/**
 * Slice the raw session queue down to the entries after the active item —
 * the "Playing next" list. When the active id matches nothing (the session
 * did not report one), the head of the queue is taken as-is.
 */
internal fun upcomingQueue(
    entries: List<QueueEntry>,
    activeQueueItemId: Long,
    limit: Int = QUEUE_UPCOMING_LIMIT,
): List<QueueEntry> =
    entries
        .drop(entries.indexOfFirst { it.id == activeQueueItemId } + 1)
        .take(limit)

/** Map the compat shuffle-mode int to the panel's boolean toggle. */
internal fun isShuffleOn(compatShuffleMode: Int): Boolean =
    compatShuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
        compatShuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP

/** Map the compat repeat-mode int to [RepeatMode]; GROUP collapses to ALL. */
internal fun repeatModeOf(compatRepeatMode: Int): RepeatMode =
    when (compatRepeatMode) {
        PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.ONE
        PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatMode.ALL
        else -> RepeatMode.NONE
    }

internal fun RepeatMode.toCompatMode(): Int =
    when (this) {
        RepeatMode.NONE -> PlaybackStateCompat.REPEAT_MODE_NONE
        RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
        RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
    }

internal fun shuffleModeFor(on: Boolean): Int =
    if (on) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE
