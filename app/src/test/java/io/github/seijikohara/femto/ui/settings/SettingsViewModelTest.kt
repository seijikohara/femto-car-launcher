package io.github.seijikohara.femto.ui.settings

import io.github.seijikohara.femto.data.calendar.CalendarCatalogState
import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.AssistantLaunchSetting
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DockWidth
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.GoogleMapType
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.display.OrientationSetting
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.data.display.SpeedUnitSetting
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.fonts.FontSource
import io.github.seijikohara.femto.data.location.LocationQualitySetting
import io.github.seijikohara.femto.data.location.LocationSettings
import io.github.seijikohara.femto.data.location.TripAutoResetSetting
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.testfixtures.FakeCalendarPreferencesStore
import io.github.seijikohara.femto.testfixtures.FakeDisplaySettingsStore
import io.github.seijikohara.femto.testfixtures.FakeDockSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeFontSelectionStore
import io.github.seijikohara.femto.testfixtures.FakeInstallConfirmation
import io.github.seijikohara.femto.testfixtures.FakeLocationSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeTrackLogPort
import io.github.seijikohara.femto.testfixtures.FakeUpdateSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeUpdaterPort
import io.github.seijikohara.femto.testfixtures.fakeCalendarInfo
import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Pure JVM: every collaborator is an in-memory fake, so there is no DataStore IO
// and the test is fully driven by the StandardTestDispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val store = FakeDisplaySettingsStore()
    private val fontStore = FakeFontSelectionStore()
    private val locationStore = FakeLocationSettingsStore()
    private val dockStore = FakeDockSettingsStore()
    private val updater = FakeUpdaterPort()
    private val updateStore = FakeUpdateSettingsStore()
    private val motion = MutableStateFlow(VehicleMotion.PARKED)
    private val manifest = fakeUpdateManifest(versionCode = 26092501)
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
            val vm = viewModel(calendarPrefs = calendarPrefs)
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
            vm.onAction(SettingsAction.SetMotionTier(MotionTier.OFF))
            vm.onAction(SettingsAction.SetShowMusic(false))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetSection(SettingsSectionId.SCREEN))
            advanceUntilIdle()

            assertEquals(FullscreenSetting.ON, store.settings.first().fullscreen)
            assertEquals(DockPosition.BOTTOM, store.settings.first().dockPosition)
            assertEquals(MotionTier.STANDARD, store.settings.first().motionTier)
            // Panels is a different section — untouched by a Screen reset.
            assertEquals(false, store.settings.first().showMusic)
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
            vm.onAction(SettingsAction.SetMapBackend(MapBackend.GOOGLEMAPS))
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
            val vm = viewModel(calendarPrefs = calendarPrefs)
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
    fun `SetGlassShowBorder writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetGlassShowBorder(true))
            advanceUntilIdle()
            assertEquals(true, store.settings.first().glassShowBorder)
        }

    @Test
    fun `SetGlassShadowEnabled writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetGlassShadowEnabled(false))
            advanceUntilIdle()
            assertEquals(false, store.settings.first().glassShadowEnabled)
        }

    @Test
    fun `SetGlassShadowIntensity writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetGlassShadowIntensity(70))
            advanceUntilIdle()
            assertEquals(70, store.settings.first().glassShadowIntensity)
        }

    @Test
    fun `SetGlassShadowSizeDp writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetGlassShadowSizeDp(16))
            advanceUntilIdle()
            assertEquals(16, store.settings.first().glassShadowSizeDp)
        }

    @Test
    fun `SetFontBaseSizeSp writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetFontBaseSizeSp(18))
            advanceUntilIdle()
            assertEquals(18, store.settings.first().fontBaseSizeSp)
        }

    @Test
    fun `SetFontWeightStep writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetFontWeightStep(2))
            advanceUntilIdle()
            assertEquals(2, store.settings.first().fontWeightStep)
        }

    @Test
    fun `SetFontLetterSpacingCentiEm writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetFontLetterSpacingCentiEm(6))
            advanceUntilIdle()
            assertEquals(6, store.settings.first().fontLetterSpacingCentiEm)
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
    fun `SetMapBackend persists and reflects in state`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetMapBackend(MapBackend.GOOGLEMAPS))
            advanceUntilIdle()
            assertEquals(MapBackend.GOOGLEMAPS, store.settings.first().mapBackend)
        }

    @Test
    fun `SetMapTileHost persists the trimmed host and ClearMapTileHost blanks it`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetMapTileHost("  https://tiles.example.test  "))
            advanceUntilIdle()
            assertEquals("https://tiles.example.test", store.settings.first().mapTileHost)

            vm.onAction(SettingsAction.ClearMapTileHost)
            advanceUntilIdle()
            assertEquals("", store.settings.first().mapTileHost)
        }

    @Test
    fun `SetMapCustomStyleUrl persists the trimmed url and ClearMapCustomStyleUrl blanks it`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetMapCustomStyleUrl("  https://example.test/style.json?key=k  "))
            advanceUntilIdle()
            assertEquals("https://example.test/style.json?key=k", store.settings.first().mapCustomStyleUrl)

            vm.onAction(SettingsAction.ClearMapCustomStyleUrl)
            advanceUntilIdle()
            assertEquals("", store.settings.first().mapCustomStyleUrl)
        }

    @Test
    fun `available calendars and hidden set surface and SetCalendarHidden toggles`() =
        runTest(dispatcher) {
            val calendarPrefs = FakeCalendarPreferencesStore()
            val vm =
                viewModel(
                    calendarPrefs = calendarPrefs,
                    availableCalendars =
                        CalendarCatalogState(
                            hasAccess = true,
                            calendars =
                                listOf(
                                    fakeCalendarInfo(id = 1L),
                                    fakeCalendarInfo(id = 2L, displayName = "Work"),
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

    @Test
    fun `SetTripAutoReset writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetTripAutoReset(TripAutoResetSetting.OFF))
            advanceUntilIdle()
            assertEquals(TripAutoResetSetting.OFF, locationStore.settings.first().tripAutoReset)
        }

    @Test
    fun `SetTripAutoReset surfaces in uiState`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect { } }
            advanceUntilIdle()

            vm.onAction(SettingsAction.SetTripAutoReset(TripAutoResetSetting.HOURS_12))
            advanceUntilIdle()
            assertEquals(TripAutoResetSetting.HOURS_12, vm.uiState.value.tripAutoReset)
        }

    @Test
    fun `ResetDock restores the dock store to its defaults`() =
        runTest(dispatcher) {
            val vm = viewModel()
            dockStore.toggleNavHidden(DockNavId.MUSIC)
            dockStore.setNavOrder(listOf(DockNavId.SETTINGS) + DockNavId.entries.filterNot { it == DockNavId.SETTINGS })
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetDock)
            advanceUntilIdle()

            assertEquals(DockNavId.entries, dockStore.navOrder.first())
            assertEquals(emptySet(), dockStore.navHidden.first())
        }

    @Test
    fun `SetDockWidth writes the value to the store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetDockWidth(DockWidth.EXTENDED))
            advanceUntilIdle()
            assertEquals(DockWidth.EXTENDED, store.settings.first().dockWidth)
        }

    @Test
    fun `SetDockStatusVisible false hides every indicator in the dock store`() =
        runTest(dispatcher) {
            viewModel().onAction(SettingsAction.SetDockStatusVisible(false))
            advanceUntilIdle()
            assertEquals(DockStatusId.entries.toSet(), dockStore.statusHidden.first())
        }

    // The switch owns no boolean of its own: it reads the dock store's hidden
    // set, so a per-indicator hide from the dock's long-press menu moves it too.
    @Test
    fun `dockStatusVisible is derived from the dock store's hidden set`() =
        runTest(dispatcher) {
            val vm = viewModel()
            // Subscribe so WhileUiSubscribed keeps the upstream combine alive across both phases.
            backgroundScope.launch { vm.uiState.collect { } }
            advanceUntilIdle()
            assertEquals(true, vm.uiState.value.dockStatusVisible)

            DockStatusId.entries.forEach { dockStore.toggleStatusHidden(it) }
            advanceUntilIdle()
            assertEquals(false, vm.uiState.value.dockStatusVisible)
        }

    // --- Updates ---------------------------------------------------------------

    @Test
    fun `a build that never checks shows the disabled status, no available version and no step`() =
        runTest(dispatcher) {
            val updates = updatesFor(UpdateState.Disabled)
            assertEquals(UpdateStatus.Disabled, updates.status)
            assertNull(updates.availableVersion)
            assertNull(updates.step)
        }

    @Test
    fun `Idle shows the persisted last attempt`() =
        runTest(dispatcher) {
            updateStore.setLastCheckAttemptAt(ATTEMPT_MS)
            assertEquals(
                UpdateStatus.Checked(Instant.ofEpochMilli(ATTEMPT_MS)),
                updatesFor(UpdateState.Idle(lastAttemptAt = null)).status,
            )
        }

    @Test
    fun `Idle without a recorded attempt has never checked`() =
        runTest(dispatcher) {
            assertEquals(UpdateStatus.NeverChecked, updatesFor(UpdateState.Idle(lastAttemptAt = null)).status)
        }

    @Test
    fun `Checking shows the checking status and offers no step`() =
        runTest(dispatcher) {
            val updates = updatesFor(UpdateState.Checking)
            assertEquals(UpdateStatus.Checking, updates.status)
            assertNull(updates.step)
        }

    @Test
    fun `the check row reports the last check, never what it found`() =
        runTest(dispatcher) {
            // The offer lives only in the "Available version" row.
            updateStore.setLastCheckAttemptAt(ATTEMPT_MS)
            listOf(
                UpdateState.UpToDate,
                UpdateState.Available(manifest),
                UpdateState.Downloading(manifest, fraction = 0.5f),
                UpdateState.Ready(manifest, File("update.apk")),
                installingWithConfirmation(),
            ).forEach { state ->
                assertEquals(
                    UpdateStatus.Checked(Instant.ofEpochMilli(ATTEMPT_MS)),
                    updatesFor(state).status,
                    "status for $state",
                )
            }
        }

    @Test
    fun `a result whose attempt the store lost shows no time rather than never checked`() =
        runTest(dispatcher) {
            // A result means a check ran; only its time is unknown.
            assertEquals(UpdateStatus.Checked(lastAttemptAt = null), updatesFor(UpdateState.UpToDate).status)
        }

    @Test
    fun `the available version row names the offer wherever the state carries one`() =
        runTest(dispatcher) {
            val offered = AvailableVersion.Offered(manifest.versionName, manifest.apk.size)
            listOf(
                UpdateState.Available(manifest),
                UpdateState.Downloading(manifest, fraction = 0.5f),
                UpdateState.Ready(manifest, File("update.apk")),
                UpdateState.Installing(manifest, sessionId = SESSION_ID),
                installingWithConfirmation(),
                UpdateState.Failed(UpdateFailure.NETWORK, manifest),
            ).forEach { state ->
                assertEquals(offered, updatesFor(state).availableVersion, "availableVersion for $state")
            }
        }

    @Test
    fun `the available version row says up to date only after a check found nothing newer`() =
        runTest(dispatcher) {
            mapOf(
                UpdateState.UpToDate to AvailableVersion.UpToDate,
                UpdateState.Idle(lastAttemptAt = null) to null,
                UpdateState.Checking to null,
                UpdateState.Failed(UpdateFailure.NETWORK, manifest = null) to null,
            ).forEach { (state, availableVersion) ->
                assertEquals(availableVersion, updatesFor(state).availableVersion, "availableVersion for $state")
            }
        }

    @Test
    fun `Available offers the one-tap update at the manifest's size before it starts`() =
        runTest(dispatcher) {
            assertEquals(
                UpdateStep.Download(manifest.versionName, manifest.apk.size, grantDeclined = false),
                updatesFor(UpdateState.Available(manifest)).step,
            )
        }

    @Test
    fun `Downloading reports its fraction in the update step`() =
        runTest(dispatcher) {
            assertEquals(
                UpdateStep.Downloading(manifest.versionName, fraction = 0.42f),
                updatesFor(UpdateState.Downloading(manifest, fraction = 0.42f)).step,
            )
        }

    @Test
    fun `Ready offers the install`() =
        runTest(dispatcher) {
            assertEquals(
                UpdateStep.Install(manifest.versionName, blockedWhileMoving = false, grantDeclined = false),
                updatesFor(UpdateState.Ready(manifest, File("update.apk"))).step,
            )
        }

    @Test
    fun `Installing before the confirmation arrives reports the hand-off`() =
        runTest(dispatcher) {
            assertEquals(
                UpdateStep.Installing(manifest.versionName),
                updatesFor(UpdateState.Installing(manifest, sessionId = SESSION_ID)).step,
            )
        }

    @Test
    fun `Installing with a kept confirmation offers the install dialog again`() =
        runTest(dispatcher) {
            assertEquals(
                UpdateStep.ShowInstallDialog(blockedWhileMoving = false, grantDeclined = false),
                updatesFor(installingWithConfirmation()).step,
            )
        }

    @Test
    fun `a failure that still names its offer offers a retry at the download's size`() =
        runTest(dispatcher) {
            // The retry may download the whole APK again, so its size shows first.
            val updates = updatesFor(UpdateState.Failed(UpdateFailure.NETWORK, manifest))
            assertEquals(UpdateStatus.Failed(UpdateFailure.NETWORK), updates.status)
            assertEquals(UpdateStep.Retry(manifest.versionName, manifest.apk.size), updates.step)
        }

    @Test
    fun `a failure that lost its offer offers no step`() =
        runTest(dispatcher) {
            val updates = updatesFor(UpdateState.Failed(UpdateFailure.INSTALL_CONFLICT, manifest = null))
            assertEquals(UpdateStatus.Failed(UpdateFailure.INSTALL_CONFLICT), updates.status)
            assertNull(updates.step)
        }

    @Test
    fun `the check row is tappable only where a check can start`() =
        runTest(dispatcher) {
            // Everywhere else the updater ignores the tap: a check is running, the
            // offer is being acted on, or the build never checks.
            mapOf(
                UpdateState.Disabled to false,
                UpdateState.Idle(lastAttemptAt = null) to true,
                UpdateState.Checking to false,
                UpdateState.UpToDate to true,
                UpdateState.Available(manifest) to true,
                UpdateState.Downloading(manifest, fraction = 0.5f) to false,
                UpdateState.Ready(manifest, File("update.apk")) to false,
                installingWithConfirmation() to false,
                UpdateState.Failed(UpdateFailure.NETWORK, manifest = null) to true,
            ).forEach { (state, canCheck) ->
                assertEquals(canCheck, updatesFor(state).canCheck, "canCheck for $state")
            }
        }

    @Test
    fun `the category list's dot follows the dock badge's offer rule`() =
        runTest(dispatcher) {
            mapOf(
                UpdateState.Disabled to false,
                UpdateState.Idle(lastAttemptAt = null) to false,
                UpdateState.Checking to false,
                UpdateState.UpToDate to false,
                UpdateState.Available(manifest) to true,
                UpdateState.Downloading(manifest, fraction = 0.5f) to true,
                UpdateState.Ready(manifest, File("update.apk")) to true,
                installingWithConfirmation() to true,
                UpdateState.Failed(UpdateFailure.NETWORK, manifest) to true,
                UpdateState.Failed(UpdateFailure.INSTALL_CONFLICT, manifest = null) to false,
            ).forEach { (state, offered) ->
                assertEquals(offered, updatesFor(state).updateOffered, "updateOffered for $state")
            }
        }

    @Test
    fun `a declined install grant marks the one-tap update step until the update starts`() =
        runTest(dispatcher) {
            // StartUpdate reaches the ViewModel only once the access is on.
            updater.state.value = UpdateState.Available(manifest)
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect { } }

            vm.onAction(SettingsAction.InstallGrantDeclined)
            advanceUntilIdle()
            assertEquals(
                UpdateStep.Download(manifest.versionName, manifest.apk.size, grantDeclined = true),
                vm.uiState.value.updates.step,
            )

            vm.onAction(SettingsAction.StartUpdate)
            advanceUntilIdle()
            assertEquals(
                UpdateStep.Download(manifest.versionName, manifest.apk.size, grantDeclined = false),
                vm.uiState.value.updates.step,
            )
        }

    @Test
    fun `a declined install grant marks the install step until an install goes ahead`() =
        runTest(dispatcher) {
            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect { } }

            vm.onAction(SettingsAction.InstallGrantDeclined)
            advanceUntilIdle()
            assertEquals(
                UpdateStep.Install(manifest.versionName, blockedWhileMoving = false, grantDeclined = true),
                vm.uiState.value.updates.step,
            )

            vm.onAction(SettingsAction.InstallUpdate)
            advanceUntilIdle()
            assertEquals(
                UpdateStep.Install(manifest.versionName, blockedWhileMoving = false, grantDeclined = false),
                vm.uiState.value.updates.step,
            )
        }

    @Test
    fun `an updater that has not resolved yet holds the rest of Settings open`() =
        runTest(dispatcher) {
            val stalled = object : UpdaterPort by updater {
                override val state: Flow<UpdateState> = flow { awaitCancellation() }
            }
            store.setShowMusic(false)
            val vm = viewModel(updaterPort = stalled)
            backgroundScope.launch { vm.uiState.collect { } }
            advanceUntilIdle()

            assertEquals(false, vm.uiState.value.showMusic)
            assertEquals(UpdatesUiState.Initial, vm.uiState.value.updates)
        }

    @Test
    fun `a failing updater costs only the Updates section`() =
        runTest(dispatcher) {
            val broken = object : UpdaterPort by updater {
                override val state: Flow<UpdateState> = flow { throw IllegalStateException("updater broke") }
            }
            store.setShowMusic(false)
            val vm = viewModel(updaterPort = broken)
            backgroundScope.launch { vm.uiState.collect { } }
            advanceUntilIdle()

            assertEquals(false, vm.uiState.value.showMusic)
            assertEquals(UpdatesUiState.Initial, vm.uiState.value.updates)
        }

    @Test
    fun `a fix showing the vehicle moving holds the install step`() =
        runTest(dispatcher) {
            motion.value = VehicleMotion.MOVING
            assertEquals(
                UpdateStep.Install(manifest.versionName, blockedWhileMoving = true, grantDeclined = false),
                updatesFor(UpdateState.Ready(manifest, File("update.apk"))).step,
            )
        }

    @Test
    fun `a fix showing the vehicle moving holds the install dialog step`() =
        runTest(dispatcher) {
            motion.value = VehicleMotion.MOVING
            assertEquals(
                UpdateStep.ShowInstallDialog(blockedWhileMoving = true, grantDeclined = false),
                updatesFor(installingWithConfirmation()).step,
            )
        }

    @Test
    fun `no fix leaves the install step open`() =
        runTest(dispatcher) {
            motion.value = VehicleMotion.UNKNOWN
            assertEquals(
                UpdateStep.Install(manifest.versionName, blockedWhileMoving = false, grantDeclined = false),
                updatesFor(UpdateState.Ready(manifest, File("update.apk"))).step,
            )
        }

    @Test
    fun `InstallUpdate installs while parked`() =
        runTest(dispatcher) {
            motion.value = VehicleMotion.PARKED
            viewModel().onAction(SettingsAction.InstallUpdate)
            advanceUntilIdle()
            assertEquals(1, updater.installs)
        }

    @Test
    fun `InstallUpdate installs without a fix`() =
        runTest(dispatcher) {
            // A phone without the location grant never gets a fix, and must still
            // be able to install the update it asked for.
            motion.value = VehicleMotion.UNKNOWN
            viewModel().onAction(SettingsAction.InstallUpdate)
            advanceUntilIdle()
            assertEquals(1, updater.installs)
        }

    @Test
    fun `InstallUpdate is ignored while a fix shows the vehicle moving`() =
        runTest(dispatcher) {
            motion.value = VehicleMotion.MOVING
            viewModel().onAction(SettingsAction.InstallUpdate)
            advanceUntilIdle()
            assertEquals(0, updater.installs)
        }

    // --- One-tap update --------------------------------------------------------

    @Test
    fun `StartUpdate downloads the offer`() =
        runTest(dispatcher) {
            updater.state.value = UpdateState.Available(manifest)
            viewModel().onAction(SettingsAction.StartUpdate)
            advanceUntilIdle()
            assertEquals(1, updater.downloads)
        }

    @Test
    fun `the one-tap update asks for the install once its download lands while parked`() =
        runTest(dispatcher) {
            val requests = startedOneTapUpdate()

            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            advanceUntilIdle()

            assertEquals(1, requests.size)
        }

    @Test
    fun `the one-tap update asks for the install without a fix, as the tap would`() =
        runTest(dispatcher) {
            motion.value = VehicleMotion.UNKNOWN
            val requests = startedOneTapUpdate()

            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            advanceUntilIdle()

            assertEquals(1, requests.size)
        }

    @Test
    fun `a download that lands while moving stops the one-tap update for good`() =
        runTest(dispatcher) {
            val requests = startedOneTapUpdate()

            motion.value = VehicleMotion.MOVING
            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            advanceUntilIdle()
            // Stopping later must not pop the install up by itself: the row
            // waits for a tap.
            motion.value = VehicleMotion.PARKED
            advanceUntilIdle()

            assertEquals(0, requests.size)
        }

    @Test
    fun `a failed download ends the one-tap update`() =
        runTest(dispatcher) {
            val requests = startedOneTapUpdate()

            updater.state.value = UpdateState.Failed(UpdateFailure.NETWORK, manifest)
            advanceUntilIdle()
            // A retry is a plain download: its verified file waits for a tap.
            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            advanceUntilIdle()

            assertEquals(0, requests.size)
        }

    @Test
    fun `leaving the Updates section ends the one-tap update`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val requests = startedOneTapUpdate(vm)

            vm.onAction(SettingsAction.UpdatesHidden)
            advanceUntilIdle()
            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            advanceUntilIdle()

            assertEquals(0, requests.size)
        }

    @Test
    fun `the one-tap update installs a build already downloaded at once`() =
        runTest(dispatcher) {
            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            val vm = viewModel()
            val requests = installRequestsOf(vm)

            vm.onAction(SettingsAction.StartUpdate)
            advanceUntilIdle()

            assertEquals(1, requests.size)
        }

    @Test
    fun `an install request waits for the screen to start collecting`() =
        runTest(dispatcher) {
            // Settings opened by the dashboard's prompt on a build already
            // downloaded: the verdict can land before the screen collects.
            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            val vm = viewModel()
            vm.onAction(SettingsAction.StartUpdate)
            advanceUntilIdle()

            val requests = installRequestsOf(vm)
            advanceUntilIdle()

            assertEquals(1, requests.size)
        }

    @Test
    fun `the one-tap update asks for the install only once`() =
        runTest(dispatcher) {
            val requests = startedOneTapUpdate()
            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            advanceUntilIdle()

            // The install went ahead and came back declined: the verified file
            // is offered again, for a tap.
            updater.state.value = UpdateState.Installing(manifest, sessionId = SESSION_ID)
            advanceUntilIdle()
            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            advanceUntilIdle()

            assertEquals(1, requests.size)
        }

    @Test
    fun `a verified download without the one-tap update waits for a tap`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val requests = installRequestsOf(vm)

            updater.state.value = UpdateState.Ready(manifest, File("update.apk"))
            advanceUntilIdle()

            assertEquals(0, requests.size)
        }

    // --- Offers seen in Settings ------------------------------------------------

    @Test
    fun `an offer the Updates section shows counts as prompted`() =
        runTest(dispatcher) {
            // The dashboard's prompt then never asks about what the user read here.
            updater.state.value = UpdateState.Available(manifest)
            val vm = viewModel()

            vm.onAction(SettingsAction.UpdatesShown)
            advanceUntilIdle()

            assertEquals(manifest.versionCode, updateStore.current.promptedVersionCode)
        }

    @Test
    fun `an offer found while the Updates section shows counts as prompted`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.UpdatesShown)
            advanceUntilIdle()

            updater.state.value = UpdateState.Available(manifest)
            advanceUntilIdle()

            assertEquals(manifest.versionCode, updateStore.current.promptedVersionCode)
        }

    @Test
    fun `an offer the Updates section never showed is not recorded`() =
        runTest(dispatcher) {
            viewModel()
            updater.state.value = UpdateState.Available(manifest)
            advanceUntilIdle()

            assertNull(updateStore.current.promptedVersionCode)
        }

    @Test
    fun `an offer found after the Updates section left is not recorded`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.UpdatesShown)
            advanceUntilIdle()
            vm.onAction(SettingsAction.UpdatesHidden)
            advanceUntilIdle()

            updater.state.value = UpdateState.Available(manifest)
            advanceUntilIdle()

            assertNull(updateStore.current.promptedVersionCode)
        }

    @Test
    fun `CheckForUpdates asks the updater to check, whatever the motion`() =
        runTest(dispatcher) {
            motion.value = VehicleMotion.MOVING
            viewModel().onAction(SettingsAction.CheckForUpdates)
            advanceUntilIdle()
            assertEquals(1, updater.checks)
        }

    @Test
    fun `DownloadUpdate asks the updater to download, whatever the motion`() =
        runTest(dispatcher) {
            motion.value = VehicleMotion.MOVING
            viewModel().onAction(SettingsAction.DownloadUpdate)
            advanceUntilIdle()
            assertEquals(1, updater.downloads)
        }

    @Test
    fun `SetUpdateAutoCheck writes the update store and surfaces in uiState`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect { } }
            vm.onAction(SettingsAction.SetUpdateAutoCheck(false))
            advanceUntilIdle()
            assertEquals(false, updateStore.current.autoCheck)
            assertEquals(false, vm.uiState.value.updates.autoCheck)
        }

    @Test
    fun `the updated-to notice surfaces until acknowledged`() =
        runTest(dispatcher) {
            updater.updatedTo.value = "2026.09.25-1"
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect { } }
            advanceUntilIdle()
            assertEquals("2026.09.25-1", vm.uiState.value.updates.updatedTo)

            vm.onAction(SettingsAction.AcknowledgeUpdatedTo)
            advanceUntilIdle()
            assertNull(vm.uiState.value.updates.updatedTo)
        }

    @Test
    fun `ResetSection(UPDATES) restores the auto-check default, leaves the display store alone`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetUpdateAutoCheck(false))
            vm.onAction(SettingsAction.SetMapZoom(11))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetSection(SettingsSectionId.UPDATES))
            advanceUntilIdle()

            assertEquals(UpdateSettings.Default.autoCheck, updateStore.current.autoCheck)
            // UPDATES owns no DisplayPreferences key, so the display store is untouched.
            assertEquals(11, store.settings.first().mapZoom)
        }

    @Test
    fun `ResetToDefaults also restores the auto-check default`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.SetUpdateAutoCheck(false))
            advanceUntilIdle()

            vm.onAction(SettingsAction.ResetToDefaults)
            advanceUntilIdle()

            assertEquals(UpdateSettings.Default.autoCheck, updateStore.current.autoCheck)
        }

    // Subscribes (WhileUiSubscribed runs the combine only while collected), stages
    // [state] and returns the section's state once it has settled.
    private fun TestScope.updatesFor(state: UpdateState): UpdatesUiState {
        updater.state.value = state
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect { } }
        advanceUntilIdle()
        return vm.uiState.value.updates
    }

    // Every install request [vm] makes from now on. Collected unconfined, so a
    // request lands at once: advanceUntilIdle does not wait for background work.
    private fun TestScope.installRequestsOf(vm: SettingsViewModel): List<Unit> =
        mutableListOf<Unit>().also { requests ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.installRequests.toList(requests) }
        }

    // Starts the one-tap update on an offer and lets its download begin; returns
    // the install requests [vm] makes from then on.
    private fun TestScope.startedOneTapUpdate(vm: SettingsViewModel = viewModel()): List<Unit> {
        val requests = installRequestsOf(vm)
        updater.state.value = UpdateState.Available(manifest)
        vm.onAction(SettingsAction.StartUpdate)
        advanceUntilIdle()
        updater.state.value = UpdateState.Downloading(manifest, fraction = 0.5f)
        advanceUntilIdle()
        return requests
    }

    private fun installingWithConfirmation() =
        UpdateState.Installing(manifest, sessionId = SESSION_ID, confirmation = FakeInstallConfirmation())

    private fun viewModel(
        calendarPrefs: FakeCalendarPreferencesStore = FakeCalendarPreferencesStore(),
        availableCalendars: CalendarCatalogState = CalendarCatalogState(hasAccess = true, calendars = emptyList()),
        updaterPort: UpdaterPort = updater,
    ) = SettingsViewModel(
        store,
        fontStore,
        locationStore,
        calendarPrefs,
        dockStore,
        trackLog = FakeTrackLogPort(),
        availableCalendars = flowOf(availableCalendars),
        updater = updaterPort,
        updatePreferences = updateStore,
        motion = motion,
    )

    private companion object {
        const val ATTEMPT_MS = 1_790_000_000_000L
        const val SESSION_ID = 7
    }
}
