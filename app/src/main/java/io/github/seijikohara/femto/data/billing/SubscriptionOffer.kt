package io.github.seijikohara.femto.data.billing

// UI-friendly projection of a ProductDetails SubscriptionOfferDetails, for the
// Diagnostics list (and later the sub-project C upsell). offerToken is what
// launchBillingFlow needs.
internal data class SubscriptionOffer(
    val basePlanId: String,
    val offerToken: String,
    val formattedPrice: String,
    val billingPeriod: String,
    val isTrial: Boolean,
)
