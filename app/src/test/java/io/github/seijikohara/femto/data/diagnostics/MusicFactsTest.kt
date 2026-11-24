package io.github.seijikohara.femto.data.diagnostics

import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.NotStartedReason
import io.github.seijikohara.femto.data.music.ResumeOutcome
import io.github.seijikohara.femto.data.music.ResumeStep
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedState
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
