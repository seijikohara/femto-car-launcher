package io.github.seijikohara.femto.ui.upsell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun UpsellRoute(
    onPurchaseComplete: () -> Unit,
    // Bubbles up to the Activity because BillingRepository.launchPurchase
    // needs a live Activity reference; neither the ViewModel nor the Route
    // can provide one.
    onLaunchPurchase: (offerToken: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: UpsellViewModel = viewModel(factory = UpsellViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // rememberUpdatedState captures the latest lambda so the LaunchedEffect
    // key can stay on the state value without referencing a captured-by-restart
    // lambda directly — avoids the ktlint compose:lambda-param-in-effect finding.
    val currentOnPurchaseComplete by rememberUpdatedState(onPurchaseComplete)

    // A completed purchase flips mapboxUnlocked; dismiss the sheet automatically
    // so the user is not left on the paywall after subscribing.
    LaunchedEffect(uiState.mapboxUnlocked) {
        if (uiState.mapboxUnlocked) currentOnPurchaseComplete()
    }

    UpsellScreen(
        uiState = uiState,
        onAction = { action ->
            // Subscribe must not reach the ViewModel — it requires a live Activity.
            // Route it to the caller which has access to onLaunchPurchase.
            when (action) {
                is UpsellAction.Subscribe -> onLaunchPurchase(action.offerToken)
                else -> viewModel.onAction(action)
            }
        },
        modifier = modifier,
    )
}
