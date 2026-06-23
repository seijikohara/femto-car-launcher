package io.github.seijikohara.femto.data.billing

// A purchase must be in the PURCHASED state for the femto_plus product to unlock
// Mapbox. Acknowledgement is a separate obligation (Play auto-refunds after 3 days
// if unacknowledged), but it does not gate the unlock itself — an active, not-yet-
// acknowledged purchase still counts as unlocked.
private fun PurchaseRecord.isActiveFemtoPlus() =
    state == PurchaseState.PURCHASED && FEMTO_PLUS_PRODUCT_ID in productIds

internal fun entitlementOf(purchases: List<PurchaseRecord>, nowMillis: Long): Entitlement =
    Entitlement(
        mapboxUnlocked = purchases.any { it.isActiveFemtoPlus() },
        lastVerifiedAtMillis = nowMillis,
    )

// Returns purchase tokens that must be acknowledged before Play's 3-day window
// expires; the repository calls acknowledge() for each one.
internal fun unacknowledgedActiveTokens(purchases: List<PurchaseRecord>): List<String> =
    purchases.filter { it.isActiveFemtoPlus() && !it.isAcknowledged }.map { it.purchaseToken }

internal fun offersOf(records: List<OfferRecord>): List<SubscriptionOffer> =
    records.map {
        SubscriptionOffer(
            basePlanId = it.basePlanId,
            offerToken = it.offerToken,
            formattedPrice = it.formattedPrice,
            billingPeriod = it.billingPeriod,
            isTrial = it.isTrial,
        )
    }
