package io.github.seijikohara.femto.data.display

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import io.github.seijikohara.femto.data.common.toEnumOr
import io.github.seijikohara.femto.data.fonts.FontPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.enums.enumEntries

private const val TAG = "DisplayPreferences"

private val Context.displayDataStore: DataStore<Preferences> by preferencesDataStore(name = "display_preferences")

/**
 * Read/write surface for [DisplaySettings]. [DisplayPreferences] is the
 * DataStore-backed production implementation; tests substitute an in-memory fake
 * so the view-model can be exercised without real DataStore IO.
 */
internal interface DisplaySettingsStore {
    val settings: Flow<DisplaySettings>

    suspend fun setThemeMode(value: ThemeMode)

    suspend fun setAccentColor(value: AccentColor)

    suspend fun setSpeedUnit(value: SpeedUnitSetting)

    suspend fun setTemperatureUnit(value: TemperatureUnitSetting)

    suspend fun setClock(value: ClockSetting)

    suspend fun setShowClockSeconds(value: Boolean)

    suspend fun setFullscreen(value: FullscreenSetting)

    suspend fun setDockPosition(value: DockPosition)

    suspend fun setOrientation(value: OrientationSetting)

    suspend fun setKeepScreenOn(value: Boolean)

    suspend fun setAssistantLaunch(value: AssistantLaunchSetting)

    suspend fun setMapStyle(value: MapStyleSetting)

    suspend fun setMapSchemeLight(value: MapColorScheme)

    suspend fun setMapSchemeDark(value: MapColorScheme)

    suspend fun setMapTilt(value: Int)

    suspend fun setMapZoom(value: Int)

    suspend fun setMapNorthUp(value: Boolean)

    suspend fun setMapRenderPercent(value: Int)

    suspend fun setMapRenderMode(value: MapRenderMode)

    suspend fun setMapMarkerPos(value: Int)

    suspend fun setMap3dBuildings(value: Boolean)

    suspend fun setMapTerrain(value: Boolean)

    suspend fun setGlassBlurRadius(value: Int)

    suspend fun setGlassTintScale(value: Int)

    suspend fun setShowCalendar(value: Boolean)

    suspend fun setShowWeather(value: Boolean)

    suspend fun setShowMusic(value: Boolean)

    suspend fun setMusicSpectrum(value: Boolean)

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
                    speedUnit = prefs[SPEED_KEY].toEnumOr(SpeedUnitSetting.AUTO),
                    temperatureUnit = prefs[TEMPERATURE_KEY].toEnumOr(TemperatureUnitSetting.AUTO),
                    clock = prefs[CLOCK_KEY].toEnumOr(ClockSetting.AUTO),
                    showClockSeconds = prefs[SHOW_CLOCK_SECONDS_KEY] ?: false,
                    fullscreen = prefs[FULLSCREEN_KEY].toEnumOr(FullscreenSetting.ON),
                    dockPosition = prefs[DOCK_POSITION_KEY].toEnumOr(DockPosition.BOTTOM),
                    orientation = prefs[ORIENTATION_KEY].toEnumOr(OrientationSetting.AUTO),
                    keepScreenOn = prefs[KEEP_SCREEN_ON_KEY] ?: true,
                    assistantLaunch = prefs[ASSISTANT_LAUNCH_KEY].toEnumOr(AssistantLaunchSetting.SYSTEM),
                    mapStyle = prefs[MAP_STYLE_KEY].toEnumOr(MapStyleSetting.AUTO),
                    mapSchemeLight = prefs[MAP_SCHEME_LIGHT_KEY].toEnumOr(MapColorScheme.ACCENT),
                    mapSchemeDark = prefs[MAP_SCHEME_DARK_KEY].toEnumOr(MapColorScheme.ACCENT),
                    mapTiltDeg = prefs[MAP_TILT_KEY] ?: DEFAULT_MAP_TILT_DEG,
                    mapZoom = prefs[MAP_ZOOM_KEY] ?: DEFAULT_MAP_ZOOM,
                    mapNorthUp = prefs[MAP_NORTH_UP_KEY] ?: false,
                    mapRenderPercent = prefs[MAP_QUALITY_KEY] ?: DEFAULT_MAP_RENDER_PERCENT,
                    mapRenderMode = prefs[MAP_RENDER_MODE_KEY].toMapRenderModeOr(MapRenderMode.LIVE),
                    mapMarkerPos = prefs[MAP_MARKER_POS_KEY] ?: DEFAULT_MAP_MARKER_POS,
                    map3dBuildings = prefs[MAP_3D_BUILDINGS_KEY] ?: true,
                    mapTerrain = prefs[MAP_TERRAIN_KEY] ?: true,
                    glassBlurRadius = prefs[GLASS_BLUR_KEY] ?: DEFAULT_GLASS_BLUR_DP,
                    glassTintScale = prefs[GLASS_TINT_KEY] ?: DEFAULT_GLASS_TINT_SCALE,
                    showCalendar = prefs[SHOW_CALENDAR_KEY] ?: true,
                    showWeather = prefs[SHOW_WEATHER_KEY] ?: true,
                    showMusic = prefs[SHOW_MUSIC_KEY] ?: true,
                    musicSpectrum = prefs[MUSIC_SPECTRUM_KEY] ?: false,
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

