package io.github.seijikohara.femto.testfixtures

import android.media.session.PlaybackState
import io.github.seijikohara.femto.data.music.ObservedSession
import io.github.seijikohara.femto.data.music.SESSION_TRACE_LOOKUP_LIMIT
import io.github.seijikohara.femto.data.music.SessionSnapshot
import io.github.seijikohara.femto.data.music.SessionTrace
import io.github.seijikohara.femto.data.music.TracedSession
import io.github.seijikohara.femto.data.music.TrackIdentity

/**
 * A title no wording of the diagnostics could produce by itself, so a test that searches the formatted facts for it
 * finds it only if the title leaked.
 */
internal const val FAKE_TRACE_TITLE = "Quartz Lantern Overture"

/** The second package of a two-session trace, beside [FAKE_MUSIC_PACKAGE]. */
internal const val FAKE_RADIO_PACKAGE = "com.example.radio"

/** A track as a session publishes it; vary [title] to make another track. */
internal fun fakeTrackIdentity(
    title: String? = FAKE_TRACE_TITLE,
    artist: String? = "Harbor Lights",
    album: String? = "Night Drive",
): TrackIdentity = TrackIdentity(title = title, artist = artist, album = album)

/** One active session as the session watch reads it; [track] null is a session that publishes no metadata. */
internal fun fakeObservedSession(
    sessionKey: Any = "session-1",
    packageName: String = FAKE_MUSIC_PACKAGE,
    state: Int? = PlaybackState.STATE_PLAYING,
    track: TrackIdentity? = fakeTrackIdentity(),
): ObservedSession = ObservedSession(sessionKey = sessionKey, packageName = packageName, state = state, track = track)

/** The elapsed-realtime clock a [fakeSessionTrace] reads; the test moves it by setting [now]. */
internal class FakeElapsedRealtime(
    var now: Long = 0L,
)

/** A fresh [SessionTrace] on [clock]; a small [lookupLimit] lets a test reach the numbering tables' bound. */
internal fun fakeSessionTrace(
    clock: FakeElapsedRealtime = FakeElapsedRealtime(),
    lookupLimit: Int = SESSION_TRACE_LOOKUP_LIMIT,
): SessionTrace = SessionTrace(elapsedRealtimeMs = { clock.now }, lookupLimit = lookupLimit)

/** One numbered session of a recorded snapshot, for the formatting tests. */
internal fun fakeTracedSession(
    session: Int = 1,
    packageName: String = FAKE_MUSIC_PACKAGE,
    state: Int? = PlaybackState.STATE_PLAYING,
    track: Int? = 7,
): TracedSession = TracedSession(session = session, packageName = packageName, state = state, track = track)

/** A recorded snapshot; [cardSession] is the number of the session the card shows, or null for none. */
internal fun fakeSessionSnapshot(
    sessions: List<TracedSession> = listOf(fakeTracedSession()),
    cardSession: Int? = 1,
    elapsedRealtimeMs: Long = 0L,
): SessionSnapshot =
    SessionSnapshot(sessions = sessions, cardSession = cardSession, elapsedRealtimeMs = elapsedRealtimeMs)
