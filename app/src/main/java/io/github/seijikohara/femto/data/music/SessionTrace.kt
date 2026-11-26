package io.github.seijikohara.femto.data.music

import android.media.MediaMetadata
import android.os.SystemClock

/**
 * How many entries each record of a [SessionTrace] keeps, the session snapshots and, separately, the card entries; an
 * older one is dropped as a new one arrives.
 */
internal const val SESSION_TRACE_SNAPSHOTS = 40

/**
 * How many sessions, and separately how many tracks, a [SessionTrace] remembers the number of. The least recently seen
 * is forgotten first; seen again, it gets a new number.
 */
internal const val SESSION_TRACE_LOOKUP_LIMIT = 256

/**
 * The fields that tell one track of an app from another: the title and the artist, which a session's metadata, the card
 * and the remembered track all carry (the remembered track has no album). Held in memory only, to number the track:
 * the trace's records carry the number, never these fields, so they never reach a log or the diagnostics report.
 * [toString] prints neither field, so a log line or an exception message that mentions an identity cannot leak them
 * either.
 */
internal data class TrackIdentity(
    val title: String?,
    val artist: String?,
) {
    override fun toString(): String = "TrackIdentity(title and artist withheld)"
}

/**
 * Return the identity of the track titled [title] by [artist]; a blank artist reads as none, as the remembered track's
 * does.
 */
internal fun trackIdentityOf(
    title: String?,
    artist: String?,
): TrackIdentity = TrackIdentity(title = title, artist = artist?.takeIf { it.isNotBlank() })

/**
 * Return the identity of the track this metadata describes. The title follows the card's own rule ([cardTitleOrNull]),
 * so a session and the card agree on which track it is.
 */
internal fun MediaMetadata.trackIdentity(): TrackIdentity =
    trackIdentityOf(title = cardTitleOrNull(), artist = getString(MediaMetadata.METADATA_KEY_ARTIST))

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
 * The active sessions in the order the session watch held them, which of them the watch selected ([cardSession], the
 * number of the session the watch selected as primary, or null for none), and when the watch read them. The selected
 * session is not always what the card shows: the card shows it only when it publishes a track and notification access
 * holds. What the card finally shows is the [CardEntry] record.
 */
internal data class SessionSnapshot(
    val sessions: List<TracedSession>,
    val cardSession: Int?,
    val elapsedRealtimeMs: Long,
) {
    /** Return whether [other] reads the same sessions, whenever it was taken. */
    fun sameReadingAs(other: SessionSnapshot): Boolean = sessions == other.sessions && cardSession == other.cardSession
}

/** What the music card can show, as [ShownCard] and [CardEntry] record it. */
internal enum class CardKind {
    /** A live session's track, playing or paused. */
    PLAYING,

    /** The remembered track's resume offer, shown while no session is live. */
    RESUME_OFFERED,

    /** The empty card. */
    NOTHING_PLAYING,

    /** The prompt to grant notification access. */
    NO_ACCESS,
}

/**
 * What the card shows, before [SessionTrace] numbers the track: its [kind], the app's [packageName] where there is
 * one, and the [track] where there is one.
 */
internal data class ShownCard(
    val kind: CardKind,
    val packageName: String? = null,
    val track: TrackIdentity? = null,
)

/**
 * Return what this card state shows. The title of a live track is the card's own, which falls back to the app's name
 * when the session names no track, so a session without a title and the card showing it differ in their track number.
 */
internal fun MusicCardState.shown(): ShownCard =
    when (this) {
        is MusicCardState.Playing -> {
            ShownCard(CardKind.PLAYING, nowPlaying.packageName, trackIdentityOf(nowPlaying.title, nowPlaying.artist))
        }

        is MusicCardState.LastPlayed -> {
            ShownCard(CardKind.RESUME_OFFERED, track.packageName, trackIdentityOf(track.title, track.artist))
        }

        MusicCardState.NoActiveSession -> {
            ShownCard(CardKind.NOTHING_PLAYING)
        }

        MusicCardState.NeedsPermission -> {
            ShownCard(CardKind.NO_ACCESS)
        }
    }

