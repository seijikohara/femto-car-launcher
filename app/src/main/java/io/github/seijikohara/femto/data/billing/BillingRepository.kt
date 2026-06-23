package io.github.seijikohara.femto.data.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Hot MutableStateFlow for entitlement and offers — NOT stateIn/WhileUiSubscribed,
// because these must survive UI unsubscribes for the process lifetime.
// The scope is never torn down.
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

    // applyPurchases is called from two concurrent sites: the purchaseUpdates collector
    // (process-lifetime background coroutine) and refresh() (which can be called from
    // any coroutine, including from the init block). Without serialization, the two
    // invocations can interleave: one call may acknowledge a token while the other is
    // mid-computation, producing a stale entitlement that overwrites the correct one.
    // The Mutex ensures each invocation runs atomically end-to-end.
    private val applyMutex = Mutex()

    init {
        scope.launch {
            // Seed last-known entitlement FIRST so the gate shows a stable value on cold
            // start before refresh() completes. refresh() runs after the seed — a separate
            // launch for the reconcile would race here on Dispatchers.Default and could
            // overwrite a completed refresh with the stale cached value.
            entitlementState.value = store.cached.first()
            // Start the purchaseUpdates collector for the process lifetime before
            // reconciling: a purchase completed between seed and refresh would arrive
            // on this channel, and we must not miss it.
            scope.launch { gateway.purchaseUpdates.collect { applyPurchases(it) } }
            // Kick off an initial reconcile; failures are silent (the cached value
            // already gated the UI correctly).
            refresh()
        }
    }

    suspend fun refresh() {
        if (!gateway.ensureConnected()) return
        applyPurchases(gateway.queryActivePurchases())
        offersState.value = offersOf(gateway.queryOffers(FEMTO_PLUS_PRODUCT_ID))
    }

    private suspend fun applyPurchases(purchases: List<PurchaseRecord>) =
        applyMutex.withLock {
            // Acknowledge before computing entitlement: Play's 3-day window starts the
            // moment state transitions to PURCHASED, so we must ack as soon as we see it.
            unacknowledgedActiveTokens(purchases).forEach { gateway.acknowledge(it) }
            val computed = entitlementOf(purchases, now())
            entitlementState.value = computed
            store.cache(computed)
        }

    /**
     * Launch the Play billing flow for the given offer token.
     *
     * Returns true when the billing dialog was shown, false when offers have not yet been loaded
     * (i.e. [refresh] has not completed) — callers should treat false as a no-op precondition
     * failure rather than a billing error.
     */
    fun launchPurchase(
        activity: Activity,
        offerToken: String,
    ): Boolean = gateway.launch(activity, offerToken)

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
                    ).also { instance = it }
            }
    }
}
