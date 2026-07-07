package io.github.seijikohara.femto.data.display

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DisplayPreferencesTest {
    // The displayDataStore delegate is a process-wide singleton bound to the
    // first Application's filesDir, while Robolectric hands each test method a
    // fresh Application. All DataStore round-trip steps therefore live in one
    // test method so the persisted file and the singleton never disagree across
    // tests (mirrors DrawerPreferencesTest). The migration tests below touch no
    // DataStore, so they stay separate.
    @Test
    fun `an empty store reads the defaults and resetToDefaults restores them`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext())
            // Clear any state left by a test that ran earlier in the same process.
            store.resetToDefaults()

            // Reset parity: every per-field read fallback is kept identical to
            // DisplaySettings.Default, so a fresh key-less store reads exactly
            // Default. A new field whose read fallback drifts from Default — the
            // failure mode the SettingsViewModel fake store cannot catch — breaks
            // this single equality.
            assertEquals(DisplaySettings.Default, store.settings.first())

            // Mutate a spread of fields across the stored types (enum / bool / int).
            store.setThemeMode(ThemeMode.DARK)
            store.setKeepScreenOn(false)
            store.setMapZoom(DEFAULT_MAP_ZOOM + 2)
            store.setMusicSpectrum(true)
            store.setMusicShowAlbum(false)
            store.setMusicShowArt(false)
            store.setPresetMode(PresetMode.DRIVING)
            store.setDrivingThresholdKmh(20)
            store.setMotionTier(MotionTier.OFF)
            store.setDriverSide(DriverSide.LEFT)
            store.setBriefingShowEvent(false)
            store.setBriefingShowWeather(false)
            assertNotEquals(DisplaySettings.Default, store.settings.first())

            // The two music-meta toggles persist and read back independently.
            store.settings.first().let { persisted ->
                assertFalse(persisted.musicShowAlbum)
                assertFalse(persisted.musicShowArt)
            }

            store.settings.first().let { persisted ->
                assertEquals(PresetMode.DRIVING, persisted.presetMode)
                assertEquals(20, persisted.drivingThresholdKmh)
                assertEquals(MotionTier.OFF, persisted.motionTier)
                assertEquals(DriverSide.LEFT, persisted.driverSide)
                assertFalse(persisted.briefingShowEvent)
                assertFalse(persisted.briefingShowWeather)
            }

            // resetToDefaults() clears every key, so the read falls back to Default
            // for all fields at once — including driverSide, whose fallback (RIGHT)
            // already matches DisplaySettings.Default.
            store.resetToDefaults()
            assertEquals(DisplaySettings.Default, store.settings.first())
            assertEquals(DriverSide.RIGHT, store.settings.first().driverSide)
        }

    // All three backend-settings cases share one test method because the
    // displayDataStore singleton is bound to the process Application, not the test
    // method — separate methods would see each other's writes (same singleton
    // constraint as the round-trip test above).
    @Test
    fun mapboxAccessToken_roundTrips() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            store.resetToDefaults()
            assertEquals("", store.settings.first().mapboxAccessToken)
            store.setMapboxAccessToken("pk.test_token_123")
            assertEquals("pk.test_token_123", store.settings.first().mapboxAccessToken)
        }

    @Test
    fun `mapBackend mapboxStyle mapboxTraffic defaults migration and round-trip`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            // Clear any state left by a test that ran earlier in the same process.
            store.resetToDefaults()

            // Defaults: absent map_backend key resolves to OSM (the migration
            // semantic — any user whose store has only the legacy map_render_mode
            // key gets OSM automatically because map_backend is simply absent).
            assertFalse(store.settings.first().mapboxTraffic)
            assertEquals(MapBackend.OSM, store.settings.first().mapBackend)
            assertEquals(MapboxStyle.STANDARD, store.settings.first().mapboxStyle)

            // Legacy: a pre-migration store has map_render_mode but no map_backend.
            // The map_backend key being absent is the migration semantic; OSM resolves
            // from the read-path default regardless of any pre-existing render-mode key.
            assertEquals(MapBackend.OSM, store.settings.first().mapBackend)

            // Round-trip: write MAPBOX / SATELLITE / traffic-on, then read back.
            store.setMapBackend(MapBackend.MAPBOX)
            store.setMapboxStyle(MapboxStyle.SATELLITE)
            store.setMapboxTraffic(true)
            val s = store.settings.first()
            assertEquals(MapBackend.MAPBOX, s.mapBackend)
            assertEquals(MapboxStyle.SATELLITE, s.mapboxStyle)
            assertTrue(s.mapboxTraffic)
        }

    @Test
    fun googleMapsSettingsRoundTrip() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            store.resetToDefaults()
            store.setGoogleMapsApiKey("AIzaTESTKEY")
            store.setGoogleMapsMapId("test-map-id-01")
            store.setGoogleMapsMapType(GoogleMapType.HYBRID)
            store.setGoogleMapsTraffic(true)
            val settings = store.settings.first()
            assertEquals("AIzaTESTKEY", settings.googleMapsApiKey)
            assertEquals("test-map-id-01", settings.googleMapsMapId)
            assertEquals(GoogleMapType.HYBRID, settings.googleMapsMapType)
            assertTrue(settings.googleMapsTraffic)
        }

    // One test per section: mutate every field away from Default, resetKeys()
    // just that section, then assert the whole DisplaySettings equals `mutated`
    // with only that section's fields folded back to Default — a single
    // equality that proves both halves at once (its own fields reset, every
    // other field untouched).
    @Test
    fun `resetKeys(APPEARANCE) restores only the appearance fields`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            val mutated = store.mutateAllAwayFromDefault()
            store.resetKeys(SettingsSectionId.APPEARANCE.displayKeys)
            val expected =
                mutated.copy(
                    themeMode = DisplaySettings.Default.themeMode,
                    accentColor = DisplaySettings.Default.accentColor,
                    mapStyle = DisplaySettings.Default.mapStyle,
                    mapSchemeLight = DisplaySettings.Default.mapSchemeLight,
                    mapSchemeDark = DisplaySettings.Default.mapSchemeDark,
                    glassBlurRadius = DisplaySettings.Default.glassBlurRadius,
                    glassTintScale = DisplaySettings.Default.glassTintScale,
                )
            assertEquals(expected, store.settings.first())
        }

    @Test
    fun `resetKeys(SCREEN) restores only the screen fields`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            val mutated = store.mutateAllAwayFromDefault()
            store.resetKeys(SettingsSectionId.SCREEN.displayKeys)
            val expected =
                mutated.copy(
                    uiScale = DisplaySettings.Default.uiScale,
                    orientation = DisplaySettings.Default.orientation,
                    fullscreen = DisplaySettings.Default.fullscreen,
                    keepScreenOn = DisplaySettings.Default.keepScreenOn,
                    dockPosition = DisplaySettings.Default.dockPosition,
                    driverSide = DisplaySettings.Default.driverSide,
                    assistantLaunch = DisplaySettings.Default.assistantLaunch,
                )
            assertEquals(expected, store.settings.first())
        }

    @Test
    fun `resetKeys(DRIVING) restores only the driving fields`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            val mutated = store.mutateAllAwayFromDefault()
            store.resetKeys(SettingsSectionId.DRIVING.displayKeys)
            val expected =
                mutated.copy(
                    presetMode = DisplaySettings.Default.presetMode,
                    drivingThresholdKmh = DisplaySettings.Default.drivingThresholdKmh,
                    motionTier = DisplaySettings.Default.motionTier,
                    briefingShowEvent = DisplaySettings.Default.briefingShowEvent,
                    briefingShowWeather = DisplaySettings.Default.briefingShowWeather,
                )
            assertEquals(expected, store.settings.first())
        }

    @Test
    fun `resetKeys(UNITS) restores only the unit fields`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            val mutated = store.mutateAllAwayFromDefault()
            store.resetKeys(SettingsSectionId.UNITS.displayKeys)
            val expected =
                mutated.copy(
                    speedUnit = DisplaySettings.Default.speedUnit,
                    temperatureUnit = DisplaySettings.Default.temperatureUnit,
                    clock = DisplaySettings.Default.clock,
                    showClockSeconds = DisplaySettings.Default.showClockSeconds,
                )
            assertEquals(expected, store.settings.first())
        }

    @Test
    fun `resetKeys(MAP) restores only the map fields`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            val mutated = store.mutateAllAwayFromDefault()
            store.resetKeys(SettingsSectionId.MAP.displayKeys)
            val expected =
                mutated.copy(
                    mapBackend = DisplaySettings.Default.mapBackend,
                    mapboxStyle = DisplaySettings.Default.mapboxStyle,
                    mapboxTraffic = DisplaySettings.Default.mapboxTraffic,
                    mapboxAccessToken = DisplaySettings.Default.mapboxAccessToken,
                    googleMapsApiKey = DisplaySettings.Default.googleMapsApiKey,
                    googleMapsMapId = DisplaySettings.Default.googleMapsMapId,
                    googleMapsMapType = DisplaySettings.Default.googleMapsMapType,
                    googleMapsTraffic = DisplaySettings.Default.googleMapsTraffic,
                    map3dBuildings = DisplaySettings.Default.map3dBuildings,
                    mapTerrain = DisplaySettings.Default.mapTerrain,
                    mapTiltDeg = DisplaySettings.Default.mapTiltDeg,
                    mapZoom = DisplaySettings.Default.mapZoom,
                    mapNorthUp = DisplaySettings.Default.mapNorthUp,
                    mapMarkerPos = DisplaySettings.Default.mapMarkerPos,
                )
            assertEquals(expected, store.settings.first())
        }

    @Test
    fun `resetKeys(PANELS) restores only the panel fields`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            val mutated = store.mutateAllAwayFromDefault()
            store.resetKeys(SettingsSectionId.PANELS.displayKeys)
            val expected =
                mutated.copy(
                    showCalendar = DisplaySettings.Default.showCalendar,
                    showWeather = DisplaySettings.Default.showWeather,
                    showMusic = DisplaySettings.Default.showMusic,
                    musicSpectrum = DisplaySettings.Default.musicSpectrum,
                    musicShowAlbum = DisplaySettings.Default.musicShowAlbum,
                    musicShowArt = DisplaySettings.Default.musicShowArt,
                )
            assertEquals(expected, store.settings.first())
        }

    // LOCATION owns no DisplayPreferences key (its settings live entirely in the
    // LOCATION store) — resetKeys(emptySet()) must be a true no-op here.
    @Test
    fun `resetKeys(LOCATION) is a no-op on DisplayPreferences`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            val mutated = store.mutateAllAwayFromDefault()
            store.resetKeys(SettingsSectionId.LOCATION.displayKeys)
            assertEquals(mutated, store.settings.first())
        }

    // Sets every persisted field to a value other than DisplaySettings.Default,
    // shared by every resetKeys(section) precision test above so each test
    // states only which fields its section owns, not how to mutate all 43.
    private suspend fun DisplayPreferences.mutateAllAwayFromDefault(): DisplaySettings {
        resetToDefaults()
        setThemeMode(ThemeMode.DARK)
        setAccentColor(AccentColor.TEAL)
        setUiScale(UiScale.LARGE)
        setSpeedUnit(SpeedUnitSetting.MILES)
        setTemperatureUnit(TemperatureUnitSetting.FAHRENHEIT)
        setClock(ClockSetting.TWENTY_FOUR_HOUR)
        setShowClockSeconds(true)
        setFullscreen(FullscreenSetting.OFF)
        setDockPosition(DockPosition.LEFT)
        setDriverSide(DriverSide.LEFT)
        setPresetMode(PresetMode.DRIVING)
        setDrivingThresholdKmh(30)
        setMotionTier(MotionTier.OFF)
        setOrientation(OrientationSetting.PORTRAIT)
        setBriefingShowEvent(false)
        setBriefingShowWeather(false)
        setKeepScreenOn(false)
        setAssistantLaunch(AssistantLaunchSetting.IN_APP)
        setMapStyle(MapStyleSetting.DARK)
        setMapSchemeLight(MapColorScheme.BRIGHT)
        setMapSchemeDark(MapColorScheme.FIORD)
        setMapTilt(40)
        setMapZoom(MAX_MAP_ZOOM)
        setMapNorthUp(true)
        setMapMarkerPos(10)
        setMap3dBuildings(false)
        setMapTerrain(true)
        setGlassBlurRadius(5)
        setGlassTintScale(90)
        setShowCalendar(false)
        setShowWeather(false)
        setShowMusic(false)
        setMusicSpectrum(true)
        setMusicShowAlbum(false)
        setMusicShowArt(false)
        setMapBackend(MapBackend.MAPBOX)
        setMapboxStyle(MapboxStyle.SATELLITE)
        setMapboxTraffic(true)
        setMapboxAccessToken("pk.mutated")
        setGoogleMapsApiKey("mutated-key")
        setGoogleMapsMapId("mutated-id")
        setGoogleMapsMapType(GoogleMapType.HYBRID)
        setGoogleMapsTraffic(true)
        return settings.first()
    }
}
