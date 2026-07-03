package io.github.seijikohara.femto.ui.diagnostics

import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.SectionId

internal data class DiagnosticsUiState(
    // Always one section per SectionId, in enum order; a null payload means
    // that collector is still running (the streaming skeleton).
    val sections: List<DiagnosticSection>,
    val problemsOnly: Boolean = false,
    // True for a short pulse after a copy so the button can confirm.
    val copyConfirmed: Boolean = false,
) {
    companion object {
        val Initial: DiagnosticsUiState =
            DiagnosticsUiState(SectionId.entries.map { DiagnosticSection(it, null) })
    }
}

internal sealed interface DiagnosticsAction {
    data object Refresh : DiagnosticsAction

    data object CopyReport : DiagnosticsAction

    data object ToggleProblemsOnly : DiagnosticsAction
}