/**
 * One change of what the card shows, as [SessionTrace] recorded it at [elapsedRealtimeMs]: the [kind], the app's
 * [packageName] where there is one, and the number of the [track] where there is one. The track number is the one the
 * session snapshots use for the same app's same track.
 */
internal data class CardEntry(
    val kind: CardKind,
    val packageName: String?,
    val track: Int?,
    val elapsedRealtimeMs: Long,
) {
    /** Return whether [other] shows the same, whenever it was recorded. */
    fun sameShowingAs(other: CardEntry): Boolean =
        kind == other.kind && packageName == other.packageName && track == other.track
}

/**
 * The write side of a [SessionTrace], the part the music session watch depends on. [SessionTrace] keeps what it is
 * given; [DiscardingSessionTrace] keeps nothing.
 */
internal interface SessionTraceSink {
    /**
     * Record [sessions] in the watch's order, with the one whose [ObservedSession.sessionKey] is [cardSessionKey]
     * selected (null for none).
     */
    fun record(
        sessions: List<ObservedSession>,
        cardSessionKey: Any?,
    )

    /** Record what the card shows now, the card state after the remembered track joined it. */
    fun recordCard(shown: ShownCard)
}

/**
 * A [SessionTraceSink] that keeps nothing, for a watch that must not write the process-wide trace: the diagnostics
 * screen builds a second repository for its live `Session` fact, and two watches writing one trace could hold the
 * priority order differently for a moment and alternate snapshots, pushing the history the screen is there to show out
 * of the bounded record.
 */
internal object DiscardingSessionTrace : SessionTraceSink {
    override fun record(
        sessions: List<ObservedSession>,
        cardSessionKey: Any?,
    ) = Unit

    override fun recordCard(shown: ShownCard) = Unit
}

/**
 * An in-memory record of what the music card's session watch saw and what the card finally showed, for the MUSIC
 * diagnostics section. Two records, each the last [SESSION_TRACE_SNAPSHOTS] changes, oldest first: the session
 * snapshots ([snapshots]) and the card entries ([cardEntries]). A reading equal to the last entry of its record is
 * dropped, so a playback position tick, which changes neither a state nor a track, writes nothing.
 *
 * Tracks and sessions appear as numbers in the order this process first saw them (see [SESSION_TRACE_LOOKUP_LIMIT]),
 * which shows a reader that the card went back to an earlier track without naming any track. Both records share the
 * track numbering, so a session's track and the same track offered for resume carry the same number.
 *
 * The session watch writes on the main thread and the card flow on another, and the diagnostics collector reads on yet
 * another, so every access holds the trace's monitor.
 */
internal class SessionTrace(
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
    lookupLimit: Int = SESSION_TRACE_LOOKUP_LIMIT,
) : SessionTraceSink {
    private val sessionNumbers = Ordinals<Any>(lookupLimit)
    private val trackNumbers = Ordinals<Pair<String, TrackIdentity>>(lookupLimit)
    private val recordedSessions = ArrayDeque<SessionSnapshot>()
    private val recordedCards = ArrayDeque<CardEntry>()

    @Synchronized
    override fun record(
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
        if (recordedSessions.lastOrNull()?.sameReadingAs(snapshot) == true) return
        recordedSessions.addLast(snapshot)
        if (recordedSessions.size > SESSION_TRACE_SNAPSHOTS) recordedSessions.removeFirst()
    }

    @Synchronized
    override fun recordCard(shown: ShownCard) {
        val entry =
            CardEntry(
                kind = shown.kind,
                packageName = shown.packageName,
                track = shown.track?.let { trackNumbers.numberOf(shown.packageName.orEmpty() to it) },
                elapsedRealtimeMs = elapsedRealtimeMs(),
            )
        if (recordedCards.lastOrNull()?.sameShowingAs(entry) == true) return
        recordedCards.addLast(entry)
        if (recordedCards.size > SESSION_TRACE_SNAPSHOTS) recordedCards.removeFirst()
    }

    /** Return the recorded session snapshots, oldest first. */
    @Synchronized
    fun snapshots(): List<SessionSnapshot> = recordedSessions.toList()

    /** Return the recorded card entries, oldest first. */
    @Synchronized
    fun cardEntries(): List<CardEntry> = recordedCards.toList()

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
