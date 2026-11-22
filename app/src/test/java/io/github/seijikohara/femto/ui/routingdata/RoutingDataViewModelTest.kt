package io.github.seijikohara.femto.ui.routingdata

import io.github.seijikohara.femto.data.routing.InstalledRoutingTile
import io.github.seijikohara.femto.testfixtures.FakeRoutingDataPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingDataViewModelTest {
    private val port = FakeRoutingDataPort(listOf(WEST, EAST))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // uiState is shared WhileUiSubscribed, so every test holds a subscriber the way the sheet does.
    private fun TestScope.subscribedViewModel(): RoutingDataViewModel =
        RoutingDataViewModel(port).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    @Test
    fun the_sheet_lists_the_tiles_on_the_device() =
        runTest {
            val state = subscribedViewModel().uiState.value

            assertTrue(state.loaded)
            assertEquals(listOf(WEST, EAST), state.tiles)
            assertEquals(WEST.sizeBytes + EAST.sizeBytes, state.totalBytes)
        }

    @Test
    fun the_list_reads_as_not_loaded_until_the_store_answers() =
        runTest {
            val viewModel = RoutingDataViewModel(FakeRoutingDataPort(initial = null))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertFalse(viewModel.uiState.value.loaded)
        }

    @Test
    fun a_delete_asks_first_and_deletes_nothing_yet() =
        runTest {
            val viewModel = subscribedViewModel()

            viewModel.onAction(RoutingDataAction.DeleteTile(WEST.name))

            assertEquals(RoutingDataDialog.ConfirmDelete(WEST), viewModel.uiState.value.dialog)
            assertEquals(emptyList(), port.deleted)
        }

    @Test
    fun a_confirmed_delete_removes_the_tile() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(RoutingDataAction.DeleteTile(WEST.name))

            viewModel.onAction(RoutingDataAction.ConfirmDelete)

            assertEquals(listOf(WEST.name), port.deleted)
            assertEquals(listOf(EAST), viewModel.uiState.value.tiles)
            assertNull(viewModel.uiState.value.dialog)
        }

    @Test
    fun a_cancelled_delete_removes_nothing() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(RoutingDataAction.DeleteTile(WEST.name))

            viewModel.onAction(RoutingDataAction.CloseDialog)
            viewModel.onAction(RoutingDataAction.ConfirmDelete)

            assertEquals(emptyList(), port.deleted)
            assertNull(viewModel.uiState.value.dialog)
        }

    @Test
    fun deleting_everything_asks_first_with_the_total() =
        runTest {
            val viewModel = subscribedViewModel()

            viewModel.onAction(RoutingDataAction.DeleteAll)

            assertEquals(
                RoutingDataDialog.ConfirmDeleteAll(WEST.sizeBytes + EAST.sizeBytes),
                viewModel.uiState.value.dialog,
            )
            assertFalse(port.deletedAll)
        }

    @Test
    fun a_confirmed_delete_of_everything_empties_the_list() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(RoutingDataAction.DeleteAll)

            viewModel.onAction(RoutingDataAction.ConfirmDeleteAll)

            assertTrue(port.deletedAll)
            assertEquals(emptyList(), viewModel.uiState.value.tiles)
        }

    @Test
    fun with_nothing_on_the_device_delete_all_asks_nothing() =
        runTest {
            val viewModel = RoutingDataViewModel(FakeRoutingDataPort(emptyList()))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.onAction(RoutingDataAction.DeleteAll)

            assertNull(viewModel.uiState.value.dialog)
        }

    @Test
    fun a_closed_sheet_drops_its_confirmation() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(RoutingDataAction.DeleteTile(WEST.name))

            viewModel.onAction(RoutingDataAction.Closed)

            assertNull(viewModel.uiState.value.dialog)
        }

    private companion object {
        val WEST = InstalledRoutingTile("E130_N30", 48_000_000L, dataDateEpochMs = null, checkedAtEpochMs = 1L)
        val EAST = InstalledRoutingTile("E135_N30", 96_000_000L, dataDateEpochMs = null, checkedAtEpochMs = 2L)
    }
}
