package io.github.seijikohara.femto.data.music

import androidx.media3.common.Player

/**
 * Repeat mode of the active media session, projected from the media3 Player
 * repeat-mode ints so `ui/` never touches player types. Shuffle and repeat
 * have no platform-API surface at all (verified via javap against the API 37
 * android.jar), and the androidx.media compat layer is deprecated — a media3
 * MediaController over the session's platform token is the sanctioned route.
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

/** Map the media3 Player repeat-mode int to [RepeatMode]. */
internal fun repeatModeOf(playerRepeatMode: Int): RepeatMode =
    when (playerRepeatMode) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.NONE
    }

internal fun RepeatMode.toPlayerMode(): Int =
    when (this) {
        RepeatMode.NONE -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }
