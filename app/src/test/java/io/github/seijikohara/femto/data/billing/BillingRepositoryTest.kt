package io.github.seijikohara.femto.data.billing

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// BillingRepositoryTest uses Robolectric only for the launchPurchase test that
// requires a real Activity instance; the other three tests are pure coroutine
// tests that do not touch Android framework.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BillingRepositoryTest {
    private open class FakeGateway(
        var purchases: List<PurchaseRecord> = emptyList(),
        var offers: List<OfferRecord> = emptyList(),
    ) : BillingClientGateway {
        override val connection: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.CONNECTED)
        override val purchaseUpdates: Flow<List<PurchaseRecord>> =
            MutableSharedFlow(replay = 0)
        val acknowledged = mutableListOf<String>()
        var launched: String? = null

        override suspend fun ensureConnected() = true

        override suspend fun queryActivePurchases() = purchases

        override suspend fun queryOffers(productId: String) = offers

        override suspend fun acknowledge(purchaseToken: String) {
            acknowledged += purchaseToken
        }

        override fun launch(
            activity: Activity,
            offerToken: String,
        ): Boolean {
            launched = offerToken
            return true
        }
    }

    private class FakeStore : BillingEntitlementStore {
        val state = MutableStateFlow(Entitlement.Locked)
        override val cached: Flow<Entitlement> = state

        override suspend fun cache(entitlement: Entitlement) {
            state.value = entitlement
        }
    }

    @Test
    fun `entitlement seeds from cache before refresh`() =
        runTest {
            val store = FakeStore().apply { state.value = Entitlement(mapboxUnlocked = true) }
            // FakeGateway that never connects: ensureConnected() returns false so
            // refresh() exits immediately without overwriting the cached value.
            val disconnectedGateway =
                object : FakeGateway() {
                    override suspend fun ensureConnected() = false
                }
            val repo =
                BillingRepository(disconnectedGateway, store, backgroundScope, now = { 5L })
            // backgroundScope coroutines are scheduled eagerly; one runCurrent() pass
            // lets the cache-collector emit before we assert.
            runCurrent()
            assertTrue(repo.entitlement.first().mapboxUnlocked)
        }

    @Test
    fun `refresh reconciles unlock and acknowledges active purchase and caches`() =
        runTest {
            val gw =
                FakeGateway(
                    purchases =
                        listOf(
                            PurchaseRecord(
                                productIds = listOf(FEMTO_PLUS_PRODUCT_ID),
                                isAcknowledged = false,
                                state = PurchaseState.PURCHASED,
                                purchaseToken = "tok",
                            ),
                        ),
                )
            val store = FakeStore()
            val repo = BillingRepository(gw, store, backgroundScope, now = { 42L })
            repo.refresh()
            advanceUntilIdle()
            assertTrue(repo.entitlement.first().mapboxUnlocked)
            assertEquals(42L, repo.entitlement.first().lastVerifiedAtMillis)
            assertEquals(listOf("tok"), gw.acknowledged)
            assertTrue(store.state.value.mapboxUnlocked)
        }

    @Test
    fun `refresh with no purchases locks entitlement`() =
        runTest {
            val store = FakeStore().apply { state.value = Entitlement(mapboxUnlocked = true) }
            val repo =
                BillingRepository(FakeGateway(purchases = emptyList()), store, backgroundScope, now = { 7L })
            repo.refresh()
            advanceUntilIdle()
            assertFalse(repo.entitlement.first().mapboxUnlocked)
        }

    @Test
    fun `refresh unlock wins over stale Locked cache seed`() =
        runTest {
            // Store pre-seeded to Locked; gateway is connected and sees an active purchase.
            // Under the old separate-launch init the seed could arrive AFTER refresh and
            // overwrite mapboxUnlocked=true back to Locked — this test would then fail.
            val store =
                FakeStore().apply {
                    state.value = Entitlement.Locked
                }
            val gw =
                FakeGateway(
                    purchases =
                        listOf(
                            PurchaseRecord(
                                productIds = listOf(FEMTO_PLUS_PRODUCT_ID),
                                isAcknowledged = true,
                                state = PurchaseState.PURCHASED,
                                purchaseToken = "tok-race",
                            ),
                        ),
                )
            // Construct in backgroundScope so the init's scope.launch runs on the same
            // test scheduler. runCurrent() drains the seed + starts the inner launches;
            // advanceUntilIdle() then lets refresh() complete all its suspension points.
            val repo = BillingRepository(gw, store, backgroundScope, now = { 1L })
            runCurrent()
            advanceUntilIdle()
            assertTrue(repo.entitlement.first().mapboxUnlocked)
        }

    @Test
    fun `launchPurchase delegates to gateway and returns true when flow launched`() =
        runTest {
            val gw = FakeGateway()
            val repo = BillingRepository(gw, FakeStore(), backgroundScope, now = { 0L })
            val activity =
                org.robolectric.Robolectric
                    .buildActivity(Activity::class.java)
                    .get()
            val launched = repo.launchPurchase(activity = activity, offerToken = "o1")
            assertTrue(launched)
            assertEquals("o1", gw.launched)
        }
}
