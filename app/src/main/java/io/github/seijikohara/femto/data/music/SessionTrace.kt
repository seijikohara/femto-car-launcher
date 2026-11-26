package io.github.seijikohara.femto.data.music

import android.media.MediaMetadata
import android.os.SystemClock

/** How many snapshots a [SessionTrace] keeps; an older one is dropped as a new one arrives. */
internal const val SESSION_TRACE_SNAPSHOTS = 40

/**
 * How many sessions, and separately how many tracks, a [SessionTrace] remembers the number of. The least recently seen
 * is forgotten first; seen again, it gets a new number.
 */
internal const val SESSION_TRACE_LOOKUP_LIMIT = 256

/**
 * The fields that tell one track a session publishes from another. Held in memory only, to number the track: a
 * [SessionSnapshot] carries the number, never these fields, so they never reach a log or the diagnostics report.
 */
internal data class TrackIdentity(
    val title: String?,
    val artist: String?,
    val album: String?,
)

/**
 * Return the identity of the track this metadata describes. The title falls back to the display title, as the card's
 * does, because many streams publish only that one.
 */
internal fun MediaMetadata.trackIdentity(): TrackIdentity =
    TrackIdentity(
        title =
            getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
        artist = getString(MediaMetadata.METADATA_KEY_ARTIST),
        album = getString(MediaMetadata.METADATA_KEY_ALBUM),
    )

/**
 * One active session as the session watch read it, before [SessionTrace] numbers it. [sessionKey] tells sessions apart
 * (the session token on a device); [state] is the [android.media.session.PlaybackState] code, null when the session
 * reports none; [track] is null when the session publishes no metadata.
 */
internal data class ObservedSession(
    val sessionKey: Any,
    val packageName: String,
    val state: Int?,
    val track: TrackIdentity?,
)

/**
 * One session in a [SessionSnapshot]: its number, its app, its playback state code (null for none), and the number of
 * its track (null when it publishes no metadata). Numbers are ordinals within this process, so the same session or
 * track keeps its number from one snapshot to the next.
 */
internal data class TracedSession(
    val session: Int,
    val packageName: String,
    val state: Int?,
    val track: Int?,
)

/**
 * The active sessions in the order the session watch held them, which of them the card shows ([cardSession], the
 * number of the session the watch selected as primary, or null for none), and when the watch read them.
 */
internal data class SessionSnapshot(
    val sessions: List<TracedSession>,
    val cardSession: Int?,
    val elapsedRealtimeMs: Long,
) {
    /** Return whether [other] reads the same sessions, whenever it was taken. */
    fun sameReadingAs(other: SessionSnapshot): Boolean = sessions == other.sessions && cardSession == other.cardSession
}

/**
 * An in-memory record of what the music card's session watch saw, for the MUSIC diagnostics section: the last
 * [SESSION_TRACE_SNAPSHOTS] changes, oldest first. A reading equal to the last snapshot is dropped, so a playback
 * position tick, which changes neither a state nor a track, writes nothing.
 *
 * Tracks and sessions appear as numbers in the order this process first saw them (see [SESSION_TRACE_LOOKUP_LIMIT]),
 * which shows a reader that the card went back to an earlier track without naming any track.
 *
 * The session watch writes on the main thread and the diagnostics collector reads on another, so every access holds
 * the trace's monitor.
 */
internal class SessionTrace(
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
    lookupLimit: Int = SESSION_TRACE_LOOKUP_LIMIT,
) {
    private val sessionNumbers = Ordinals<Any>(lookupLimit)
    private val trackNumbers = Ordinals<Pair<String, TrackIdentity>>(lookupLimit)
    private val recorded = ArrayDeque<SessionSnapshot>()

    /**
     * Record [sessions] in the watch's order, with the one whose [ObservedSession.sessionKey] is [cardSessionKey] on
     * the card (null for none), unless the last snapshot already reads the same.
     */
    @Synchronized
    fun record(
        sessions: List<ObservedSession>,
        cardSessionKey: Any?,
    ) {
        val traced = sessions.map { it.traced() }
        val snapshot =
            SessionSnapshot(
                sessions = traced,
                cardSession = traced.getOrNull(sessions.indexOfFirst { it.sessionKey == cardSessionKey })?.session,
                elapsedRealtimeMs = elapsedRealtimeMs(),
            )
        if (recorded.lastOrNull()?.sameReadingAs(snapshot) == true) return
        recorded.addLast(snapshot)
        if (recorded.size > SESSION_TRACE_SNAPSHOTS) recorded.removeFirst()
    }

    /** Return the recorded snapshots, oldest first. */
    @Synchronized
    fun snapshots(): List<SessionSnapshot> = recorded.toList()

    // A track is the same track only within one app: two apps publishing the
    // same title are two sources the reader must be able to tell apart.
    private fun ObservedSession.traced(): TracedSession =
        TracedSession(
            session = sessionNumbers.numberOf(sessionKey),
            packageName = packageName,
            state = state,
            track = track?.let { trackNumbers.numberOf(packageName to it) },
        )

    companion object {
        /**
         * The trace of this process. The music card's session watch writes it by default, and the MUSIC diagnostics
         * section reads it. Session-scoped by design, like [ResumeAttempts].
         */
        val processWide = SessionTrace()
    }
}

/**
 * Numbers keys 1, 2, 3, … in the order they are first seen, remembering at most [limit] of them. The least recently
 * seen is forgotten first, so a key still in use keeps its number; a forgotten key seen again gets the next number.
 * Not thread-safe: [SessionTrace] calls it under its monitor.
 */
private class Ordinals<K : Any>(
    private val limit: Int,
) {
    private var last = 0

    // Access order, so a lookup counts as a sighting for the eviction.
    private val numbers =
        object : LinkedHashMap<K, Int>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Int>?): Boolean = size > limit
        }

    fun numberOf(key: K): Int = numbers.getOrPut(key) { ++last }

    private companion object {
        // java.util.LinkedHashMap's own defaults; the constructor that takes
        // the access order takes these too.
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
