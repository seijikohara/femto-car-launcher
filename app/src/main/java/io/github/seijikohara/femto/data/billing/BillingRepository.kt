package io.github.seijikohara.femto.data.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Hot MutableStateFlow for entitlement and offers — NOT stateIn/WhileUiSubscribed,
// because these must survive UI unsubscribes for the process lifetime. The
// cache-seed collector keeps entitlement warm; the scope is never torn down.
internal class BillingRepository internal constructor(
    private val gateway: BillingClientGateway,
    private val store: BillingEntitlementStore,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    private val entitlementState = MutableStateFlow(Entitlement.Locked)
    val entitlement: StateFlow<Entitlement> = entitlementState.asStateFlow()

    private val offersState = MutableStateFlow<List<SubscriptionOffer>>(emptyList())
    val offers: StateFlow<List<SubscriptionOffer>> = offersState.asStateFlow()

    val connection: StateFlow<ConnectionState> = gateway.connection

    init {
        // Seed entitlement from DataStore so the gate never flickers on cold start;
        // the collector overwrites Locked the moment the first cached value arrives.
        scope.launch { store.cached.collect { entitlementState.value = it } }
        // React to PurchasesUpdatedListener callbacks (e.g. purchase completed in-app).
        scope.launch { gateway.purchaseUpdates.collect { applyPurchases(it) } }
        // Kick off an initial reconcile on construction; failures are silent (no crash
        // path needed here — the cached value from above already gated the UI correctly).
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        if (!gateway.ensureConnected()) return
        applyPurchases(gateway.queryActivePurchases())
        offersState.value = offersOf(gateway.queryOffers(FEMTO_PLUS_PRODUCT_ID))
    }

    private suspend fun applyPurchases(purchases: List<PurchaseRecord>) {
        // Acknowledge before computing entitlement: Play's 3-day window starts the
        // moment state transitions to PURCHASED, so we must ack as soon as we see it.
        unacknowledgedActiveTokens(purchases).forEach { gateway.acknowledge(it) }
        val computed = entitlementOf(purchases, now())
        entitlementState.value = computed
        store.cache(computed)
    }

    fun launchPurchase(activity: Activity, offerToken: String) =
        gateway.launch(activity, offerToken)

    companion object {
        @Volatile private var instance: BillingRepository? = null

        fun get(context: Context): BillingRepository =
            instance ?: synchronized(this) {
                instance
                    ?: BillingRepository(
                            gateway = PlayBillingClientGateway(context.applicationContext),
                            store = BillingPreferences(context.applicationContext),
                            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                            now = System::currentTimeMillis,
                        )
                        .also { instance = it }
            }
    }
}
