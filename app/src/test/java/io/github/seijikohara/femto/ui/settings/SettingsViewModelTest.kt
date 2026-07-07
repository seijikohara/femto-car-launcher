package io.github.seijikohara.femto.ui.settings

import io.github.seijikohara.femto.data.calendar.CalendarCatalogState
import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.AssistantLaunchSetting
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.GoogleMapType
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapboxStyle
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.display.OrientationSetting
import io.github.seijikohara.femto.data.display.PresetMode
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.data.display.SpeedUnitSetting
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.fonts.FontSource
import io.github.seijikohara.femto.data.location.LocationQualitySetting
import io.github.seijikohara.femto.data.location.LocationSettings
import io.github.seijikohara.femto.testfixtures.FakeCalendarPreferencesStore
import io.github.seijikohara.femto.testfixtures.FakeDisplaySettingsStore
import io.github.seijikohara.femto.testfixtures.FakeFontSelectionStore
import io.github.seijikohara.femto.testfixtures.FakeLocationSettingsStore
import io.github.seijikohara.femto.testfixtures.fakeCalendarInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
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
    fun `SetMusicShowAlbum writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMusicShowAlbum(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().musicShowAlbum)
        }

    @Test
    fun `SetMusicShowArt writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMusicShowArt(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().musicShowArt)
        }

    @Test
    fun `music meta toggles default on`() =
        runTest(dispatcher) {
            store.settings.first().let {
                assertEquals(true, it.musicShowAlbum)
                assertEquals(true, it.musicShowArt)
            }
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
    fun `ResetToDefaults also clears the hidden calendar set`() =
        runTest(dispatcher) {
            val calendarPrefs = FakeCalendarPreferencesStore(initialHidden = setOf(1L, 2L))
            val vm =
                SettingsViewModel(
                    store,
                    fontStore,
                    locationStore,
                    calendarPrefs,
                    availableCalendars = flowOf(CalendarCatalogState(hasAccess = true, calendars = emptyList())),
                )
            vm.onAction(SettingsAction.ResetToDefaults)
            advanceUntilIdle()
            assertEquals(emptySet(), calendarPrefs.hiddenCalendarIds.first())
        }

    @Test
    fun `ResetSection(APPEARANCE) resets its fields and the font store, leaves other sections alone`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetThemeMode(ThemeMode.DARK))
            vm.onAction(SettingsAction.SetSpeedUnit(SpeedUnitSetting.MILES))
            fontStore.setSource(FontSlot.LATIN, FontSource.GoogleFonts("Roboto Slab"))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetSection(SettingsSectionId.APPEARANCE))
            advanceUntilIdle()

            assertEquals(ThemeMode.SYSTEM, store.settings.first().themeMode)
            assertEquals(FontSource.SystemDefault, fontStore.selection.first().latin)
            // Units is a different section — untouched by an Appearance reset.
            assertEquals(SpeedUnitSetting.MILES, store.settings.first().speedUnit)
        }

    @Test
    fun `ResetSection(SCREEN) resets its fields, leaves other sections alone`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetFullscreen(FullscreenSetting.OFF))
            vm.onAction(SettingsAction.SetDockPosition(DockPosition.LEFT))
            vm.onAction(SettingsAction.SetShowMusic(false))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetSection(SettingsSectionId.SCREEN))
            advanceUntilIdle()

            assertEquals(FullscreenSetting.ON, store.settings.first().fullscreen)
            assertEquals(DockPosition.BOTTOM, store.settings.first().dockPosition)
            // Panels is a different section — untouched by a Screen reset.
            assertEquals(false, store.settings.first().showMusic)
        }

    @Test
    fun `ResetSection(DRIVING) resets its fields, leaves other sections alone`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetPresetMode(PresetMode.DRIVING))
            vm.onAction(SettingsAction.SetMotionTier(MotionTier.OFF))
            vm.onAction(SettingsAction.SetMapZoom(11))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetSection(SettingsSectionId.DRIVING))
            advanceUntilIdle()

            assertEquals(PresetMode.AUTO, store.settings.first().presetMode)
            assertEquals(MotionTier.STANDARD, store.settings.first().motionTier)
            // Map is a different section — untouched by a Driving reset.
            assertEquals(11, store.settings.first().mapZoom)
        }

    @Test
    fun `ResetSection(UNITS) resets its fields, leaves other sections alone`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetSpeedUnit(SpeedUnitSetting.MILES))
            vm.onAction(SettingsAction.SetShowClockSeconds(true))
            vm.onAction(SettingsAction.SetDriverSide(DriverSide.LEFT))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetSection(SettingsSectionId.UNITS))
            advanceUntilIdle()

            assertEquals(SpeedUnitSetting.AUTO, store.settings.first().speedUnit)
            assertEquals(false, store.settings.first().showClockSeconds)
            // Screen is a different section — untouched by a Units reset.
            assertEquals(DriverSide.LEFT, store.settings.first().driverSide)
        }

    @Test
    fun `ResetSection(MAP) resets its fields, leaves other sections alone`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetMapBackend(MapBackend.MAPBOX))
            vm.onAction(SettingsAction.SetMapZoom(11))
            vm.onAction(SettingsAction.SetShowMusic(false))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetSection(SettingsSectionId.MAP))
            advanceUntilIdle()

            assertEquals(MapBackend.OSM, store.settings.first().mapBackend)
            assertEquals(16, store.settings.first().mapZoom)
            // Panels is a different section — untouched by a Map reset.
            assertEquals(false, store.settings.first().showMusic)
        }

    @Test
    fun `ResetSection(LOCATION) resets the location store only, leaves the display store alone`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetLocationQuality(LocationQualitySetting.LOW_POWER))
            vm.onAction(SettingsAction.SetMapZoom(11))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetSection(SettingsSectionId.LOCATION))
            advanceUntilIdle()

            assertEquals(LocationQualitySetting.HIGH_ACCURACY, locationStore.settings.first().quality)
            // LOCATION owns no DisplayPreferences key, so the display store is untouched.
            assertEquals(11, store.settings.first().mapZoom)
        }

    @Test
    fun `ResetSection(PANELS) resets its fields and the calendar store, leaves other sections alone`() =
        runTest(dispatcher) {
            val calendarPrefs = FakeCalendarPreferencesStore(initialHidden = setOf(1L))
            val vm =
                SettingsViewModel(
                    store,
                    fontStore,
                    locationStore,
                    calendarPrefs,
                    availableCalendars = flowOf(CalendarCatalogState(hasAccess = true, calendars = emptyList())),
                )
            vm.onAction(SettingsAction.SetShowMusic(false))
            vm.onAction(SettingsAction.SetMusicSpectrum(true))
            vm.onAction(SettingsAction.SetMapZoom(11))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetSection(SettingsSectionId.PANELS))
            advanceUntilIdle()

            assertEquals(true, store.settings.first().showMusic)
            assertEquals(false, store.settings.first().musicSpectrum)
            assertEquals(emptySet(), calendarPrefs.hiddenCalendarIds.first())
            // Map is a different section — untouched by a Panels reset.
            assertEquals(11, store.settings.first().mapZoom)
        }

    @Test
    fun `defaults reflect the revised values`() =
        runTest(dispatcher) {
            val defaults = DisplaySettings.Default
            assertEquals(FullscreenSetting.ON, defaults.fullscreen)
            assertEquals(true, defaults.map3dBuildings)
            assertEquals(false, defaults.mapTerrain)
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
    fun `SetDriverSide writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetDriverSide(DriverSide.LEFT))
            advanceUntilIdle()
            assertEquals(DriverSide.LEFT, store.settings.first().driverSide)
        }

    @Test
    fun `SetPresetMode writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetPresetMode(PresetMode.DRIVING))
            advanceUntilIdle()
            assertEquals(PresetMode.DRIVING, store.settings.first().presetMode)
        }

    @Test
    fun `SetDrivingThresholdKmh writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetDrivingThresholdKmh(20))
            advanceUntilIdle()
            assertEquals(20, store.settings.first().drivingThresholdKmh)
        }

    @Test
    fun `SetMotionTier writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetMotionTier(MotionTier.OFF))
            advanceUntilIdle()
            assertEquals(MotionTier.OFF, store.settings.first().motionTier)
        }

    @Test
    fun `SetOrientation writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetOrientation(OrientationSetting.LANDSCAPE))
            advanceUntilIdle()
            assertEquals(OrientationSetting.LANDSCAPE, store.settings.first().orientation)
        }

    @Test
    fun `SetBriefingShowEvent writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetBriefingShowEvent(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().briefingShowEvent)
        }

    @Test
    fun `SetBriefingShowWeather writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetBriefingShowWeather(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().briefingShowWeather)
        }

    @Test
    fun `SetMapBackend persists and reflects in state`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetMapBackend(MapBackend.MAPBOX))
            advanceUntilIdle()
            assertEquals(MapBackend.MAPBOX, store.settings.first().mapBackend)
        }

    @Test
    fun `SetMapboxStyle persists and reflects in state`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetMapboxStyle(MapboxStyle.SATELLITE))
            advanceUntilIdle()
            assertEquals(MapboxStyle.SATELLITE, store.settings.first().mapboxStyle)
        }

    @Test
    fun `SetMapboxTraffic persists and reflects in state`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetMapboxTraffic(true))
            advanceUntilIdle()
            assertEquals(true, store.settings.first().mapboxTraffic)
        }

    @Test
    fun `SaveMapboxToken persists token and selects the Mapbox backend atomically`() =
        runTest(dispatcher) {
            val vm = viewModel()
            // Subscribe so WhileUiSubscribed keeps the upstream combine alive across both phases.
            backgroundScope.launch { vm.uiState.collect { } }
            advanceUntilIdle()

            vm.onAction(SettingsAction.SaveMapboxToken("pk.abc"))
            advanceUntilIdle()
            assertEquals("pk.abc", vm.uiState.value.mapboxAccessToken)
            assertEquals(MapBackend.MAPBOX, vm.uiState.value.mapBackend)

            vm.onAction(SettingsAction.ClearMapboxToken)
            advanceUntilIdle()
            assertEquals("", vm.uiState.value.mapboxAccessToken)
        }

    @Test
    fun `available calendars and hidden set surface and SetCalendarHidden toggles`() =
        runTest(dispatcher) {
            val calendarPrefs = FakeCalendarPreferencesStore()
            val vm =
                SettingsViewModel(
                    store,
                    fontStore,
                    locationStore,
                    calendarPrefs,
                    availableCalendars =
                        flowOf(
                            CalendarCatalogState(
                                hasAccess = true,
                                calendars =
                                    listOf(
                                        fakeCalendarInfo(id = 1L),
                                        fakeCalendarInfo(id = 2L, displayName = "Work"),
                                    ),
                            ),
                        ),
                )
            backgroundScope.launch { vm.uiState.collect { } }
            advanceUntilIdle()
            assertEquals(
                listOf(1L, 2L),
                vm.uiState.value.availableCalendars
                    .map { it.id },
            )
            assertEquals(emptySet(), vm.uiState.value.hiddenCalendarIds)

            vm.onAction(SettingsAction.SetCalendarHidden(id = 2L, hidden = true))
            advanceUntilIdle()
            assertEquals(setOf(2L), vm.uiState.value.hiddenCalendarIds)
        }

    @Test
    fun `SaveGoogleMapsKey persists key then switches backend atomically`() =
        runTest(dispatcher) {
            val vm = viewModel()
            // Subscribe so WhileUiSubscribed keeps the upstream combine alive across both phases.
            backgroundScope.launch { vm.uiState.collect { } }
            advanceUntilIdle()

            vm.onAction(SettingsAction.SaveGoogleMapsKey("AIza-test-key"))
            advanceUntilIdle()
            assertEquals("AIza-test-key", vm.uiState.value.googleMapsApiKey)
            assertEquals(MapBackend.GOOGLEMAPS, vm.uiState.value.mapBackend)

            vm.onAction(SettingsAction.ClearGoogleMapsKey)
            advanceUntilIdle()
            assertEquals("", vm.uiState.value.googleMapsApiKey)
        }

    @Test
    fun `SetGoogleMapsMapType and SetGoogleMapsTraffic surface in uiState`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect { } }
            advanceUntilIdle()

            vm.onAction(SettingsAction.SetGoogleMapsMapType(GoogleMapType.SATELLITE))
            vm.onAction(SettingsAction.SetGoogleMapsTraffic(true))
            advanceUntilIdle()
            assertEquals(GoogleMapType.SATELLITE, vm.uiState.value.googleMapsMapType)
            assertEquals(true, vm.uiState.value.googleMapsTraffic)
        }

    private fun viewModel() =
        SettingsViewModel(
            store,
            fontStore,
            locationStore,
            FakeCalendarPreferencesStore(),
            availableCalendars = flowOf(CalendarCatalogState(hasAccess = true, calendars = emptyList())),
        )
}
