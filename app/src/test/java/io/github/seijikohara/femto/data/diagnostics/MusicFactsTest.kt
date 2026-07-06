package io.github.seijikohara.femto.data.diagnostics

import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
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
                    DiagnosticFact("Session", FactValue.Text("com.spotify.music (playing)")),
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
            DiagnosticFact("Session", FactValue.Text("com.spotify.music (paused)")),
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
