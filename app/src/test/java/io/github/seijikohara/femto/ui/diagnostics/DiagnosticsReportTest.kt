package io.github.seijikohara.femto.ui.diagnostics

import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.testfixtures.fakeDiagnosticsSnapshot
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakePerformanceSnapshot
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
        assertTrue(report.contains("| ACCESS_FINE_LOCATION | granted |"))
        assertTrue(report.contains("| RECORD_AUDIO | DENIED |"))
    }

    @Test
    fun `report is markdown with a title heading and a fenced log block`() {
        val report =
            diagnosticsReport(
                DiagnosticsUiState(isLoading = false, snapshot = fakeDiagnosticsSnapshot()),
            )
        assertTrue(report.startsWith("# Femto Car Launcher diagnostics"))
        assertTrue(report.contains("## Permissions"))
        assertTrue(report.contains("```text\n06-12 12:00:00.000 W/AudioSpectrumRepo: sample warning\n```"))
    }

    @Test
    fun `report renders the performance section with thermal and frame stats`() {
        val report =
            diagnosticsReport(
                DiagnosticsUiState(
                    isLoading = false,
                    snapshot = fakeDiagnosticsSnapshot(),
                    performance = fakePerformanceSnapshot(),
                ),
            )
        assertTrue(report.contains("## Performance"))
        assertTrue(report.contains("- Thermal: NONE (headroom 0.42)"))
        assertTrue(report.contains("- UI frames (120 sampled): median 16 ms, worst 48 ms, delayed 4%"))
        assertTrue(report.contains("- Uptime: process 2h 13m, device 5d 4h"))
        assertTrue(report.contains("## Map settings"))
        assertTrue(report.contains("- Map render mode: LIVE"))
    }

    @Test
    fun `a missing performance probe omits the section`() {
        val report =
            diagnosticsReport(
                DiagnosticsUiState(isLoading = false, snapshot = fakeDiagnosticsSnapshot()),
            )
        assertTrue(!report.contains("## Performance"))
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
