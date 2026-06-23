package io.github.seijikohara.femto.data.billing

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class EntitlementLogicTest {
    private fun purchase(
        state: PurchaseState = PurchaseState.PURCHASED,
        ack: Boolean = true,
        ids: List<String> = listOf(FEMTO_PLUS_PRODUCT_ID),
    ) = PurchaseRecord(
        productIds = ids,
        isAcknowledged = ack,
        state = state,
        purchaseToken = "tok",
    )

    @Test
    fun `active acknowledged femto_plus purchase unlocks mapbox`() {
        val e = entitlementOf(listOf(purchase()), nowMillis = 1000L)
        assertTrue(e.mapboxUnlocked)
        assertEquals(1000L, e.lastVerifiedAtMillis)
    }

    @Test
    fun `no purchases is locked`() {
        assertFalse(entitlementOf(emptyList(), 1000L).mapboxUnlocked)
    }

    @Test
    fun `pending purchase does not unlock`() {
        assertFalse(entitlementOf(listOf(purchase(state = PurchaseState.PENDING)), 1L).mapboxUnlocked)
    }

    @Test
    fun `unrelated product does not unlock`() {
        assertFalse(entitlementOf(listOf(purchase(ids = listOf("other"))), 1L).mapboxUnlocked)
    }

    @Test
    fun `active purchased but unacknowledged still unlocks but is flagged for ack`() {
        val list = listOf(purchase(ack = false))
        assertTrue(entitlementOf(list, 1L).mapboxUnlocked)
        assertEquals(listOf("tok"), unacknowledgedActiveTokens(list))
    }

    @Test
    fun `acknowledged purchase yields no ack work`() {
        assertTrue(unacknowledgedActiveTokens(listOf(purchase(ack = true))).isEmpty())
    }
}
