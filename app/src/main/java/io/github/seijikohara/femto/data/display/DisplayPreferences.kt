package io.github.seijikohara.femto.data.display

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import io.github.seijikohara.femto.data.common.toEnumOr
import io.github.seijikohara.femto.data.fonts.FontPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "DisplayPreferences"

// Internal (not private): DisplayPreferencesTest reads the raw persisted key
// set off this same DataStore instance to validate ALL_KEYS against reality,
// rather than against another hand-typed set.
internal val Context.displayDataStore: DataStore<Preferences> by preferencesDataStore(name = "display_preferences")

/**
 * Read/write surface for [DisplaySettings]. [DisplayPreferences] is the
 * DataStore-backed production implementation; tests substitute an in-memory fake
 * so the view-model can be exercised without real DataStore IO.
 */
internal interface DisplaySettingsStore {
    val settings: Flow<DisplaySettings>

    suspend fun setThemeMode(value: ThemeMode)

    suspend fun setAccentColor(value: AccentColor)

    suspend fun setUiScale(value: UiScale)

    suspend fun setSpeedUnit(value: SpeedUnitSetting)

    suspend fun setTemperatureUnit(value: TemperatureUnitSetting)

    suspend fun setClock(value: ClockSetting)

    suspend fun setShowClockSeconds(value: Boolean)

    suspend fun setFullscreen(value: FullscreenSetting)

    suspend fun setDockPosition(value: DockPosition)

    suspend fun setDriverSide(value: DriverSide)

    suspend fun setMotionTier(value: MotionTier)

    suspend fun setOrientation(value: OrientationSetting)

    suspend fun setKeepScreenOn(value: Boolean)

    suspend fun setAssistantLaunch(value: AssistantLaunchSetting)

    suspend fun setMapStyle(value: MapStyleSetting)

    suspend fun setMapSchemeLight(value: MapColorScheme)

    suspend fun setMapSchemeDark(value: MapColorScheme)

    suspend fun setMapTilt(value: Int)

    suspend fun setMapZoom(value: Int)

    /**
     * Step the persisted map zoom by [delta], clamped to
     * [MIN_MAP_ZOOM]..[MAX_MAP_ZOOM] — the same bounds the settings slider
     * uses, so the on-map buttons and the slider stay in lock-step. Atomic
     * read-modify-write: rapid taps must not recompute from a stale UI
     * snapshot and lose steps.
     */
    suspend fun adjustMapZoom(delta: Int)

    suspend fun setMapNorthUp(value: Boolean)

    /** Flip north-up ⇄ heading-up atomically (the on-map compass tap). */
    suspend fun toggleMapNorthUp()

    suspend fun setMapMarkerPos(value: Int)

    suspend fun setMap3dBuildings(value: Boolean)

    suspend fun setMapTerrain(value: Boolean)

    suspend fun setGlassBlurRadius(value: Int)

    suspend fun setGlassTintScale(value: Int)

    suspend fun setGlassShowBorder(value: Boolean)

    suspend fun setGlassShadowEnabled(value: Boolean)

    suspend fun setGlassShadowIntensity(value: Int)

    suspend fun setGlassShadowSizeDp(value: Int)

    suspend fun setFontBaseSizeSp(value: Int)

    suspend fun setFontWeightStep(value: Int)

    suspend fun setFontLetterSpacingCentiEm(value: Int)

    suspend fun setShowCalendar(value: Boolean)

    suspend fun setShowWeather(value: Boolean)

    suspend fun setShowMusic(value: Boolean)

    suspend fun setMusicSpectrum(value: Boolean)

    suspend fun setMusicShowAlbum(value: Boolean)

    suspend fun setMusicShowArt(value: Boolean)

    suspend fun setMapBackend(value: MapBackend)

    suspend fun setMapboxStyle(value: MapboxStyle)

    suspend fun setMapboxTraffic(value: Boolean)

    suspend fun setMapboxAccessToken(value: String)

    suspend fun setGoogleMapsApiKey(value: String)

    suspend fun setGoogleMapsMapId(value: String)

    suspend fun setGoogleMapsMapType(value: GoogleMapType)

    suspend fun setGoogleMapsTraffic(value: Boolean)

