package io.github.seijikohara.femto.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.enums.enumEntries

/** Light / dark / follow-system theme choice. */
internal enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Speed/distance unit: follow the locale, or force metric / imperial. */
internal enum class SpeedUnitSetting { AUTO, KILOMETERS, MILES }

/** Temperature unit: follow the locale, or force Celsius / Fahrenheit. */
internal enum class TemperatureUnitSetting { AUTO, CELSIUS, FAHRENHEIT }

/** Clock: follow the system 12/24h setting, or force 12h / 24h. */
internal enum class ClockSetting { AUTO, TWELVE_HOUR, TWENTY_FOUR_HOUR }

/** Fullscreen: keep the system bars, or hide both status and navigation bars. */
internal enum class FullscreenSetting { OFF, ON }

/** Default target map frame rate (fps) before the user picks one. */
internal const val DEFAULT_MAP_FPS = 10

/** Map light/dark style: follow the system theme, or force light / dark. */
internal enum class MapStyleSetting { AUTO, LIGHT, DARK }

/**
 * Map render backend. SNAPSHOT draws off-screen bitmaps (presents reliably on the
 * projected / virtualised displays of AI boxes); LIVE uses a GL MapView (smoother
 * where the device can scan out GL buffers, but blank on displays that cannot).
 */
internal enum class MapRenderMode { SNAPSHOT, LIVE }

/** Default oblique-camera tilt (degrees) and zoom level for the map. */
internal const val DEFAULT_MAP_TILT_DEG = 55
internal const val DEFAULT_MAP_ZOOM = 16

/**
 * Default snapshot render resolution, as a percentage of the panel's pixel size.
 * 100 renders at full resolution; lower values render a smaller bitmap (upscaled
 * to fill), trading sharpness for a faster render and a smoother frame rate.
 */
internal const val DEFAULT_MAP_RENDER_PERCENT = 100

/**
 * Default map look-ahead (metres): the camera aims this far ahead of the current
 * position along the heading, so the location marker sits low in the frame (near
 * the speed overlay) with the road ahead visible. Larger = marker lower.
 */
internal const val DEFAULT_MAP_LOOKAHEAD_M = 180

/**
 * User display settings that override the locale / system defaults. Every value
 * defaults to the auto / system choice so a fresh install behaves exactly as
 * before the settings screen existed.
 */
internal data class DisplaySettings(
    val themeMode: ThemeMode,
    val speedUnit: SpeedUnitSetting,
    val temperatureUnit: TemperatureUnitSetting,
    val clock: ClockSetting,
    // Whether the clock overlay shows seconds. Defaults to true (the original
    // HH:mm:ss readout); when false the overlay drops to HH:mm and self-times
    // per-minute instead of per-second.
    val showClockSeconds: Boolean,
    val fullscreen: FullscreenSetting,
    // Target map frame rate (fps): the snapshot map caps its re-render rate at
    // this many frames per second. Clamped to the display's max refresh at use.
    val mapFps: Int,
    val mapStyle: MapStyleSetting,
    val mapTiltDeg: Int,
    val mapZoom: Int,
    // Snapshot render resolution as a percent of the panel pixel size; lower is
    // blurrier but renders faster, so the frame rate can climb closer to mapFps.
    val mapRenderPercent: Int,
    val mapRenderMode: MapRenderMode,
    // Camera look-ahead (metres): how far ahead of the current position the camera
    // aims, which sets how low the location marker sits (closer to the speed panel).
    val mapLookAheadM: Int,
    // Info-pane card visibility. Each card defaults to shown so a fresh install
    // renders the full dashboard; hiding one lets the remaining cards (or the map)
    // reflow into the freed space.
    val showCalendar: Boolean,
    val showWeather: Boolean,
    val showMusic: Boolean,
) {
    companion object {
        val Default =
            DisplaySettings(
                themeMode = ThemeMode.SYSTEM,
                speedUnit = SpeedUnitSetting.AUTO,
                temperatureUnit = TemperatureUnitSetting.AUTO,
                clock = ClockSetting.AUTO,
                showClockSeconds = true,
                fullscreen = FullscreenSetting.OFF,
                mapFps = DEFAULT_MAP_FPS,
                mapStyle = MapStyleSetting.AUTO,
                mapTiltDeg = DEFAULT_MAP_TILT_DEG,
                mapZoom = DEFAULT_MAP_ZOOM,
                mapRenderPercent = DEFAULT_MAP_RENDER_PERCENT,
                mapRenderMode = MapRenderMode.SNAPSHOT,
                mapLookAheadM = DEFAULT_MAP_LOOKAHEAD_M,
                showCalendar = true,
                showWeather = true,
                showMusic = true,
            )
    }
}

