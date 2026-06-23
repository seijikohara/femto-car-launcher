package io.github.seijikohara.femto.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// SDK-free projections so the entitlement logic and BillingRepository are
// testable without the Play Billing SDK or a device.
internal enum class PurchaseState { PURCHASED, PENDING, UNSPECIFIED }

internal data class PurchaseRecord(
    val productIds: List<String>,
    val isAcknowledged: Boolean,
    val state: PurchaseState,
    val purchaseToken: String,
)

internal data class OfferRecord(
    val basePlanId: String,
    val offerToken: String,
    val formattedPrice: String,
    val billingPeriod: String,
    val isTrial: Boolean,
)

internal enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

// The seam over BillingClient. The real impl wraps the Play SDK; tests inject a fake.
// launch() returns true when the billing flow was shown, false when a precondition
// was unmet (offers not yet loaded, no Play Store); purchase results always arrive
// via purchaseUpdates, not the return value.
internal interface BillingClientGateway {
    val connection: StateFlow<ConnectionState>
    val purchaseUpdates: Flow<List<PurchaseRecord>>

    suspend fun ensureConnected(): Boolean

    suspend fun queryActivePurchases(): List<PurchaseRecord>

    suspend fun queryOffers(productId: String): List<OfferRecord>

    suspend fun acknowledge(purchaseToken: String)

    fun launch(
        activity: Activity,
        offerToken: String,
    ): Boolean
}
