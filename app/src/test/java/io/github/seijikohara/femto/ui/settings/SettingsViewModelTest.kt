package io.github.seijikohara.femto.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.AssistantLaunchSetting
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapRenderMode
import io.github.seijikohara.femto.data.display.OrientationSetting
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.data.fonts.FontPreferences
import io.github.seijikohara.femto.data.location.LocationQualitySetting
import io.github.seijikohara.femto.data.location.LocationSettings
import io.github.seijikohara.femto.testfixtures.FakeDisplaySettingsStore
import io.github.seijikohara.femto.testfixtures.FakeFontSelectionStore
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

// Pure JVM: every collaborator is an in-memory fake, so there is no DataStore IO
// and the test is fully driven by the StandardTestDispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val store = FakeDisplaySettingsStore()
    private val fontStore = FakeFontSelectionStore()
    private val locationStore = FakeLocationSettingsStore()
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
    fun `SetAssistantLaunch writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetAssistantLaunch(AssistantLaunchSetting.IN_APP))
            advanceUntilIdle()
            assertEquals(AssistantLaunchSetting.IN_APP, store.settings.first().assistantLaunch)
        }

    @Test
    fun `SetAccentColor writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetAccentColor(AccentColor.TEAL))
            advanceUntilIdle()
            assertEquals(AccentColor.TEAL, store.settings.first().accentColor)
        }

    @Test
    fun `SetUiScale writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetUiScale(UiScale.SMALL))
            advanceUntilIdle()
            assertEquals(UiScale.SMALL, store.settings.first().uiScale)
        }

    @Test
    fun `ui scale defaults to medium`() =
        runTest(dispatcher) {
            assertEquals(UiScale.MEDIUM, store.settings.first().uiScale)
        }

    @Test
    fun `SetShowMusic writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetShowMusic(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().showMusic)
        }

    @Test
    fun `SetMusicSpectrum writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMusicSpectrum(true))
            advanceUntilIdle()
            assertEquals(true, store.settings.first().musicSpectrum)
        }

    @Test
    fun `music spectrum defaults to off`() =
        runTest(dispatcher) {
            assertEquals(false, store.settings.first().musicSpectrum)
        }

    @Test
    fun `SetShowClockSeconds writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetShowClockSeconds(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().showClockSeconds)
        }

    @Test
    fun `SetMapNorthUp writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMapNorthUp(true))
            advanceUntilIdle()
            assertEquals(true, store.settings.first().mapNorthUp)
        }

    @Test
    fun `map orientation defaults to heading-up`() =
        runTest(dispatcher) {
            assertEquals(false, store.settings.first().mapNorthUp)
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
    fun `SetBackgroundRanging writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetBackgroundRanging(true))
            advanceUntilIdle()
            assertEquals(true, locationStore.settings.first().backgroundRangingEnabled)
        }

    @Test
    fun `background ranging defaults to off`() =
        runTest(dispatcher) {
            assertEquals(false, locationStore.settings.first().backgroundRangingEnabled)
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
            assertEquals(16, vm.uiState.value.glassBlurRadius)
            assertEquals(50, vm.uiState.value.glassTintScale)
            vm.onAction(SettingsAction.SetGlassBlurRadius(12))
            vm.onAction(SettingsAction.SetGlassTintScale(60))
            advanceUntilIdle()
            assertEquals(12, store.settings.first().glassBlurRadius)
            assertEquals(60, store.settings.first().glassTintScale)
        }

    @Test
    fun `SetDockPosition writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetDockPosition(DockPosition.LEFT))
            advanceUntilIdle()
            assertEquals(DockPosition.LEFT, store.settings.first().dockPosition)
        }

    @Test
    fun `SetOrientation writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetOrientation(OrientationSetting.LANDSCAPE))
            advanceUntilIdle()
            assertEquals(OrientationSetting.LANDSCAPE, store.settings.first().orientation)
        }

    private fun viewModel() = SettingsViewModel(store, fontStore, locationStore)
}
