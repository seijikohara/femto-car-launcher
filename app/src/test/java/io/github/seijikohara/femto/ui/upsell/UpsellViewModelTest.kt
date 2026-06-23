package io.github.seijikohara.femto.ui.upsell

import app.cash.turbine.test
import io.github.seijikohara.femto.data.billing.ConnectionState
import io.github.seijikohara.femto.data.billing.Entitlement
import io.github.seijikohara.femto.data.billing.SubscriptionOffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpsellViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun offer(id: String) = SubscriptionOffer(id, "tok-$id", "$3.99", "P1M", isTrial = false)

    @Test
    fun `state reflects offers connection and entitlement`() =
        runTest {
            val vm = UpsellViewModel(
                offers = MutableStateFlow(listOf(offer("monthly"))),
                entitlement = MutableStateFlow(Entitlement(mapboxUnlocked = false)),
                connection = MutableStateFlow(ConnectionState.CONNECTED),
                onRefresh = {},
            )
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(1, state.offers.size)
                assertTrue(state.connected)
                assertFalse(state.mapboxUnlocked)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Retry invokes onRefresh`() =
        runTest {
            var refreshed = 0
            val vm = UpsellViewModel(
                offers = MutableStateFlow(emptyList()),
                entitlement = MutableStateFlow(Entitlement.Locked),
                connection = MutableStateFlow(ConnectionState.DISCONNECTED),
                onRefresh = { refreshed++ },
            )
            vm.uiState.test {
                awaitItem()
                vm.onAction(UpsellAction.Retry)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, refreshed)
        }
}
