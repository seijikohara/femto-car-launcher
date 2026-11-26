package io.github.seijikohara.femto.data.music

import android.media.session.PlaybackState
import io.github.seijikohara.femto.testfixtures.FAKE_RADIO_PACKAGE
import io.github.seijikohara.femto.testfixtures.FakeElapsedRealtime
import io.github.seijikohara.femto.testfixtures.fakeObservedSession
import io.github.seijikohara.femto.testfixtures.fakeSessionTrace
import io.github.seijikohara.femto.testfixtures.fakeTrackIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionTraceTest {
    private val clock = FakeElapsedRealtime()
    private val trace = fakeSessionTrace(clock)

    @Test
    fun `a track seen again keeps the number it got first`() {
        listOf("First", "Second", "First").forEach { title -> playOne(trace, title) }

        assertEquals(1, latestTrack(trace))
    }

    @Test
    fun `a new track gets the next number`() {
        listOf("First", "Second").forEach { title -> playOne(trace, title) }

        assertEquals(2, latestTrack(trace))
    }

    @Test
    fun `two sessions get different numbers`() {
        trace.record(
            sessions =
                listOf(
                    fakeObservedSession(sessionKey = "music"),
                    fakeObservedSession(sessionKey = "radio", packageName = FAKE_RADIO_PACKAGE),
                ),
            cardSessionKey = "music",
        )

        assertEquals(listOf(1, 2), trace.snapshots().single().sessions.map { it.session })
    }

    @Test
    fun `the snapshot names the session the card shows`() {
        trace.record(
            sessions =
                listOf(
                    fakeObservedSession(sessionKey = "music", state = PlaybackState.STATE_STOPPED),
                    fakeObservedSession(sessionKey = "radio", packageName = FAKE_RADIO_PACKAGE),
                ),
            cardSessionKey = "radio",
        )

        assertEquals(2, trace.snapshots().single().cardSession)
    }

    @Test
    fun `a snapshot with no session on the card names none`() {
        trace.record(sessions = listOf(fakeObservedSession(state = PlaybackState.STATE_STOPPED)), cardSessionKey = null)

        assertNull(trace.snapshots().single().cardSession)
    }

    @Test
    fun `a session without metadata has no track number`() {
        trace.record(sessions = listOf(fakeObservedSession(track = null)), cardSessionKey = "session-1")

        assertNull(latestTrack(trace))
    }

    @Test
    fun `a reading equal to the last one records nothing, as a playback position tick does`() {
        repeat(3) {
            clock.now += 1_000L
            trace.record(sessions = listOf(fakeObservedSession()), cardSessionKey = "session-1")
        }

        assertEquals(1, trace.snapshots().size)
    }

    @Test
    fun `a state change records a snapshot`() {
        listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED).forEach { state ->
            trace.record(sessions = listOf(fakeObservedSession(state = state)), cardSessionKey = "session-1")
        }

        assertEquals(2, trace.snapshots().size)
    }

    @Test
    fun `the card moving to another session records a snapshot`() {
        val sessions =
            listOf(
                fakeObservedSession(sessionKey = "music"),
                fakeObservedSession(sessionKey = "radio", packageName = FAKE_RADIO_PACKAGE),
            )
        listOf("music", "radio").forEach { card -> trace.record(sessions = sessions, cardSessionKey = card) }

        assertEquals(2, trace.snapshots().size)
    }

    @Test
    fun `the trace keeps only the newest snapshots`() {
        (0..SESSION_TRACE_SNAPSHOTS).forEach { index ->
            clock.now = index.toLong()
            val state = if (index % 2 == 0) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            trace.record(sessions = listOf(fakeObservedSession(state = state)), cardSessionKey = "session-1")
        }

        assertEquals((1L..SESSION_TRACE_SNAPSHOTS.toLong()).toList(), trace.snapshots().map { it.elapsedRealtimeMs })
    }

    @Test
    fun `a track evicted from the numbering table comes back with a new number`() {
        val small = fakeSessionTrace(clock, lookupLimit = 2)
        listOf("First", "Second", "Third", "First").forEach { title -> playOne(small, title) }

        assertEquals(4, latestTrack(small))
    }

    @Test
    fun `a track seen recently survives the numbering table's eviction`() {
        val small = fakeSessionTrace(clock, lookupLimit = 2)
        listOf("First", "Second", "First", "Third", "First").forEach { title -> playOne(small, title) }

        assertEquals(1, latestTrack(small))
    }

    // Record one playing session; the artist and album stay fixed, so the
    // title alone tells one track from another.
    private fun playOne(
        trace: SessionTrace,
        title: String,
    ) = trace.record(
        sessions = listOf(fakeObservedSession(track = fakeTrackIdentity(title = title))),
        cardSessionKey = "session-1",
    )

    private fun latestTrack(trace: SessionTrace): Int? =
        trace
            .snapshots()
            .last()
            .sessions
            .single()
            .track
}
