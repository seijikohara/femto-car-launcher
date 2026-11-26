package io.github.seijikohara.femto.data.diagnostics

import android.media.session.PlaybackState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.NotStartedReason
import io.github.seijikohara.femto.data.music.ResumeOutcome
import io.github.seijikohara.femto.data.music.ResumeStep
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.testfixtures.FAKE_RADIO_PACKAGE
import io.github.seijikohara.femto.testfixtures.FAKE_TRACE_TITLE
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedState
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeObservedSession
import io.github.seijikohara.femto.testfixtures.fakeSessionSnapshot
import io.github.seijikohara.femto.testfixtures.fakeSessionTrace
import io.github.seijikohara.femto.testfixtures.fakeTrackIdentity
import io.github.seijikohara.femto.testfixtures.fakeTracedSession
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicFactsTest {
    @Test
    fun `spectrumFacts reads an active capture as OK`() {
        assertEquals(
            listOf(DiagnosticFact("Spectrum capture", FactValue.Status("ACTIVE", FactHealth.OK))),
            spectrumFacts(SpectrumDiagnosis.ACTIVE),
        )
    }

    @Test
    fun `spectrumFacts reads a silent capture as plain info`() {
        assertEquals(
            listOf(DiagnosticFact("Spectrum capture", FactValue.Text("SILENT"))),
            spectrumFacts(SpectrumDiagnosis.SILENT),
        )
    }

    @Test
    fun `spectrumFacts flags an unavailable engine as a warning`() {
        assertEquals(
            listOf(DiagnosticFact("Spectrum capture", FactValue.Status("ENGINE_UNAVAILABLE", FactHealth.WARNING))),
            spectrumFacts(SpectrumDiagnosis.ENGINE_UNAVAILABLE),
        )
    }

    @Test
    fun `spectrumFacts flags a missing grant as a warning`() {
        assertEquals(
            listOf(DiagnosticFact("Spectrum capture", FactValue.Status("NO_PERMISSION", FactHealth.WARNING))),
            spectrumFacts(SpectrumDiagnosis.NO_PERMISSION),
        )
    }

    @Test
    fun `spectrumFacts reports a skipped probe as not probed`() {
        assertEquals(
            listOf(DiagnosticFact("Spectrum capture", FactValue.Text("not probed"))),
            spectrumFacts(null),
        )
    }

    @Test
    fun `musicSectionWithSession prepends a playing session onto the collected facts`() {
        val collected = SectionPayload.Facts(listOf(DiagnosticFact("Volume", FactValue.Text("7/15"))))

        assertEquals(
            SectionPayload.Facts(
                listOf(
                    DiagnosticFact("Session", FactValue.Text("com.example.music (playing)")),
                    DiagnosticFact("Volume", FactValue.Text("7/15")),
                ),
            ),
            musicSectionWithSession(collected, MusicCardState.Playing(fakeNowPlaying())),
        )
    }

    @Test
    fun `musicSectionWithSession describes a paused session as paused`() {
        val section =
            musicSectionWithSession(
                SectionPayload.Facts(emptyList()),
                MusicCardState.Playing(fakeNowPlaying(isPlaying = false)),
            ) as SectionPayload.Facts

        assertEquals(
            DiagnosticFact("Session", FactValue.Text("com.example.music (paused)")),
            section.facts.first(),
        )
    }

    @Test
    fun `musicSectionWithSession describes an idle session as no active session`() {
        val section =
            musicSectionWithSession(
                SectionPayload.Facts(emptyList()),
                MusicCardState.NoActiveSession,
            ) as SectionPayload.Facts

        assertEquals(
            DiagnosticFact("Session", FactValue.Text("no active session")),
            section.facts.first(),
        )
    }

    @Test
    fun `musicSectionWithSession names the app of a remembered track, never the track`() {
        val section =
            musicSectionWithSession(SectionPayload.Facts(emptyList()), fakeLastPlayedState()) as SectionPayload.Facts

        assertEquals(
            DiagnosticFact("Session", FactValue.Text("no active session (resume offered for com.example.music)")),
            section.facts.first(),
        )
    }

    @Test
    fun `resumeFact names the app and the step that started playback`() {
        assertEquals(
            DiagnosticFact("Last resume", FactValue.Text("com.example.music: started by the play media key")),
            resumeFact(ResumeOutcome.Started("com.example.music", ResumeStep.MEDIA_KEY)),
        )
    }

    @Test
    fun `resumeFact flags an attempt no step started as a warning`() {
        assertEquals(
            DiagnosticFact(
                "Last resume",
                FactValue.Status("com.example.music: no step started playback", FactHealth.WARNING),
            ),
            resumeFact(ResumeOutcome.NotStarted("com.example.music", NotStartedReason.NO_PLAYBACK)),
        )
    }

    @Test
    fun `resumeFact reports a gone app as plain info`() {
        assertEquals(
            DiagnosticFact("Last resume", FactValue.Text("com.example.music: app not installed or not launchable")),
            resumeFact(ResumeOutcome.NotStarted("com.example.music", NotStartedReason.APP_GONE)),
        )
    }

    @Test
    fun `resumeFact flags another app playing in the remembered app's place as a warning`() {
        assertEquals(
            DiagnosticFact(
                "Last resume",
                FactValue.Status("com.example.music: another app started playing", FactHealth.WARNING),
            ),
            resumeFact(ResumeOutcome.NotStarted("com.example.music", NotStartedReason.OTHER_APP_PLAYING)),
        )
    }

    @Test
    fun `resumeFact reports a left dashboard as plain info`() {
        assertEquals(
            DiagnosticFact(
                "Last resume",
                FactValue.Text("com.example.music: dashboard left before the app would have opened"),
            ),
            resumeFact(ResumeOutcome.NotStarted("com.example.music", NotStartedReason.LEFT_DASHBOARD)),
        )
    }

    @Test
    fun `resumeFact reports no attempt yet`() {
        assertEquals(
            DiagnosticFact("Last resume", FactValue.Text("none since the launcher started")),
            resumeFact(null),
        )
    }

    @Test
    fun `musicSectionWithSession flags a missing notification listener as a warning`() {
        val section =
            musicSectionWithSession(
                SectionPayload.Facts(emptyList()),
                MusicCardState.NeedsPermission,
            ) as SectionPayload.Facts

        assertEquals(
            DiagnosticFact("Session", FactValue.Status("notification listener not granted", FactHealth.WARNING)),
            section.facts.first(),
        )
    }

    @Test
    fun `musicSectionWithSession reports an unknown session state as unknown`() {
        val section =
            musicSectionWithSession(SectionPayload.Facts(emptyList()), musicState = null) as SectionPayload.Facts

        assertEquals(
            DiagnosticFact("Session", FactValue.Text("unknown")),
            section.facts.first(),
        )
    }

    @Test
    fun `musicSectionWithSession keeps a still-collecting section null`() {
        assertNull(musicSectionWithSession(collected = null, musicState = MusicCardState.NoActiveSession))
    }

    @Test
    fun `musicSectionWithSession leaves a failed section unavailable`() {
        assertEquals(
            SectionPayload.Unavailable,
            musicSectionWithSession(SectionPayload.Unavailable, MusicCardState.NoActiveSession),
        )
    }

    @Test
    fun `sessionsFact lists every session in order and marks the one on the card`() {
        assertEquals(
            DiagnosticFact(
                "Sessions",
                FactValue.Text(
                    "#1 com.example.music PLAYING, track 7, on the card; #2 com.example.radio PAUSED, track 2",
                ),
            ),
            sessionsFact(twoSessions()),
        )
    }

    @Test
    fun `sessionsFact says no track for a session that publishes no metadata`() {
        assertEquals(
            DiagnosticFact("Sessions", FactValue.Text("#1 com.example.music BUFFERING, no track, on the card")),
            sessionsFact(
                fakeSessionSnapshot(
                    sessions = listOf(fakeTracedSession(state = PlaybackState.STATE_BUFFERING, track = null)),
                ),
            ),
        )
    }

    @Test
    fun `sessionsFact says no state for a session that reports none`() {
        assertEquals(
            DiagnosticFact("Sessions", FactValue.Text("#1 com.example.music no state, track 7")),
            sessionsFact(fakeSessionSnapshot(sessions = listOf(fakeTracedSession(state = null)), cardSession = null)),
        )
    }

    @Test
    fun `sessionsFact reports a snapshot without an active session as none`() {
        assertEquals(
            DiagnosticFact("Sessions", FactValue.Text("none")),
            sessionsFact(fakeSessionSnapshot(sessions = emptyList(), cardSession = null)),
        )
    }

    @Test
    fun `sessionsFact reports no snapshot yet`() {
        assertEquals(
            DiagnosticFact("Sessions", FactValue.Text("none recorded since the launcher started")),
            sessionsFact(null),
        )
    }

    @Test
    fun `playbackStateName names every platform state in words`() {
        assertEquals(
            listOf(
                "NONE",
                "STOPPED",
                "PAUSED",
                "PLAYING",
                "FAST_FORWARDING",
                "REWINDING",
                "BUFFERING",
                "ERROR",
                "CONNECTING",
                "SKIPPING_TO_PREVIOUS",
                "SKIPPING_TO_NEXT",
                "SKIPPING_TO_QUEUE_ITEM",
            ),
            listOf(
                PlaybackState.STATE_NONE,
                PlaybackState.STATE_STOPPED,
                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_FAST_FORWARDING,
                PlaybackState.STATE_REWINDING,
                PlaybackState.STATE_BUFFERING,
                PlaybackState.STATE_ERROR,
                PlaybackState.STATE_CONNECTING,
                PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
                PlaybackState.STATE_SKIPPING_TO_NEXT,
                PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
            ).map(::playbackStateName),
        )
    }

    @Test
    fun `playbackStateName gives an unknown code by number`() {
        assertEquals("state 99", playbackStateName(99))
    }

    @Test
    fun `sessionHistoryFacts lists the snapshots oldest first with their age`() {
        assertEquals(
            listOf(
                DiagnosticFact(
                    "Session history -42.3 s",
                    FactValue.Text("#1 com.example.music PLAYING, track 7, on the card"),
                ),
                DiagnosticFact(
                    "Session history -5.0 s",
                    FactValue.Text(
                        "#1 com.example.music PLAYING, track 7, on the card; #2 com.example.radio PAUSED, track 2",
                    ),
                ),
            ),
            sessionHistoryFacts(
                snapshots =
                    listOf(
                        fakeSessionSnapshot(elapsedRealtimeMs = 7_700L),
                        twoSessions(elapsedRealtimeMs = 45_000L),
                    ),
                nowElapsedRealtimeMs = 50_000L,
            ),
        )
    }

    @Test
    fun `sessionHistoryFacts reports no snapshot yet`() {
        assertEquals(
            listOf(DiagnosticFact("Session history", FactValue.Text("none recorded since the launcher started"))),
            sessionHistoryFacts(snapshots = emptyList(), nowElapsedRealtimeMs = 50_000L),
        )
    }

    @Test
    fun `the session facts never carry the title, artist or album`() {
        val track = fakeTrackIdentity()
        val trace = fakeSessionTrace()
        trace.record(sessions = listOf(fakeObservedSession(track = track)), cardSessionKey = "session-1")
        val printed =
            (listOf(sessionsFact(trace.snapshots().last())) + sessionHistoryFacts(trace.snapshots(), 0L))
                .joinToString("\n") { fact -> "${fact.label}: ${(fact.value as FactValue.Text).value}" }

        assertTrue(
            listOfNotNull(FAKE_TRACE_TITLE, track.artist, track.album).none { it in printed },
            "the facts print a track field: $printed",
        )
    }

    // The two-session example: the music app plays track 7 on the card while
    // the radio app sits paused on track 2.
    private fun twoSessions(elapsedRealtimeMs: Long = 0L) =
        fakeSessionSnapshot(
            sessions =
                listOf(
                    fakeTracedSession(),
                    fakeTracedSession(
                        session = 2,
                        packageName = FAKE_RADIO_PACKAGE,
                        state = PlaybackState.STATE_PAUSED,
                        track = 2,
                    ),
                ),
            cardSession = 1,
            elapsedRealtimeMs = elapsedRealtimeMs,
        )
}
