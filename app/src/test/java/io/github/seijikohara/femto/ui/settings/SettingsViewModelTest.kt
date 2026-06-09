package io.github.seijikohara.femto.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.AccentColor
import io.github.seijikohara.femto.data.FontPreferences
import io.github.seijikohara.femto.data.FullscreenSetting
import io.github.seijikohara.femto.data.MapRenderMode
import io.github.seijikohara.femto.testfixtures.FakeDisplaySettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

// Robolectric only to obtain an Application for the (inert) FontPreferences; the
// settings under test go through an in-memory FakeDisplaySettingsStore, so there
// is no DataStore IO and the test is fully driven by the StandardTestDispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val store = FakeDisplaySettingsStore()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `SetFullscreen writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetFullscreen(FullscreenSetting.ON))
            advanceUntilIdle()
            assertEquals(FullscreenSetting.ON, store.settings.first().fullscreen)
        }

    @Test
    fun `SetAccentColor writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetAccentColor(AccentColor.TEAL))
            advanceUntilIdle()
            assertEquals(AccentColor.TEAL, store.settings.first().accentColor)
        }

    @Test
    fun `SetShowMusic writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetShowMusic(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().showMusic)
        }

    @Test
    fun `SetShowClockSeconds writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetShowClockSeconds(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().showClockSeconds)
        }

    @Test
    fun `SetMapRenderPercent writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMapRenderPercent(50))
            advanceUntilIdle()
            assertEquals(50, store.settings.first().mapRenderPercent)
        }

    @Test
    fun `SetMapRenderMode writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMapRenderMode(MapRenderMode.LIVE))
            advanceUntilIdle()
            assertEquals(MapRenderMode.LIVE, store.settings.first().mapRenderMode)
        }

    @Test
    fun `SetMapMarkerPos writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMapMarkerPos(40))
            advanceUntilIdle()
            assertEquals(40, store.settings.first().mapMarkerPos)
        }

    @Test
    fun `SetMap3dBuildings writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMap3dBuildings(true))
            advanceUntilIdle()
            assertEquals(true, store.settings.first().map3dBuildings)
        }

    @Test
    fun `SetMapTerrain writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMapTerrain(true))
            advanceUntilIdle()
            assertEquals(true, store.settings.first().mapTerrain)
        }

    private fun viewModel() = SettingsViewModel(store, FontPreferences(application))
}
