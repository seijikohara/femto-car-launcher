package io.github.seijikohara.femto.data.billing

// The single entitlement truth sub-project C gates on. mapboxUnlocked is derived
// from "the femto_plus subscription has an active, acknowledged purchase" — offer-
// agnostic (a trial purchase counts as unlocked). lastVerifiedAtMillis records the
// last successful queryPurchasesAsync reconcile (null = only the cached default).
internal data class Entitlement(
    val mapboxUnlocked: Boolean = false,
    val lastVerifiedAtMillis: Long? = null,
) {
    companion object {
        val Locked = Entitlement()
    }
}

// One subscription product, two base plans (monthly/annual) defined in Play
// Console. The id is the single source of truth — never re-type the literal.
internal const val FEMTO_PLUS_PRODUCT_ID = "femto_plus"
