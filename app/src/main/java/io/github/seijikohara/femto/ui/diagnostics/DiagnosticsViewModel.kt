package io.github.seijikohara.femto.ui.diagnostics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.music.AudioSpectrumRepository
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicSessionRepository
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.data.system.DiagnosticsRepository
import io.github.seijikohara.femto.data.system.DiagnosticsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DiagnosticsViewModel"

/**
 * Owns the Diagnostics screen state: an on-demand snapshot + spectrum probe
 * (action-driven) merged with the live music-session state (flow-derived).
 * Dependencies are plain seams so JVM tests drive every transition without
 * Android types.
 */
internal class DiagnosticsViewModel(
    private val collectSnapshot: suspend () -> DiagnosticsSnapshot,
    private val probeSpectrum: suspend () -> SpectrumDiagnosis,
    musicStateFlow: Flow<MusicCardState>,
) : ViewModel() {
    private val probes = MutableStateFlow(DiagnosticsUiState.Initial)

    val uiState: StateFlow<DiagnosticsUiState> =
        combine(
            probes,
            musicStateFlow.catch { e ->
                if (e is CancellationException) throw e
                // Degrade the row, keep the rest of the report usable.
                Log.e(TAG, "music state flow failed", e)
                emit(MusicCardState.NoActiveSession)
            },
        ) { probed, music -> probed.copy(musicState = music) }
            .stateIn(viewModelScope, WhileUiSubscribed, DiagnosticsUiState.Initial)

    init {
        refresh()
    }

    fun onAction(action: DiagnosticsAction) =
        when (action) {
            DiagnosticsAction.Refresh -> refresh()
        }

    private fun refresh() {
        probes.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // Each probe degrades to null independently so a broken collector
            // never hides the other one's findings — this screen exists to
            // surface failure, not to add its own silent variety. The spectrum
            // probe runs FIRST: its failure detail lands in logcat, and the
            // snapshot's log tail must be captured after it so the report
            // carries the exact Visualizer error rather than predating it.
            val spectrum = runCatchingOrNull("spectrum probe") { probeSpectrum() }
            val snapshot = runCatchingOrNull("snapshot") { collectSnapshot() }
            probes.update { it.copy(isLoading = false, snapshot = snapshot, spectrum = spectrum) }
        }
    }

    private inline fun <T> runCatchingOrNull(
        label: String,
        block: () -> T,
    ): T? =
        runCatching(block)
            .onFailure {
                // runCatching also traps cancellation; rethrow to keep
                // structured concurrency intact (AppDrawerViewModel precedent).
                if (it is CancellationException) throw it
                Log.e(TAG, "$label failed", it)
            }.getOrNull()
}

/** Wires the production repositories without an UNCHECKED_CAST factory. */
internal val DiagnosticsViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            DiagnosticsViewModel(
                collectSnapshot = DiagnosticsRepository(application)::snapshot,
                probeSpectrum = AudioSpectrumRepository(application)::diagnose,
                musicStateFlow = MusicSessionRepository(application).stateFlow(),
            )
        }
    }
