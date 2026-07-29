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
    // fresh Application. Every test below therefore resets the store itself
    // (resetToDefaults(), directly or via mutateAllAwayFromDefault()) rather than
    // relying on test order, so the persisted file and the singleton never
    // disagree across tests. DrawerPreferencesTest hits the same constraint but
    // copes differently, keeping every round-trip step in a single test method.
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
            store.setMotionTier(MotionTier.OFF)
            store.setDriverSide(DriverSide.LEFT)
            assertNotEquals(DisplaySettings.Default, store.settings.first())

            // The two music-meta toggles persist and read back independently.
            store.settings.first().let { persisted ->
                assertFalse(persisted.musicShowAlbum)
                assertFalse(persisted.musicShowArt)
            }

            store.settings.first().let { persisted ->
                assertEquals(MotionTier.OFF, persisted.motionTier)
                assertEquals(DriverSide.LEFT, persisted.driverSide)
            }

            // resetToDefaults() clears every key, so the read falls back to Default
            // for all fields at once — including driverSide, whose fallback (RIGHT)
            // already matches DisplaySettings.Default.
            store.resetToDefaults()
            assertEquals(DisplaySettings.Default, store.settings.first())
            assertEquals(DriverSide.RIGHT, store.settings.first().driverSide)
        }

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
    fun `mapBackend mapboxStyle mapboxTraffic read their defaults and round-trip`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            // Clear any state left by a test that ran earlier in the same process.
            store.resetToDefaults()

            // Defaults: with no backend keys written the read path falls back to
            // OSM / STANDARD / traffic-off. There is no legacy-key migration to
            // exercise here — the read path never reads the retired map_render_mode
            // key, so a pre-rename store resolves to OSM purely because map_backend
            // is absent (asserted once, above; a second identical assertion would
            // prove nothing further).
            assertFalse(store.settings.first().mapboxTraffic)
            assertEquals(MapBackend.OSM, store.settings.first().mapBackend)
            assertEquals(MapboxStyle.STANDARD, store.settings.first().mapboxStyle)

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
            store.setGoogleMapsRendering(GoogleMapsRendering.VECTOR)
            store.setGoogleMapsMapType(GoogleMapType.HYBRID)
            store.setGoogleMapsTraffic(true)
            val settings = store.settings.first()
            assertEquals("AIzaTESTKEY", settings.googleMapsApiKey)
            assertEquals("test-map-id-01", settings.googleMapsMapId)
            assertEquals(GoogleMapType.HYBRID, settings.googleMapsMapType)
            assertTrue(settings.googleMapsTraffic)
        }

    @Test
    fun `font adjustment settings read their defaults and round-trip`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            store.resetToDefaults()

            // Defaults: with no font keys written the read path falls back to the
            // theme baseline (16sp base, no weight shift, no tracking).
            store.settings.first().let { s ->
                assertEquals(DEFAULT_FONT_BASE_SIZE_SP, s.fontBaseSizeSp)
                assertEquals(DEFAULT_FONT_WEIGHT_STEP, s.fontWeightStep)
                assertEquals(DEFAULT_FONT_LETTER_SPACING_CENTI_EM, s.fontLetterSpacingCentiEm)
            }

            // Round-trip: write non-default values (including a negative weight step
            // and centi-em spacing), then read them back.
            store.setFontBaseSizeSp(18)
            store.setFontWeightStep(-1)
            store.setFontLetterSpacingCentiEm(6)
            store.settings.first().let { s ->
                assertEquals(18, s.fontBaseSizeSp)
                assertEquals(-1, s.fontWeightStep)
                assertEquals(6, s.fontLetterSpacingCentiEm)
            }
        }

    @Test
    fun `glass chrome settings read their defaults and round-trip`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext<Context>())
            store.resetToDefaults()

            // Defaults: with no glass-chrome keys written the read path falls back to
            // border off / shadow on at the shared intensity / size constants.
            store.settings.first().let { s ->
                assertFalse(s.glassShowBorder)
                assertTrue(s.glassShadowEnabled)
                assertEquals(DEFAULT_GLASS_SHADOW_INTENSITY, s.glassShadowIntensity)
                assertEquals(DEFAULT_GLASS_SHADOW_SIZE_DP, s.glassShadowSizeDp)
            }

            // Round-trip: move each toggle off its default (border on, shadow off)
            // and write non-default intensity / size.
            store.setGlassShowBorder(true)
            store.setGlassShadowEnabled(false)
            store.setGlassShadowIntensity(70)
            store.setGlassShadowSizeDp(16)
            store.settings.first().let { s ->
                assertTrue(s.glassShowBorder)
                assertFalse(s.glassShadowEnabled)
                assertEquals(70, s.glassShadowIntensity)
                assertEquals(16, s.glassShadowSizeDp)
            }
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
                    glassShowBorder = DisplaySettings.Default.glassShowBorder,
                    glassShadowEnabled = DisplaySettings.Default.glassShadowEnabled,
                    glassShadowIntensity = DisplaySettings.Default.glassShadowIntensity,
                    glassShadowSizeDp = DisplaySettings.Default.glassShadowSizeDp,
                    fontBaseSizeSp = DisplaySettings.Default.fontBaseSizeSp,
                    fontWeightStep = DisplaySettings.Default.fontWeightStep,
                    fontLetterSpacingCentiEm = DisplaySettings.Default.fontLetterSpacingCentiEm,
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
                    motionTier = DisplaySettings.Default.motionTier,
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
                    googleMapsRendering = DisplaySettings.Default.googleMapsRendering,
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

    // ALL_KEYS and SettingsSectionIdTest's completeness check are both
    // hand-typed sets compared against each other — a key omitted from BOTH
    // would still pass that comparison and silently escape every section's
    // reset. This test grounds ALL_KEYS in the real persisted surface instead:
    // it writes through every setter via mutateAllAwayFromDefault(), then reads
    // the DataStore's actual key set and asserts it is exactly ALL_KEYS. A
    // setter added for a new field without a matching ALL_KEYS entry leaves an
    // extra real key behind and fails this equality.
    @Test
    fun `every real persisted key exactly equals ALL_KEYS`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = DisplayPreferences(context)
            store.mutateAllAwayFromDefault()

            val persistedKeys = context.displayDataStore.data
                .first()
                .asMap()
                .keys
            assertEquals(DisplayPreferences.ALL_KEYS, persistedKeys)
        }

    // Sets every persisted field to a value other than DisplaySettings.Default,
    // shared by every resetKeys(section) precision test above so each test
    // states only which fields its section owns, not how to mutate them all.
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
        setMotionTier(MotionTier.OFF)
        setOrientation(OrientationSetting.PORTRAIT)
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
        setGlassShowBorder(true)
        setGlassShadowEnabled(false)
        setGlassShadowIntensity(70)
        setGlassShadowSizeDp(16)
        setFontBaseSizeSp(20)
        setFontWeightStep(2)
        setFontLetterSpacingCentiEm(8)
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
        setGoogleMapsRendering(GoogleMapsRendering.VECTOR)
        setGoogleMapsMapType(GoogleMapType.HYBRID)
        setGoogleMapsTraffic(true)
        return settings.first()
    }
}