    /**
     * Remove exactly [keys] so their read falls back to [DisplaySettings.Default]
     * for those fields only, leaving every other persisted field untouched.
     * [SettingsSectionId]'s "reset this section" affordance drives this with
     * one section's [SettingsSectionId.displayKeys].
     */
    suspend fun resetKeys(keys: Set<Preferences.Key<*>>)

    /** Restore every display setting to [DisplaySettings.Default]. */
    suspend fun resetToDefaults()
}

/**
 * DataStore-backed accessor for [DisplaySettings].
 *
 * Each enum is stored by name and decoded defensively (an unknown / renamed
 * value falls back to the auto default) so a downgrade or a renamed entry never
 * crashes the read path. Modelled on [FontPreferences].
 */
internal class DisplayPreferences(
    private val context: Context,
) : DisplaySettingsStore {
    override val settings: Flow<DisplaySettings> =
        context.displayDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                DisplaySettings(
                    themeMode = prefs[THEME_KEY].toEnumOr(ThemeMode.SYSTEM),
                    accentColor = prefs[ACCENT_KEY].toEnumOr(AccentColor.DYNAMIC),
                    uiScale = prefs[UI_SCALE_KEY].toEnumOr(UiScale.MEDIUM),
                    speedUnit = prefs[SPEED_KEY].toEnumOr(SpeedUnitSetting.AUTO),
                    temperatureUnit = prefs[TEMPERATURE_KEY].toEnumOr(TemperatureUnitSetting.AUTO),
                    clock = prefs[CLOCK_KEY].toEnumOr(ClockSetting.AUTO),
                    showClockSeconds = prefs[SHOW_CLOCK_SECONDS_KEY] ?: false,
                    fullscreen = prefs[FULLSCREEN_KEY].toEnumOr(FullscreenSetting.ON),
                    dockPosition = prefs[DOCK_POSITION_KEY].toEnumOr(DockPosition.BOTTOM),
                    driverSide = prefs[DRIVER_SIDE_KEY].toEnumOr(DriverSide.RIGHT),
                    motionTier = prefs[MOTION_TIER_KEY].toEnumOr(MotionTier.STANDARD),
                    orientation = prefs[ORIENTATION_KEY].toEnumOr(OrientationSetting.AUTO),
                    keepScreenOn = prefs[KEEP_SCREEN_ON_KEY] ?: true,
                    assistantLaunch = prefs[ASSISTANT_LAUNCH_KEY].toEnumOr(AssistantLaunchSetting.SYSTEM),
                    mapStyle = prefs[MAP_STYLE_KEY].toEnumOr(MapStyleSetting.AUTO),
                    mapSchemeLight = prefs[MAP_SCHEME_LIGHT_KEY].toEnumOr(MapColorScheme.ACCENT),
                    mapSchemeDark = prefs[MAP_SCHEME_DARK_KEY].toEnumOr(MapColorScheme.ACCENT),
                    mapTiltDeg = prefs[MAP_TILT_KEY] ?: DEFAULT_MAP_TILT_DEG,
                    mapZoom = prefs[MAP_ZOOM_KEY] ?: DEFAULT_MAP_ZOOM,
                    mapNorthUp = prefs[MAP_NORTH_UP_KEY] ?: false,
                    mapMarkerPos = prefs[MAP_MARKER_POS_KEY] ?: DEFAULT_MAP_MARKER_POS,
                    map3dBuildings = prefs[MAP_3D_BUILDINGS_KEY] ?: true,
                    mapTerrain = prefs[MAP_TERRAIN_KEY] ?: false,
                    glassBlurRadius = prefs[GLASS_BLUR_KEY] ?: DEFAULT_GLASS_BLUR_DP,
                    glassTintScale = prefs[GLASS_TINT_KEY] ?: DEFAULT_GLASS_TINT_SCALE,
                    glassShowBorder = prefs[GLASS_SHOW_BORDER_KEY] ?: false,
                    glassShadowEnabled = prefs[GLASS_SHADOW_ENABLED_KEY] ?: false,
                    glassShadowIntensity = prefs[GLASS_SHADOW_INTENSITY_KEY] ?: DEFAULT_GLASS_SHADOW_INTENSITY,
                    glassShadowSizeDp = prefs[GLASS_SHADOW_SIZE_KEY] ?: DEFAULT_GLASS_SHADOW_SIZE_DP,
                    fontBaseSizeSp = prefs[FONT_BASE_SIZE_KEY] ?: DEFAULT_FONT_BASE_SIZE_SP,
                    fontWeightStep = prefs[FONT_WEIGHT_STEP_KEY] ?: DEFAULT_FONT_WEIGHT_STEP,
                    fontLetterSpacingCentiEm = prefs[FONT_LETTER_SPACING_KEY] ?: DEFAULT_FONT_LETTER_SPACING_CENTI_EM,
                    showCalendar = prefs[SHOW_CALENDAR_KEY] ?: true,
                    showWeather = prefs[SHOW_WEATHER_KEY] ?: true,
                    showMusic = prefs[SHOW_MUSIC_KEY] ?: true,
                    musicSpectrum = prefs[MUSIC_SPECTRUM_KEY] ?: false,
                    musicShowAlbum = prefs[MUSIC_SHOW_ALBUM_KEY] ?: true,
                    musicShowArt = prefs[MUSIC_SHOW_ART_KEY] ?: true,
                    mapBackend = prefs[MAP_BACKEND_KEY].toEnumOr(MapBackend.OSM),
                    mapboxStyle = prefs[MAPBOX_STYLE_KEY].toEnumOr(MapboxStyle.STANDARD),
                    mapboxTraffic = prefs[MAPBOX_TRAFFIC_KEY] ?: false,
                    mapboxAccessToken = prefs[MAPBOX_ACCESS_TOKEN_KEY].orEmpty(),
                    googleMapsApiKey = prefs[GOOGLE_MAPS_API_KEY_KEY].orEmpty(),
                    googleMapsMapId = prefs[GOOGLE_MAPS_MAP_ID_KEY].orEmpty(),
                    googleMapsMapType = prefs[GOOGLE_MAPS_MAP_TYPE_KEY].toEnumOr(GoogleMapType.ROADMAP),
                    googleMapsTraffic = prefs[GOOGLE_MAPS_TRAFFIC_KEY] ?: false,
                )
            }

    override suspend fun setThemeMode(value: ThemeMode) {
        context.displayDataStore.editOrLog(TAG) { it[THEME_KEY] = value.name }
    }

    override suspend fun setAccentColor(value: AccentColor) {
        context.displayDataStore.editOrLog(TAG) { it[ACCENT_KEY] = value.name }
    }

    override suspend fun setSpeedUnit(value: SpeedUnitSetting) {
        context.displayDataStore.editOrLog(TAG) { it[SPEED_KEY] = value.name }
    }

    override suspend fun setTemperatureUnit(value: TemperatureUnitSetting) {
        context.displayDataStore.editOrLog(TAG) { it[TEMPERATURE_KEY] = value.name }
    }

    override suspend fun setClock(value: ClockSetting) {
        context.displayDataStore.editOrLog(TAG) { it[CLOCK_KEY] = value.name }
    }

    override suspend fun setShowClockSeconds(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[SHOW_CLOCK_SECONDS_KEY] = value }
    }

    override suspend fun setFullscreen(value: FullscreenSetting) {
        context.displayDataStore.editOrLog(TAG) { it[FULLSCREEN_KEY] = value.name }
    }

    override suspend fun setDockPosition(value: DockPosition) {
        context.displayDataStore.editOrLog(TAG) { it[DOCK_POSITION_KEY] = value.name }
    }

    override suspend fun setDriverSide(value: DriverSide) {
        context.displayDataStore.editOrLog(TAG) { it[DRIVER_SIDE_KEY] = value.name }
    }

    override suspend fun setMotionTier(value: MotionTier) {
        context.displayDataStore.editOrLog(TAG) { it[MOTION_TIER_KEY] = value.name }
    }

    override suspend fun setOrientation(value: OrientationSetting) {
        context.displayDataStore.editOrLog(TAG) { it[ORIENTATION_KEY] = value.name }
    }

    override suspend fun setUiScale(value: UiScale) {
        context.displayDataStore.editOrLog(TAG) { it[UI_SCALE_KEY] = value.name }
    }

    override suspend fun setKeepScreenOn(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[KEEP_SCREEN_ON_KEY] = value }
    }

    override suspend fun setAssistantLaunch(value: AssistantLaunchSetting) {
        context.displayDataStore.editOrLog(TAG) { it[ASSISTANT_LAUNCH_KEY] = value.name }
    }

    override suspend fun setMapStyle(value: MapStyleSetting) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_STYLE_KEY] = value.name }
    }

    override suspend fun setMapSchemeLight(value: MapColorScheme) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_SCHEME_LIGHT_KEY] = value.name }
    }

    override suspend fun setMapSchemeDark(value: MapColorScheme) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_SCHEME_DARK_KEY] = value.name }
    }

    override suspend fun setMapTilt(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_TILT_KEY] = value }
    }

    override suspend fun setMapZoom(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_ZOOM_KEY] = value }
    }

    override suspend fun adjustMapZoom(delta: Int) {
        context.displayDataStore.editOrLog(TAG) {
            it[MAP_ZOOM_KEY] = ((it[MAP_ZOOM_KEY] ?: DEFAULT_MAP_ZOOM) + delta).coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
        }
    }

    override suspend fun setMapNorthUp(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_NORTH_UP_KEY] = value }
    }

    override suspend fun toggleMapNorthUp() {
        context.displayDataStore.editOrLog(TAG) { it[MAP_NORTH_UP_KEY] = !(it[MAP_NORTH_UP_KEY] ?: false) }
    }

    override suspend fun setMapMarkerPos(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_MARKER_POS_KEY] = value }
    }

    override suspend fun setMap3dBuildings(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_3D_BUILDINGS_KEY] = value }
    }

    override suspend fun setMapTerrain(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_TERRAIN_KEY] = value }
    }

    override suspend fun setGlassBlurRadius(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[GLASS_BLUR_KEY] = value }
    }

    override suspend fun setGlassTintScale(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[GLASS_TINT_KEY] = value }
    }

    override suspend fun setGlassShowBorder(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[GLASS_SHOW_BORDER_KEY] = value }
    }

    override suspend fun setGlassShadowEnabled(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[GLASS_SHADOW_ENABLED_KEY] = value }
    }

    override suspend fun setGlassShadowIntensity(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[GLASS_SHADOW_INTENSITY_KEY] = value }
    }

    override suspend fun setGlassShadowSizeDp(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[GLASS_SHADOW_SIZE_KEY] = value }
    }

    override suspend fun setFontBaseSizeSp(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[FONT_BASE_SIZE_KEY] = value }
    }

    override suspend fun setFontWeightStep(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[FONT_WEIGHT_STEP_KEY] = value }
    }

    override suspend fun setFontLetterSpacingCentiEm(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[FONT_LETTER_SPACING_KEY] = value }
    }

    override suspend fun setShowCalendar(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[SHOW_CALENDAR_KEY] = value }
    }

    override suspend fun setShowWeather(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[SHOW_WEATHER_KEY] = value }
    }

    override suspend fun setShowMusic(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[SHOW_MUSIC_KEY] = value }
    }

    override suspend fun setMusicSpectrum(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[MUSIC_SPECTRUM_KEY] = value }
    }

    override suspend fun setMusicShowAlbum(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[MUSIC_SHOW_ALBUM_KEY] = value }
    }

    override suspend fun setMusicShowArt(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[MUSIC_SHOW_ART_KEY] = value }
    }

    override suspend fun setMapBackend(value: MapBackend) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_BACKEND_KEY] = value.name }
    }

    override suspend fun setMapboxStyle(value: MapboxStyle) {
        context.displayDataStore.editOrLog(TAG) { it[MAPBOX_STYLE_KEY] = value.name }
    }

    override suspend fun setMapboxTraffic(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[MAPBOX_TRAFFIC_KEY] = value }
    }

    override suspend fun setMapboxAccessToken(value: String) {
        context.displayDataStore.editOrLog(TAG) { it[MAPBOX_ACCESS_TOKEN_KEY] = value }
    }

    override suspend fun setGoogleMapsApiKey(value: String) {
        context.displayDataStore.editOrLog(TAG) { it[GOOGLE_MAPS_API_KEY_KEY] = value }
    }

    override suspend fun setGoogleMapsMapId(value: String) {
        context.displayDataStore.editOrLog(TAG) { it[GOOGLE_MAPS_MAP_ID_KEY] = value }
    }

    override suspend fun setGoogleMapsMapType(value: GoogleMapType) {
        context.displayDataStore.editOrLog(TAG) { it[GOOGLE_MAPS_MAP_TYPE_KEY] = value.name }
    }

    override suspend fun setGoogleMapsTraffic(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[GOOGLE_MAPS_TRAFFIC_KEY] = value }
    }

    // Removing only the given keys makes the read path above fall back to its
    // per-field defaults for exactly those fields; every other key is left as-is.
    override suspend fun resetKeys(keys: Set<Preferences.Key<*>>) {
        context.displayDataStore.editOrLog(TAG) { prefs -> keys.forEach { prefs.remove(it) } }
    }

    // Clearing every key makes the read path above fall back to its per-field
    // defaults, which are kept identical to DisplaySettings.Default — so a reset
    // restores the defaults without duplicating the default literals here.
    override suspend fun resetToDefaults() {
        context.displayDataStore.editOrLog(TAG) { it.clear() }
    }

    // Internal (not private): SettingsSectionId groups these same key instances
    // into per-section reset sets, and test fixtures reset individual fields by
    // key — both need to reference the exact singletons declared here, the SSOT
    // for a persisted field's key.
    internal companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val ACCENT_KEY = stringPreferencesKey("accent_color")
        val UI_SCALE_KEY = stringPreferencesKey("ui_scale")
        val SPEED_KEY = stringPreferencesKey("speed_unit")
        val TEMPERATURE_KEY = stringPreferencesKey("temperature_unit")
        val CLOCK_KEY = stringPreferencesKey("clock")
        val SHOW_CLOCK_SECONDS_KEY = booleanPreferencesKey("show_clock_seconds")
        val FULLSCREEN_KEY = stringPreferencesKey("fullscreen")
        val DOCK_POSITION_KEY = stringPreferencesKey("dock_position")
        val DRIVER_SIDE_KEY = stringPreferencesKey("driver_side")
        val MOTION_TIER_KEY = stringPreferencesKey("motion_tier")
        val ORIENTATION_KEY = stringPreferencesKey("orientation")
        val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        val ASSISTANT_LAUNCH_KEY = stringPreferencesKey("assistant_launch")
        val MAP_STYLE_KEY = stringPreferencesKey("map_style")
        val MAP_SCHEME_LIGHT_KEY = stringPreferencesKey("map_scheme_light")
        val MAP_SCHEME_DARK_KEY = stringPreferencesKey("map_scheme_dark")
        val MAP_TILT_KEY = intPreferencesKey("map_tilt_deg")
        val MAP_ZOOM_KEY = intPreferencesKey("map_zoom")
        val MAP_NORTH_UP_KEY = booleanPreferencesKey("map_north_up")
        val MAP_MARKER_POS_KEY = intPreferencesKey("map_marker_pos")
        val MAP_3D_BUILDINGS_KEY = booleanPreferencesKey("map_3d_buildings")
        val MAP_TERRAIN_KEY = booleanPreferencesKey("map_terrain")
        val GLASS_BLUR_KEY = intPreferencesKey("glass_blur_radius")
        val GLASS_TINT_KEY = intPreferencesKey("glass_tint_scale")
        val GLASS_SHOW_BORDER_KEY = booleanPreferencesKey("glass_show_border")
        val GLASS_SHADOW_ENABLED_KEY = booleanPreferencesKey("glass_shadow_enabled")
        val GLASS_SHADOW_INTENSITY_KEY = intPreferencesKey("glass_shadow_intensity")
        val GLASS_SHADOW_SIZE_KEY = intPreferencesKey("glass_shadow_size_dp")
        val FONT_BASE_SIZE_KEY = intPreferencesKey("font_base_size_sp")
        val FONT_WEIGHT_STEP_KEY = intPreferencesKey("font_weight_step")
        val FONT_LETTER_SPACING_KEY = intPreferencesKey("font_letter_spacing_centi_em")
        val SHOW_CALENDAR_KEY = booleanPreferencesKey("show_calendar")
        val SHOW_WEATHER_KEY = booleanPreferencesKey("show_weather")
        val SHOW_MUSIC_KEY = booleanPreferencesKey("show_music")
        val MUSIC_SPECTRUM_KEY = booleanPreferencesKey("music_spectrum")
        val MUSIC_SHOW_ALBUM_KEY = booleanPreferencesKey("music_show_album")
        val MUSIC_SHOW_ART_KEY = booleanPreferencesKey("music_show_art")
        val MAP_BACKEND_KEY = stringPreferencesKey("map_backend")
        val MAPBOX_STYLE_KEY = stringPreferencesKey("mapbox_style")
        val MAPBOX_TRAFFIC_KEY = booleanPreferencesKey("mapbox_traffic")
        val MAPBOX_ACCESS_TOKEN_KEY = stringPreferencesKey("mapbox_access_token")
        val GOOGLE_MAPS_API_KEY_KEY = stringPreferencesKey("google_maps_api_key")
        val GOOGLE_MAPS_MAP_ID_KEY = stringPreferencesKey("google_maps_map_id")
        val GOOGLE_MAPS_MAP_TYPE_KEY = stringPreferencesKey("google_maps_map_type")
        val GOOGLE_MAPS_TRAFFIC_KEY = booleanPreferencesKey("google_maps_traffic")

        /**
         * Every key persisted above, declared once right beside them.
         *
         * Two drift guards depend on this set, since both compare against it
         * rather than restating it: `SettingsSectionIdTest` compares it against
         * the union of every [SettingsSectionId]'s [SettingsSectionId.displayKeys]
         * (a key added here without a section assignment fails that test), and
         * `DisplayPreferencesTest` compares it against the real DataStore's
         * persisted keys after writing through every setter (a setter added
         * without a matching entry here — or vice versa — fails that test).
         */
        val ALL_KEYS: Set<Preferences.Key<*>> =
            setOf(
                THEME_KEY,
                ACCENT_KEY,
                UI_SCALE_KEY,
                SPEED_KEY,
                TEMPERATURE_KEY,
                CLOCK_KEY,
                SHOW_CLOCK_SECONDS_KEY,
                FULLSCREEN_KEY,
                DOCK_POSITION_KEY,
                DRIVER_SIDE_KEY,
                MOTION_TIER_KEY,
                ORIENTATION_KEY,
                KEEP_SCREEN_ON_KEY,
                ASSISTANT_LAUNCH_KEY,
                MAP_STYLE_KEY,
                MAP_SCHEME_LIGHT_KEY,
                MAP_SCHEME_DARK_KEY,
                MAP_TILT_KEY,
                MAP_ZOOM_KEY,
                MAP_NORTH_UP_KEY,
                MAP_MARKER_POS_KEY,
                MAP_3D_BUILDINGS_KEY,
                MAP_TERRAIN_KEY,
                GLASS_BLUR_KEY,
                GLASS_TINT_KEY,
                GLASS_SHOW_BORDER_KEY,
                GLASS_SHADOW_ENABLED_KEY,
                GLASS_SHADOW_INTENSITY_KEY,
                GLASS_SHADOW_SIZE_KEY,
                FONT_BASE_SIZE_KEY,
                FONT_WEIGHT_STEP_KEY,
                FONT_LETTER_SPACING_KEY,
                SHOW_CALENDAR_KEY,
                SHOW_WEATHER_KEY,
                SHOW_MUSIC_KEY,
                MUSIC_SPECTRUM_KEY,
                MUSIC_SHOW_ALBUM_KEY,
                MUSIC_SHOW_ART_KEY,
                MAP_BACKEND_KEY,
                MAPBOX_STYLE_KEY,
                MAPBOX_TRAFFIC_KEY,
                MAPBOX_ACCESS_TOKEN_KEY,
                GOOGLE_MAPS_API_KEY_KEY,
                GOOGLE_MAPS_MAP_ID_KEY,
                GOOGLE_MAPS_MAP_TYPE_KEY,
                GOOGLE_MAPS_TRAFFIC_KEY,
            )
    }
}