private val Context.displayDataStore: DataStore<Preferences> by preferencesDataStore(name = "display_preferences")

/**
 * Read/write surface for [DisplaySettings]. [DisplayPreferences] is the
 * DataStore-backed production implementation; tests substitute an in-memory fake
 * so the view-model can be exercised without real DataStore IO.
 */
internal interface DisplaySettingsStore {
    val settings: Flow<DisplaySettings>

    suspend fun setThemeMode(value: ThemeMode)

    suspend fun setSpeedUnit(value: SpeedUnitSetting)

    suspend fun setTemperatureUnit(value: TemperatureUnitSetting)

    suspend fun setClock(value: ClockSetting)

    suspend fun setShowClockSeconds(value: Boolean)

    suspend fun setFullscreen(value: FullscreenSetting)

    suspend fun setMapFps(value: Int)

    suspend fun setMapStyle(value: MapStyleSetting)

    suspend fun setMapTilt(value: Int)

    suspend fun setMapZoom(value: Int)

    suspend fun setMapRenderPercent(value: Int)

    suspend fun setMapRenderMode(value: MapRenderMode)

    suspend fun setMapLookAhead(value: Int)

    suspend fun setShowCalendar(value: Boolean)

    suspend fun setShowWeather(value: Boolean)

    suspend fun setShowMusic(value: Boolean)
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
        context.displayDataStore.data.map { prefs ->
            DisplaySettings(
                themeMode = prefs[THEME_KEY].toEnumOr(ThemeMode.SYSTEM),
                speedUnit = prefs[SPEED_KEY].toEnumOr(SpeedUnitSetting.AUTO),
                temperatureUnit = prefs[TEMPERATURE_KEY].toEnumOr(TemperatureUnitSetting.AUTO),
                clock = prefs[CLOCK_KEY].toEnumOr(ClockSetting.AUTO),
                showClockSeconds = prefs[SHOW_CLOCK_SECONDS_KEY] ?: true,
                fullscreen = prefs[FULLSCREEN_KEY].toEnumOr(FullscreenSetting.OFF),
                mapFps = prefs[MAP_FPS_KEY] ?: DEFAULT_MAP_FPS,
                mapStyle = prefs[MAP_STYLE_KEY].toEnumOr(MapStyleSetting.AUTO),
                mapTiltDeg = prefs[MAP_TILT_KEY] ?: DEFAULT_MAP_TILT_DEG,
                mapZoom = prefs[MAP_ZOOM_KEY] ?: DEFAULT_MAP_ZOOM,
                mapRenderPercent = prefs[MAP_QUALITY_KEY] ?: DEFAULT_MAP_RENDER_PERCENT,
                mapRenderMode = prefs[MAP_RENDER_MODE_KEY].toEnumOr(MapRenderMode.SNAPSHOT),
                mapLookAheadM = prefs[MAP_LOOKAHEAD_KEY] ?: DEFAULT_MAP_LOOKAHEAD_M,
                showCalendar = prefs[SHOW_CALENDAR_KEY] ?: true,
                showWeather = prefs[SHOW_WEATHER_KEY] ?: true,
                showMusic = prefs[SHOW_MUSIC_KEY] ?: true,
            )
        }

