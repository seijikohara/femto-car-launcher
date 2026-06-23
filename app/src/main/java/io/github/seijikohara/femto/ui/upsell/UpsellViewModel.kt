package io.github.seijikohara.femto.ui.upsell

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "UpsellViewModel"

/**
 * Merges the three billing flows into a single [UpsellUiState] projection.
 * Holds no Play Billing SDK objects and no Activity reference — purchase
 * launch is bridged through MainActivity via [UpsellAction.Subscribe].
 * Dependencies are plain flows and lambdas, so JVM unit tests drive every
 * transition without the Play Billing SDK or an Activity on the classpath.
 */
internal class UpsellViewModel(
    offers: Flow<List<SubscriptionOffer>>,
    entitlement: Flow<Entitlement>,
    connection: Flow<ConnectionState>,
    // Injected so the ViewModel never directly references BillingRepository;
    // the factory provides the real call, tests inject a lambda.
    private val onRefresh: suspend () -> Unit,
) : ViewModel() {
    val uiState: StateFlow<UpsellUiState> =
        combine(offers, connection, entitlement) { offerList, conn, ent ->
            UpsellUiState(
                offers = offerList,
                connected = conn == ConnectionState.CONNECTED,
                mapboxUnlocked = ent.mapboxUnlocked,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, UpsellUiState.Initial)

    fun onAction(action: UpsellAction) =
        when (action) {
            is UpsellAction.Subscribe -> {
                // Routing marker only — handled by the Route, which has access to
                // onLaunchPurchase and therefore a live Activity reference.
                Unit
            }

            UpsellAction.Retry -> {
                viewModelScope.launch {
                    // Mirror the runCatching pattern in DiagnosticsViewModel: a refresh
                    // failure must degrade silently (logcat only) rather than propagate to
                    // the coroutine supervisor and crash the ViewModel scope.
                    runCatching { onRefresh() }
                        .onFailure {
                            if (it is CancellationException) throw it
                            Log.e(TAG, "billing refresh failed", it)
                        }
                }
                Unit
            }
        }
}

/** Wires the production BillingRepository without an UNCHECKED_CAST factory. */
internal val UpsellViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            val repo = BillingRepository.get(application)
            UpsellViewModel(
                offers = repo.offers,
                entitlement = repo.entitlement,
                connection = repo.connection,
                onRefresh = repo::refresh,
            )
        }
    }
