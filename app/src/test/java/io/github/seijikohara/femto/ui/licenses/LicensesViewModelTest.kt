package io.github.seijikohara.femto.ui.licenses

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LicensesViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads libraries into uiState and clears loading`() =
        runTest {
            val libraries = listOf(item("Jetpack Compose"), item("OkHttp"))
            val viewModel = LicensesViewModel(loadLibraries = { libraries })
            viewModel.uiState.test {
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertEquals(libraries, state.libraries)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `degrades to an empty list when the loader fails`() =
        runTest {
            val viewModel = LicensesViewModel(loadLibraries = { error("metadata parse broke") })
            viewModel.uiState.test {
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertEquals(emptyList(), state.libraries)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Select focuses the chosen license`() =
        runTest {
            val maplibre = item("MapLibre GL JS")
            val viewModel = LicensesViewModel(loadLibraries = { listOf(maplibre) })
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(LicensesAction.Select(maplibre))
                assertEquals(maplibre, awaitItem().selected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ClearSelection resets the focused license`() =
        runTest {
            val maplibre = item("MapLibre GL JS")
            val viewModel = LicensesViewModel(loadLibraries = { listOf(maplibre) })
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(LicensesAction.Select(maplibre))
                awaitItem()
                viewModel.onAction(LicensesAction.ClearSelection)
                assertNull(awaitItem().selected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun item(name: String) =
        LicenseItem(
            id = name,
            name = name,
            licenseName = "Apache-2.0",
            licenseText = "License text for $name",
            url = "https://example.com/$name",
        )
}