    // Block bodies (returning Unit): edit() yields Preferences, which the
    // DisplaySettingsStore contract intentionally discards.
    override suspend fun setThemeMode(value: ThemeMode) {
        context.displayDataStore.edit { it[THEME_KEY] = value.name }
    }

    override suspend fun setSpeedUnit(value: SpeedUnitSetting) {
        context.displayDataStore.edit { it[SPEED_KEY] = value.name }
    }

    override suspend fun setTemperatureUnit(value: TemperatureUnitSetting) {
        context.displayDataStore.edit { it[TEMPERATURE_KEY] = value.name }
    }

    override suspend fun setClock(value: ClockSetting) {
        context.displayDataStore.edit { it[CLOCK_KEY] = value.name }
    }

    override suspend fun setShowClockSeconds(value: Boolean) {
        context.displayDataStore.edit { it[SHOW_CLOCK_SECONDS_KEY] = value }
    }

    override suspend fun setFullscreen(value: FullscreenSetting) {
        context.displayDataStore.edit { it[FULLSCREEN_KEY] = value.name }
    }

    override suspend fun setMapFps(value: Int) {
        context.displayDataStore.edit { it[MAP_FPS_KEY] = value }
    }

    override suspend fun setMapStyle(value: MapStyleSetting) {
        context.displayDataStore.edit { it[MAP_STYLE_KEY] = value.name }
    }

    override suspend fun setMapTilt(value: Int) {
        context.displayDataStore.edit { it[MAP_TILT_KEY] = value }
    }

    override suspend fun setMapZoom(value: Int) {
        context.displayDataStore.edit { it[MAP_ZOOM_KEY] = value }
    }

    override suspend fun setMapRenderPercent(value: Int) {
        context.displayDataStore.edit { it[MAP_QUALITY_KEY] = value }
    }

    override suspend fun setMapRenderMode(value: MapRenderMode) {
        context.displayDataStore.edit { it[MAP_RENDER_MODE_KEY] = value.name }
    }

    override suspend fun setMapLookAhead(value: Int) {
        context.displayDataStore.edit { it[MAP_LOOKAHEAD_KEY] = value }
    }

    override suspend fun setShowCalendar(value: Boolean) {
        context.displayDataStore.edit { it[SHOW_CALENDAR_KEY] = value }
    }

    override suspend fun setShowWeather(value: Boolean) {
        context.displayDataStore.edit { it[SHOW_WEATHER_KEY] = value }
    }

    override suspend fun setShowMusic(value: Boolean) {
        context.displayDataStore.edit { it[SHOW_MUSIC_KEY] = value }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val SPEED_KEY = stringPreferencesKey("speed_unit")
        val TEMPERATURE_KEY = stringPreferencesKey("temperature_unit")
        val CLOCK_KEY = stringPreferencesKey("clock")
        val SHOW_CLOCK_SECONDS_KEY = booleanPreferencesKey("show_clock_seconds")
        val FULLSCREEN_KEY = stringPreferencesKey("fullscreen")
        val MAP_FPS_KEY = intPreferencesKey("map_fps")
        val MAP_STYLE_KEY = stringPreferencesKey("map_style")
        val MAP_TILT_KEY = intPreferencesKey("map_tilt_deg")
        val MAP_ZOOM_KEY = intPreferencesKey("map_zoom")
        val MAP_QUALITY_KEY = intPreferencesKey("map_render_percent")
        val MAP_RENDER_MODE_KEY = stringPreferencesKey("map_render_mode")
        val MAP_LOOKAHEAD_KEY = intPreferencesKey("map_look_ahead_m")
        val SHOW_CALENDAR_KEY = booleanPreferencesKey("show_calendar")
        val SHOW_WEATHER_KEY = booleanPreferencesKey("show_weather")
        val SHOW_MUSIC_KEY = booleanPreferencesKey("show_music")
    }
}

// Decode a stored enum name to [T], falling back to [fallback] for a missing or
// unrecognised value so the read never throws on a downgrade / renamed entry.
private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
    this?.let { name -> enumEntries<T>().firstOrNull { it.name == name } } ?: fallback
