package io.github.seijikohara.femto.ui.diagnostics

import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.data.system.DiagnosticsSnapshot
import io.github.seijikohara.femto.data.system.PerformanceSnapshot

internal data class DiagnosticsUiState(
    val isLoading: Boolean = true,
    // null until the first collection lands (or when it failed — the screen
    // shows the unavailable row and the report says so explicitly).
    val snapshot: DiagnosticsSnapshot? = null,
    // null until the first probe completes; the probe is only meaningful
    // while music is playing, which the screen calls out.
    val spectrum: SpectrumDiagnosis? = null,
    val musicState: MusicCardState? = null,
    // null until the first collection lands; degrades independently like the
    // other probes.
    val performance: PerformanceSnapshot? = null,
) {
    companion object {
        val Initial: DiagnosticsUiState = DiagnosticsUiState()
    }
}

internal sealed interface DiagnosticsAction {
    data object Refresh : DiagnosticsAction
}
