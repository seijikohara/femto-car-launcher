package io.github.seijikohara.femto.ui.diagnostics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.billing.BillingRepository
import io.github.seijikohara.femto.data.billing.ConnectionState
import io.github.seijikohara.femto.data.billing.Entitlement
import io.github.seijikohara.femto.data.billing.SubscriptionOffer
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.music.AudioSpectrumRepository
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicSessionRepository
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.data.system.DiagnosticsRepository
import io.github.seijikohara.femto.data.system.DiagnosticsSnapshot
import io.github.seijikohara.femto.data.system.PerformanceProbe
import io.github.seijikohara.femto.data.system.PerformanceSnapshot
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
 * (action-driven) merged with the live music-session state and billing state
 * (both flow-derived). Dependencies are plain seams so JVM tests drive every
 * transition without Android types.
 */
internal class DiagnosticsViewModel(
    private val collectSnapshot: suspend () -> DiagnosticsSnapshot,
    private val probeSpectrum: suspend () -> SpectrumDiagnosis,
    musicStateFlow: Flow<MusicCardState>,
    private val collectPerformance: suspend () -> PerformanceSnapshot? = { null },
    // Injected as Flows so tests never need the Play Billing SDK or an Application.
    billingEntitlement: Flow<Entitlement> = MutableStateFlow(Entitlement.Locked),
    billingOffers: Flow<List<SubscriptionOffer>> = MutableStateFlow(emptyList()),
    billingConnection: Flow<ConnectionState> = MutableStateFlow(ConnectionState.DISCONNECTED),
    // DEBUG-only flag flow; defaults to a constant false so tests not exercising
    // force-unlock never observe it. The real value comes from BillingRepository.
    billingDebugForceUnlocked: Flow<Boolean> = MutableStateFlow(false),
    // Suspend refresh call injected so the ViewModel never directly references
    // BillingRepository; the factory provides the real call, tests inject a lambda.
    private val onRefreshBilling: suspend () -> Unit = {},
    // Suspend setter for the DEBUG force-unlock flag; same injection rationale.
    private val onSetDebugForceUnlocked: suspend (Boolean) -> Unit = {},
    // LaunchPurchase is NOT handled here: it requires a live Activity reference
    // (BillingRepository.launchPurchase takes an Activity). The action bubbles
    // up to the Route/Sheet/MainActivity where the Activity is reachable.
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
            // Four billing flows combined into one BillingDiagnostics? projection.
            // Any failure degrades this field to null independently — a broken
            // billing SDK must not hide the permissions/network rows.
            combine(
                billingEntitlement,
                billingOffers,
                billingConnection,
                billingDebugForceUnlocked,
            ) { entitlement, offers, connection, debugForceUnlocked ->
                BillingDiagnostics(
                    mapboxUnlocked = entitlement.mapboxUnlocked,
                    lastVerified = entitlement.lastVerifiedAtMillis,
                    connection = connection,
                    offers = offers,
                    debugForceUnlocked = debugForceUnlocked,
                ) as BillingDiagnostics?
            }.catch { e ->
                if (e is CancellationException) throw e
                Log.e(TAG, "billing state flow failed", e)
                emit(null)
            },
        ) { probed, music, billing -> probed.copy(musicState = music, billing = billing) }
            .stateIn(viewModelScope, WhileUiSubscribed, DiagnosticsUiState.Initial)

    init {
        refresh()
    }

    fun onAction(action: DiagnosticsAction) =
        when (action) {
            DiagnosticsAction.Refresh -> {
                refresh()
            }

            DiagnosticsAction.RefreshBilling -> {
                viewModelScope.launch {
                    // Mirror the runCatchingOrNull pattern used by refresh(): a billing
                    // refresh failure must degrade silently (logcat only) rather than
                    // propagate an unhandled exception to the coroutine supervisor and
                    // crash the ViewModel's scope.
                    runCatching { onRefreshBilling() }
                        .onFailure {
                            if (it is CancellationException) throw it
                            Log.e(TAG, "billing refresh failed", it)
                        }
                }
            }

            // LaunchPurchase reaches here only as a routing marker; the actual
            // launch happens in MainActivity (it needs a live Activity reference).
            // The Route surfaces it via onLaunchPurchase so it never reaches the
            // ViewModel's own effect boundary.
            is DiagnosticsAction.LaunchPurchase -> {
                Unit
            }

            is DiagnosticsAction.SetDebugForceUnlocked -> {
                viewModelScope.launch {
                    runCatching { onSetDebugForceUnlocked(action.value) }
                        .onFailure {
                            if (it is CancellationException) throw it
                            Log.e(TAG, "set debug force-unlock failed", it)
                        }
                }
            }
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
            val performance = runCatchingOrNull("performance probe") { collectPerformance() }
            val snapshot = runCatchingOrNull("snapshot") { collectSnapshot() }
            probes.update {
                it.copy(
                    isLoading = false,
                    snapshot = snapshot,
                    spectrum = spectrum,
                    performance = performance,
                )
            }
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
            val billingRepository = BillingRepository.get(application)
            DiagnosticsViewModel(
                collectSnapshot = DiagnosticsRepository(application)::snapshot,
                probeSpectrum = AudioSpectrumRepository(application)::diagnose,
                musicStateFlow = MusicSessionRepository(application).stateFlow(),
                collectPerformance = PerformanceProbe(application)::snapshot,
                billingEntitlement = billingRepository.entitlement,
                billingOffers = billingRepository.offers,
                billingConnection = billingRepository.connection,
                billingDebugForceUnlocked = billingRepository.debugForceUnlocked,
                onRefreshBilling = billingRepository::refresh,
                onSetDebugForceUnlocked = billingRepository::setDebugForceUnlocked,
            )
        }
    }
