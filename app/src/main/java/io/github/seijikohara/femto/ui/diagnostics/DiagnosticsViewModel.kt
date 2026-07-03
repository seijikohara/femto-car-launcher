package io.github.seijikohara.femto.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.SectionCollector
import io.github.seijikohara.femto.data.diagnostics.SectionId
import io.github.seijikohara.femto.data.diagnostics.SectionPayload
import io.github.seijikohara.femto.data.diagnostics.diagnosticsCollectors
import io.github.seijikohara.femto.data.diagnostics.musicSectionWithSession
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DiagnosticsViewModel"

// How long the Copy button confirms before reverting to its idle label.
internal const val COPY_CONFIRM_MS = 2_000L

/**
 * Owns the Diagnostics screen state: the section registry collected on demand
 * (action-driven, one child coroutine per section so rows stream in as they
 * land) merged with the live music-session state (flow-derived). Dependencies
 * are plain seams so JVM tests drive every transition without Android types.
 */
internal class DiagnosticsViewModel(
    private val collectors: List<SectionCollector>,
    musicStateFlow: Flow<MusicCardState>,
    private val copyToClipboard: suspend (String) -> Unit,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val payloads = MutableStateFlow(emptyPayloads())
    private val problemsOnly = MutableStateFlow(false)
    private val copyConfirmed = MutableStateFlow(false)
    private var collectionJob: Job? = null
    private var copyJob: Job? = null

    val uiState: StateFlow<DiagnosticsUiState> =
        combine(
            payloads,
            musicStateFlow.catch { e ->
                if (e is CancellationException) throw e
                // Degrade the Session fact, keep the rest of the report usable.
                Log.e(TAG, "music state flow failed", e)
                emit(MusicCardState.NoActiveSession)
            },
            problemsOnly,
            copyConfirmed,
        ) { collected, music, problems, confirmed ->
            DiagnosticsUiState(
                sections =
                    SectionId.entries.map { id ->
                        DiagnosticSection(
                            id = id,
                            payload =
                                if (id == SectionId.MUSIC) {
                                    musicSectionWithSession(collected[id], music)
                                } else {
                                    collected[id]
                                },
                        )
                    },
                problemsOnly = problems,
                copyConfirmed = confirmed,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, DiagnosticsUiState.Initial)

    init {
        refresh()
    }

    fun onAction(action: DiagnosticsAction) =
        when (action) {
            DiagnosticsAction.Refresh -> refresh()
            DiagnosticsAction.CopyReport -> copyReport()
            DiagnosticsAction.ToggleProblemsOnly -> problemsOnly.update { !it }
        }

    private fun refresh() {
        collectionJob?.cancel()
        payloads.value = emptyPayloads()
        collectionJob =
            viewModelScope.launch {
                // One child per section: a slow or wedged collector delays its
                // own row only, and every other section streams in around it.
                collectors.forEach { collector ->
                    launch {
                        val payload = collectOrUnavailable(collector)
                        payloads.update { it + (collector.id to payload) }
                    }
                }
            }
    }

    private fun copyReport() {
        // Cancel any pulse still in flight: without this, two CopyReport
        // actions inside one confirmation window race two coroutines on the
        // same flag, and the first one's trailing `= false` reverts the
        // label right after the second copy re-confirms it.
        copyJob?.cancel()
        copyJob =
            viewModelScope.launch {
                copyToClipboard(diagnosticsReport(uiState.value.sections, nowEpochMs()))
                copyConfirmed.value = true
                delay(COPY_CONFIRM_MS)
                copyConfirmed.value = false
            }
    }

    /**
     * Run one collector, degrading a failure to [SectionPayload.Unavailable]:
     * on the screen that exists to surface failure, the absence itself is the
     * datum — a broken collector must never hide its siblings' findings.
     */
    private suspend fun collectOrUnavailable(collector: SectionCollector): SectionPayload =
        runCatching { collector.collect() }
            .onFailure {
                // runCatching also traps cancellation; rethrow to keep
                // structured concurrency intact (AppDrawerViewModel precedent).
                if (it is CancellationException) throw it
                Log.e(TAG, "${collector.id} collection failed", it)
            }.getOrDefault(SectionPayload.Unavailable)
}

private fun emptyPayloads(): Map<SectionId, SectionPayload?> = SectionId.entries.associateWith { null }

/** Wires the production registry and clipboard without an UNCHECKED_CAST factory. */
internal val DiagnosticsViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            DiagnosticsViewModel(
                collectors = diagnosticsCollectors(application),
                musicStateFlow = MusicSessionRepository(application).stateFlow(),
                copyToClipboard = { text ->
                    // The platform clipboard expects main-thread writes.
                    withContext(Dispatchers.Main) {
                        application
                            .getSystemService<ClipboardManager>()
                            ?.setPrimaryClip(ClipData.newPlainText("Femto diagnostics", text))
                    }
                },
            )
        }
    }
