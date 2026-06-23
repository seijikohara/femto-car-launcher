package io.github.seijikohara.femto.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "PlayBillingGateway"

// Milliseconds to wait for a single connection attempt before giving up.
private const val CONNECT_TIMEOUT_MS = 10_000L

// Real BillingClient gateway. Not unit-tested — on-device / integration tests cover this
// class, as the SDK embeds Android framework internals that cannot run on JVM.
internal class PlayBillingClientGateway(context: Context) : BillingClientGateway {
    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _purchaseUpdates = MutableSharedFlow<List<PurchaseRecord>>(extraBufferCapacity = 8)
    override val purchaseUpdates: Flow<List<PurchaseRecord>> = _purchaseUpdates

    // Cached for launchBillingFlow — populated on the first successful queryProductDetails.
    private var cachedProductDetails: com.android.billingclient.api.ProductDetails? = null

    private val billingClient: BillingClient =
        BillingClient.newBuilder(context)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    _purchaseUpdates.tryEmit(purchases.map { it.toRecord() })
                } else {
                    Log.w(TAG, "PurchasesUpdatedListener non-OK: ${result.debugMessage}")
                }
            }
            // enablePendingPurchases(PendingPurchasesParams) is required since v7;
            // enablePrepaidPlans() surfaces prepaid subscription purchases.
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()

    override suspend fun ensureConnected(): Boolean {
        if (_connection.value == ConnectionState.CONNECTED) return true
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                _connection.value = ConnectionState.CONNECTING
                billingClient.startConnection(
                    object : com.android.billingclient.api.BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                                _connection.value = ConnectionState.CONNECTED
                                cont.resume(true)
                            } else {
                                Log.w(TAG, "onBillingSetupFinished: ${result.debugMessage}")
                                _connection.value = ConnectionState.DISCONNECTED
                                cont.resume(false)
                            }
                        }

                        override fun onBillingServiceDisconnected() {
                            _connection.value = ConnectionState.DISCONNECTED
                            // BillingClient handles reconnection automatically on the next call;
                            // we surface the disconnected state but do not restart here to avoid
                            // busy-looping while the Play Store service is unavailable.
                            Log.d(TAG, "Billing service disconnected")
                        }
                    }
                )
            }
        } ?: run {
            Log.w(TAG, "ensureConnected timed out after ${CONNECT_TIMEOUT_MS}ms")
            false
        }
    }

    override suspend fun queryActivePurchases(): List<PurchaseRecord> =
        withContext(Dispatchers.IO) {
            val params =
                QueryPurchasesParams.newBuilder()
                    .setProductType(ProductType.SUBS)
                    .build()
            val result = billingClient.queryPurchasesAsync(params)
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryPurchasesAsync: ${result.billingResult.debugMessage}")
                return@withContext emptyList()
            }
            result.purchasesList.map { it.toRecord() }
        }

    override suspend fun queryOffers(productId: String): List<OfferRecord> =
        withContext(Dispatchers.IO) {
            val params =
                QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(ProductType.SUBS)
                                .build()
                        )
                    )
                    .build()
            val result = billingClient.queryProductDetails(params)
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails: ${result.billingResult.debugMessage}")
                return@withContext emptyList()
            }
            val details = result.productDetailsList?.firstOrNull() ?: return@withContext emptyList()
            cachedProductDetails = details
            details.subscriptionOfferDetails?.map { it.toRecord() } ?: emptyList()
        }

    override suspend fun acknowledge(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        val result = withContext(Dispatchers.IO) { billingClient.acknowledgePurchase(params) }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "acknowledgePurchase failed: ${result.debugMessage}")
        }
    }

    override fun launch(activity: Activity, offerToken: String) {
        val details = cachedProductDetails
        if (details == null) {
            Log.e(TAG, "launch() called before queryOffers(); ProductDetails not cached")
            return
        }
        val productDetailsParams =
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            )
        val flowParams =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParams)
                .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    // Map SDK Purchase to the SDK-free projection the rest of the billing package uses.
    private fun Purchase.toRecord() =
        PurchaseRecord(
            productIds = products,
            isAcknowledged = isAcknowledged,
            // purchaseState is Int; map to our sealed enum (UNSPECIFIED_STATE = 0,
            // PURCHASED = 1, PENDING = 2 in the SDK).
            state =
                when (purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
                    Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
                    else -> PurchaseState.UNSPECIFIED
                },
            purchaseToken = purchaseToken,
        )

    // Map SubscriptionOfferDetails to the SDK-free OfferRecord. isTrial is true
    // when any pricing phase has a zero priceAmountMicros (free-trial phase).
    private fun com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails.toRecord() =
        OfferRecord(
            basePlanId = basePlanId,
            offerToken = offerToken,
            formattedPrice =
                pricingPhases.pricingPhaseList
                    .lastOrNull { it.priceAmountMicros > 0 }
                    ?.formattedPrice
                    ?: pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice
                    ?: "",
            billingPeriod =
                pricingPhases.pricingPhaseList
                    .lastOrNull { it.priceAmountMicros > 0 }
                    ?.billingPeriod
                    ?: "",
            isTrial = pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L },
        )
}
