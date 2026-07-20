package io.github.seijikohara.femto.ui.drawer

import app.cash.turbine.test
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.testfixtures.fakeAppEntry
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Exercises [AppDrawerViewModel]'s recentApps resolution against real
 * [AppEntry] objects (which need a real [android.graphics.Bitmap], hence
 * Robolectric) — [AppDrawerViewModelTest] deliberately avoids that runtime
 * cost and only covers the Loading/Content/Error apps-query transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppDrawerViewModelRecentAppsTest {
    private val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
    private val music = fakeAppEntry(packageName = "com.music", className = ".Main", label = "Music")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `content resolves recentApps in recency order and drops an uninstalled entry`() =
        runTest {
            val recent = MutableStateFlow(listOf("com.music/.Main", "com.stale/.Gone", "com.maps/.Main"))
            val viewModel = AppDrawerViewModel(queryApps = { listOf(maps, music) }, recentComponents = recent)

            viewModel.uiState.test {
                assertEquals(AppDrawerUiState.Loading, awaitItem())

                viewModel.onAction(AppDrawerAction.Refresh)

                val content = assertIs<AppDrawerUiState.Content>(awaitItem())
                assertEquals(listOf(music, maps), content.recentApps)
            }
        }

    @Test
    fun `a later launch-history emission updates recentApps without a new apps query`() =
        runTest {
            var queryCount = 0
            val recent = MutableStateFlow(listOf("com.maps/.Main"))
            val viewModel =
                AppDrawerViewModel(
                    queryApps = {
                        queryCount++
                        listOf(maps, music)
                    },
                    recentComponents = recent,
                )

            viewModel.uiState.test {
                assertEquals(AppDrawerUiState.Loading, awaitItem())

                viewModel.onAction(AppDrawerAction.Refresh)
                val firstContent = assertIs<AppDrawerUiState.Content>(awaitItem())
                assertEquals(listOf(maps), firstContent.recentApps)

                // Simulate a launch from the same open sheet: the store's flow
                // emits a new order, and Content updates in place.
                recent.value = listOf("com.music/.Main", "com.maps/.Main")
                val secondContent = assertIs<AppDrawerUiState.Content>(awaitItem())
                assertEquals(listOf(music, maps), secondContent.recentApps)
                assertEquals(listOf(maps, music), secondContent.apps)
            }
            assertEquals(1, queryCount)
        }

    @Test
    fun `content starts with no recent apps when the launch history is empty`() =
        runTest {
            val viewModel = AppDrawerViewModel(queryApps = { listOf(maps, music) })

            viewModel.uiState.test {
                assertEquals(AppDrawerUiState.Loading, awaitItem())

                viewModel.onAction(AppDrawerAction.Refresh)

                val content = assertIs<AppDrawerUiState.Content>(awaitItem())
                assertEquals(emptyList(), content.recentApps)
            }
        }

    @Test
    fun `Launch records the launch in recent history when launchApp resolves`() =
        runTest {
            val recorded = mutableListOf<String>()
            val viewModel =
                AppDrawerViewModel(
                    queryApps = { listOf(maps) },
                    launchApp = { true },
                    recordLaunch = { recorded += it },
                )

            viewModel.onAction(AppDrawerAction.Launch(maps.componentName))

            assertEquals(listOf(maps.componentName.flattenToString()), recorded)
        }

    @Test
    fun `Launch does not record when launchApp fails to resolve`() =
        runTest {
            val recorded = mutableListOf<String>()
            var attempted: android.content.ComponentName? = null
            val viewModel =
                AppDrawerViewModel(
                    queryApps = { listOf(maps) },
                    launchApp = { component ->
                        attempted = component
                        false
                    },
                    recordLaunch = { recorded += it },
                )

            viewModel.onAction(AppDrawerAction.Launch(maps.componentName))

            // A stale tile never opened anything worth surfacing again.
            assertEquals(maps.componentName, attempted)
            assertEquals(emptyList(), recorded)
        }
}
