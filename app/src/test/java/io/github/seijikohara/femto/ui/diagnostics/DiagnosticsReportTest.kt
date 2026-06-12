package io.github.seijikohara.femto.ui.diagnostics

import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.testfixtures.fakeDiagnosticsSnapshot
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DiagnosticsReportTest {
    @Test
    fun `report carries permission states with denied flagged loudly`() {
        val report =
            diagnosticsReport(
                DiagnosticsUiState(isLoading = false, snapshot = fakeDiagnosticsSnapshot()),
            )
        assertTrue(report.contains("ACCESS_FINE_LOCATION: granted"))
        assertTrue(report.contains("RECORD_AUDIO: DENIED"))
    }

    @Test
    fun `report names the playing session and the probe verdict`() {
        val report =
            diagnosticsReport(
                DiagnosticsUiState(
                    isLoading = false,
                    snapshot = fakeDiagnosticsSnapshot(),
                    spectrum = SpectrumDiagnosis.SILENT,
                    musicState = MusicCardState.Playing(fakeNowPlaying()),
                ),
            )
        assertTrue(report.contains("com.spotify.music (playing)"))
        assertTrue(report.contains("Spectrum capture: SILENT"))
    }

    @Test
    fun `report includes the log tail with its count`() {
        val report =
            diagnosticsReport(
                DiagnosticsUiState(isLoading = false, snapshot = fakeDiagnosticsSnapshot()),
            )
        assertTrue(report.contains("Recent warnings (1)"))
        assertTrue(report.contains("sample warning"))
    }

    @Test
    fun `a missing snapshot is reported explicitly`() {
        val report = diagnosticsReport(DiagnosticsUiState(isLoading = false, snapshot = null))
        assertTrue(report.contains("Snapshot UNAVAILABLE"))
    }
}
