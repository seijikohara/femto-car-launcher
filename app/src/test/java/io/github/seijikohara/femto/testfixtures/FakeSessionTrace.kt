package io.github.seijikohara.femto.testfixtures

import android.media.session.PlaybackState
import io.github.seijikohara.femto.data.music.CardEntry
import io.github.seijikohara.femto.data.music.CardKind
import io.github.seijikohara.femto.data.music.ObservedSession
import io.github.seijikohara.femto.data.music.SESSION_TRACE_LOOKUP_LIMIT
import io.github.seijikohara.femto.data.music.SessionSnapshot
import io.github.seijikohara.femto.data.music.SessionTrace
import io.github.seijikohara.femto.data.music.SessionTraceSink
import io.github.seijikohara.femto.data.music.ShownCard
import io.github.seijikohara.femto.data.music.TracedSession
import io.github.seijikohara.femto.data.music.TrackIdentity
import java.util.concurrent.atomic.AtomicInteger

/**
 * A title no wording of the diagnostics could produce by itself, so a test that searches the formatted facts for it
 * finds it only if the title leaked.
 */
internal const val FAKE_TRACE_TITLE = "Quartz Lantern Overture"

/** The second package of a two-session trace, beside [FAKE_MUSIC_PACKAGE]. */
internal const val FAKE_RADIO_PACKAGE = "com.example.radio"

/** The artist of the default [fakeTrackIdentity], likewise absent from the wording of the diagnostics. */
internal const val FAKE_TRACE_ARTIST = "Harbor Lights"

/** A track as a session publishes it; vary [title] to make another track. */
internal fun fakeTrackIdentity(
    title: String? = FAKE_TRACE_TITLE,
    artist: String? = FAKE_TRACE_ARTIST,
): TrackIdentity = TrackIdentity(title = title, artist = artist)

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

/** What the card shows, for [SessionTrace.recordCard]; [track] defaults to none, as for the cards without one. */
internal fun fakeShownCard(
    kind: CardKind = CardKind.PLAYING,
    packageName: String? = FAKE_MUSIC_PACKAGE,
    track: TrackIdentity? = fakeTrackIdentity(),
): ShownCard = ShownCard(kind = kind, packageName = packageName, track = track)

/** One recorded card entry, for the formatting tests. */
internal fun fakeCardEntry(
    kind: CardKind = CardKind.PLAYING,
    packageName: String? = FAKE_MUSIC_PACKAGE,
    track: Int? = 7,
    elapsedRealtimeMs: Long = 0L,
): CardEntry = CardEntry(kind = kind, packageName = packageName, track = track, elapsedRealtimeMs = elapsedRealtimeMs)

/** A [SessionTraceSink] whose every write fails, to show the card survives a broken trace; [writes] counts the attempts. */
internal class ThrowingSessionTrace : SessionTraceSink {
    // Written from Main and from Default, read from the test thread.
    private val attempts = AtomicInteger()

    val writes: Int get() = attempts.get()

    override fun record(
        sessions: List<ObservedSession>,
        cardSessionKey: Any?,
    ) = fail()

    override fun recordCard(shown: ShownCard) = fail()

    private fun fail(): Nothing {
        attempts.incrementAndGet()
        error("the trace is broken")
    }
}
