package io.github.seijikohara.femto.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.AccentColor
import io.github.seijikohara.femto.data.DisplaySettings
import io.github.seijikohara.femto.data.DrawerIconSize
import io.github.seijikohara.femto.data.FontPreferences
import io.github.seijikohara.femto.data.FullscreenSetting
import io.github.seijikohara.femto.data.LocationQualitySetting
import io.github.seijikohara.femto.data.LocationSettings
import io.github.seijikohara.femto.data.MapColorScheme
import io.github.seijikohara.femto.data.MapRenderMode
import io.github.seijikohara.femto.testfixtures.FakeDisplaySettingsStore
import io.github.seijikohara.femto.testfixtures.FakeDrawerSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeLocationSettingsStore
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
    private val locationStore = FakeLocationSettingsStore()
    private val drawerStore = FakeDrawerSettingsStore()
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
    fun `SetKeepScreenOn writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetKeepScreenOn(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().keepScreenOn)
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
    fun `SetMapSchemeLight writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMapSchemeLight(MapColorScheme.BRIGHT))
            advanceUntilIdle()
            assertEquals(MapColorScheme.BRIGHT, store.settings.first().mapSchemeLight)
        }

    @Test
    fun `SetMapSchemeDark writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMapSchemeDark(MapColorScheme.FIORD))
            advanceUntilIdle()
            assertEquals(MapColorScheme.FIORD, store.settings.first().mapSchemeDark)
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

    @Test
    fun `SetLocationQuality writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetLocationQuality(LocationQualitySetting.LOW_POWER))
            advanceUntilIdle()
            assertEquals(LocationQualitySetting.LOW_POWER, locationStore.settings.first().quality)
        }

    @Test
    fun `SetLocationIntervalMillis writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetLocationIntervalMillis(1_000L))
            advanceUntilIdle()
            assertEquals(1_000L, locationStore.settings.first().intervalMillis)
        }

    @Test
    fun `SetLocationMinDistance writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetLocationMinDistance(5))
            advanceUntilIdle()
            assertEquals(5, locationStore.settings.first().minUpdateDistanceMeters)
        }

    @Test
    fun `ResetToDefaults restores display settings to their defaults`() =
        runTest(dispatcher) {
            val vm = viewModel()
            // Move a representative field of each persisted type off its default.
            vm.onAction(SettingsAction.SetFullscreen(FullscreenSetting.ON))
            vm.onAction(SettingsAction.SetAccentColor(AccentColor.TEAL))
            vm.onAction(SettingsAction.SetShowMusic(false))
            vm.onAction(SettingsAction.SetMapTilt(10))
            vm.onAction(SettingsAction.SetLocationIntervalMillis(2_000L))
            advanceUntilIdle()
            vm.onAction(SettingsAction.ResetToDefaults)
            advanceUntilIdle()
            assertEquals(DisplaySettings.Default, store.settings.first())
            assertEquals(LocationSettings.Default, locationStore.settings.first())
        }

    @Test
    fun `defaults reflect the revised values`() =
        runTest(dispatcher) {
            val defaults = DisplaySettings.Default
            assertEquals(FullscreenSetting.ON, defaults.fullscreen)
            assertEquals(MapRenderMode.LIVE, defaults.mapRenderMode)
            assertEquals(true, defaults.map3dBuildings)
            assertEquals(true, defaults.mapTerrain)
            assertEquals(false, defaults.showClockSeconds)
        }

    @Test
    fun `glass defaults and setters write to the store`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(24, vm.uiState.value.glassBlurRadius)
            assertEquals(100, vm.uiState.value.glassTintScale)
            vm.onAction(SettingsAction.SetGlassBlurRadius(12))
            vm.onAction(SettingsAction.SetGlassTintScale(60))
            advanceUntilIdle()
            assertEquals(12, store.settings.first().glassBlurRadius)
            assertEquals(60, store.settings.first().glassTintScale)
        }

    @Test
    fun `SetDrawerIconSize writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetDrawerIconSize(DrawerIconSize.LARGE))
            advanceUntilIdle()
            assertEquals(DrawerIconSize.LARGE, drawerStore.iconSize.first())
        }

    private fun viewModel() = SettingsViewModel(store, FontPreferences(application), locationStore, drawerStore)
}