    override suspend fun setOrientation(value: OrientationSetting) {
        context.displayDataStore.editOrLog(TAG) { it[ORIENTATION_KEY] = value.name }
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

    override suspend fun setMapNorthUp(value: Boolean) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_NORTH_UP_KEY] = value }
    }

    override suspend fun setMapRenderPercent(value: Int) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_QUALITY_KEY] = value }
    }

    override suspend fun setMapRenderMode(value: MapRenderMode) {
        context.displayDataStore.editOrLog(TAG) { it[MAP_RENDER_MODE_KEY] = value.name }
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

    // Clearing every key makes the read path above fall back to its per-field
    // defaults, which are kept identical to DisplaySettings.Default — so a reset
    // restores the defaults without duplicating the default literals here.
    override suspend fun resetToDefaults() {
        context.displayDataStore.editOrLog(TAG) { it.clear() }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val ACCENT_KEY = stringPreferencesKey("accent_color")
        val SPEED_KEY = stringPreferencesKey("speed_unit")
        val TEMPERATURE_KEY = stringPreferencesKey("temperature_unit")
        val CLOCK_KEY = stringPreferencesKey("clock")
        val SHOW_CLOCK_SECONDS_KEY = booleanPreferencesKey("show_clock_seconds")
        val FULLSCREEN_KEY = stringPreferencesKey("fullscreen")
        val DOCK_POSITION_KEY = stringPreferencesKey("dock_position")
        val ORIENTATION_KEY = stringPreferencesKey("orientation")
        val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        val ASSISTANT_LAUNCH_KEY = stringPreferencesKey("assistant_launch")
        val MAP_STYLE_KEY = stringPreferencesKey("map_style")
        val MAP_SCHEME_LIGHT_KEY = stringPreferencesKey("map_scheme_light")
        val MAP_SCHEME_DARK_KEY = stringPreferencesKey("map_scheme_dark")
        val MAP_TILT_KEY = intPreferencesKey("map_tilt_deg")
        val MAP_ZOOM_KEY = intPreferencesKey("map_zoom")
        val MAP_NORTH_UP_KEY = booleanPreferencesKey("map_north_up")
        val MAP_QUALITY_KEY = intPreferencesKey("map_render_percent")
        val MAP_RENDER_MODE_KEY = stringPreferencesKey("map_render_mode")
        val MAP_MARKER_POS_KEY = intPreferencesKey("map_marker_pos")
        val MAP_3D_BUILDINGS_KEY = booleanPreferencesKey("map_3d_buildings")
        val MAP_TERRAIN_KEY = booleanPreferencesKey("map_terrain")
        val GLASS_BLUR_KEY = intPreferencesKey("glass_blur_radius")
        val GLASS_TINT_KEY = intPreferencesKey("glass_tint_scale")
        val SHOW_CALENDAR_KEY = booleanPreferencesKey("show_calendar")
        val SHOW_WEATHER_KEY = booleanPreferencesKey("show_weather")
        val SHOW_MUSIC_KEY = booleanPreferencesKey("show_music")
        val MUSIC_SPECTRUM_KEY = booleanPreferencesKey("music_spectrum")
    }
}

// Decode the render mode, migrating the short-lived three-mode values
// (LIVE_HARDWARE / LIVE_SOFTWARE) back to LIVE, so anyone who picked a live map
// keeps it after the software backend was removed.
private fun String?.toMapRenderModeOr(fallback: MapRenderMode): MapRenderMode =
    when (this) {
        "LIVE_HARDWARE", "LIVE_SOFTWARE" -> MapRenderMode.LIVE
        else -> toEnumOr(fallback)
    }
