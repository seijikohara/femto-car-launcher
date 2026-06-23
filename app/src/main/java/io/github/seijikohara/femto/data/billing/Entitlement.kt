package io.github.seijikohara.femto.data.billing

// The single entitlement truth the app gates on. mapboxUnlocked is true iff an active
// (PURCHASED) femto_plus purchase exists — offer-agnostic (a trial purchase counts as
// unlocked). Acknowledgement is performed as a side-effect of applyPurchases() and does
// NOT gate unlock: the unlock is derived from purchase state alone so we unblock the
// user immediately when Play confirms PURCHASED. lastVerifiedAtMillis records the last
// successful queryPurchasesAsync reconcile (null = only the cached default).
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
