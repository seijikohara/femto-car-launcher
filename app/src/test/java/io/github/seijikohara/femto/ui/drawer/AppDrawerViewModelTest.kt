package io.github.seijikohara.femto.ui.drawer

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
import kotlin.test.assertEquals
import kotlin.test.assertIs

// Content(emptyList()) is the legitimate "no apps installed" success state, so
// the transitions are assertable without constructing AppEntry (whose Bitmap
// icon needs an Android runtime).
@OptIn(ExperimentalCoroutinesApi::class)
class AppDrawerViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh emits content when the query succeeds`() =
        runTest {
            val viewModel = AppDrawerViewModel(queryApps = { emptyList() })
            viewModel.uiState.test {
                assertEquals(AppDrawerUiState.Loading, awaitItem())

                viewModel.onAction(AppDrawerAction.Refresh)

                assertEquals(AppDrawerUiState.Content(emptyList()), awaitItem())
            }
        }

    @Test
    fun `refresh emits error when the query throws`() =
        runTest {
            val viewModel = AppDrawerViewModel(queryApps = { error("pm died") })
            viewModel.uiState.test {
                assertEquals(AppDrawerUiState.Loading, awaitItem())

                viewModel.onAction(AppDrawerAction.Refresh)

                assertIs<AppDrawerUiState.Error>(awaitItem())
            }
        }

    @Test
    fun `retry after an error flips back through loading to content`() =
        runTest {
            var failOnce = true
            val viewModel =
                AppDrawerViewModel(
                    queryApps = {
                        if (failOnce) {
                            failOnce = false
                            error("pm died")
                        } else {
                            emptyList()
                        }
                    },
                )
            viewModel.uiState.test {
                assertEquals(AppDrawerUiState.Loading, awaitItem())

                viewModel.onAction(AppDrawerAction.Refresh)
                assertIs<AppDrawerUiState.Error>(awaitItem())

                viewModel.onAction(AppDrawerAction.Refresh)
                assertEquals(AppDrawerUiState.Loading, awaitItem())
                assertEquals(AppDrawerUiState.Content(emptyList()), awaitItem())
            }
        }
}
