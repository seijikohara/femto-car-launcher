package io.github.seijikohara.femto.ui.diagnostics

import io.github.seijikohara.femto.data.billing.ConnectionState
import io.github.seijikohara.femto.data.billing.SubscriptionOffer
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.data.system.DiagnosticsSnapshot
import io.github.seijikohara.femto.data.system.PerformanceSnapshot

// UI model surfacing the current entitlement + Play Billing connection status in
// Diagnostics. Kept flat so each field maps to a single diagnostic row without
// additional transformation at the call site.
internal data class BillingDiagnostics(
    val mapboxUnlocked: Boolean,
    // null means the entitlement was seeded from the cached default only;
    // a non-null timestamp means a live Play reconcile succeeded at least once.
    val lastVerified: Long?,
    val connection: ConnectionState,
    val offers: List<SubscriptionOffer>,
    // DEBUG-only override: true when the developer has toggled force-unlock in
    // Diagnostics. Carried here so the toggle row can bind its checked state
    // directly from uiState.billing without reaching into a separate field.
    val debugForceUnlocked: Boolean = false,
)

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
    // null until the first billing flow emission arrives; degrades independently
    // so a billing-SDK failure never hides the other diagnostic rows.
    val billing: BillingDiagnostics? = null,
) {
    companion object {
        val Initial: DiagnosticsUiState = DiagnosticsUiState()
    }
}

internal sealed interface DiagnosticsAction {
    data object Refresh : DiagnosticsAction

    data object RefreshBilling : DiagnosticsAction

    // DEBUG-only: launch the Play billing flow for the given offer. Routed
    // through the Activity (MainActivity holds the BillingRepository reference)
    // because launchBillingFlow requires a live Activity, not an Application context.
    data class LaunchPurchase(
        val offerToken: String,
    ) : DiagnosticsAction

    // DEBUG-only: toggle the force-unlock override so developers can test the paid
    // Mapbox experience locally without a real Play subscription.
    data class SetDebugForceUnlocked(
        val value: Boolean,
    ) : DiagnosticsAction
}
