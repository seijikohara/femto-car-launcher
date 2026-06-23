package io.github.seijikohara.femto.ui.upsell

import io.github.seijikohara.femto.data.billing.SubscriptionOffer

internal data class UpsellUiState(
    val offers: List<SubscriptionOffer>,
    val connected: Boolean,
    val mapboxUnlocked: Boolean,
) {
    companion object {
        val Initial: UpsellUiState = UpsellUiState(
            offers = emptyList(),
            connected = false,
            mapboxUnlocked = false,
        )
    }
}

internal sealed interface UpsellAction {
    // Routing marker: the Route dispatches this to onLaunchPurchase (which needs
    // a live Activity) — the ViewModel never sees it.
    data class Subscribe(
        val offerToken: String,
    ) : UpsellAction

    data object Retry : UpsellAction
}
