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

/**
 * App accent: [DYNAMIC] keeps Material You wallpaper-derived color (the default);
 * every other entry forces a fixed preset seed from which the whole Material 3
 * scheme is generated. The seed color for each preset lives in the theme layer
 * (`accentSeedColor`), keeping this data enum free of any Compose dependency.
 *
 * Public (unlike the other display enums) because the public [FemtoTheme] takes
 * it as a parameter, mirroring [FontTheme]'s visibility.
 */
enum class AccentColor { DYNAMIC, BLUE, TEAL, GREEN, AMBER, ORANGE, RED, VIOLET, PINK }

/** Fullscreen: keep the system bars, or hide both status and navigation bars. */
internal enum class FullscreenSetting { OFF, ON }

/** Map light/dark style: follow the system theme, or force light / dark. */
internal enum class MapStyleSetting { AUTO, LIGHT, DARK }

/**
 * A map colour scheme. [ACCENT] is the adaptive default: the base style for the
 * active light/dark context (bundled positron / dark-matter) recoloured with the
 * app's Material accent. The rest are fixed OpenFreeMap styles — [POSITRON],
 * [BRIGHT], [LIBERTY] read as light; [DARK_MATTER] (bundled), [DARK], [FIORD] read
 * as dark. The light and dark schemes are chosen independently (see
 * [DisplaySettings.mapSchemeLight] / [DisplaySettings.mapSchemeDark]).
 */
internal enum class MapColorScheme { ACCENT, POSITRON, BRIGHT, LIBERTY, DARK_MATTER, DARK, FIORD }

/**
 * Map render backend, picked explicitly by the user (no auto-fallback).
 *
 * - [LIVE]: MapLibre GL JS (WebGL) in a hardware-accelerated WebView — smooth,
 *   animated, renders on both the emulator and the head unit.
 * - [SNAPSHOT]: off-screen `MapSnapshotter` bitmaps; presents reliably on any
 *   display (the robust floor for hardware that cannot keep a WebGL context).
 *   The default.
 *
 * A software-WebGL (SwiftShader) backend was removed: there is no per-app API to
 * route WebView WebGL through SwiftShader (`setLayerType(LAYER_TYPE_SOFTWARE)`
 * yields a blank map, not software WebGL), and hardware WebGL works on the target
 * device, so SNAPSHOT already covers the no-WebGL case.
 */
internal enum class MapRenderMode { LIVE, SNAPSHOT }

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
 * Default location-marker vertical position (0..100): 0 centres the marker in the
 * map, 100 drops it just above the speed overlay. The camera is shifted so the
 * marker lands at the chosen height.
 */
internal const val DEFAULT_MAP_MARKER_POS = 70

/**
 * User display settings that override the locale / system defaults. Every value
 * defaults to the auto / system choice so a fresh install behaves exactly as
 * before the settings screen existed.
 */
internal data class DisplaySettings(
    val themeMode: ThemeMode,
    val accentColor: AccentColor,
    val speedUnit: SpeedUnitSetting,
    val temperatureUnit: TemperatureUnitSetting,
    val clock: ClockSetting,
    // Whether the clock overlay shows seconds. Defaults to true (the original
    // HH:mm:ss readout); when false the overlay drops to HH:mm and self-times
    // per-minute instead of per-second.
    val showClockSeconds: Boolean,
    val fullscreen: FullscreenSetting,
    val mapStyle: MapStyleSetting,
    // Independent colour schemes for the light and dark map contexts (which one
    // applies follows [mapStyle] / the system theme). Both default to ACCENT.
    val mapSchemeLight: MapColorScheme,
    val mapSchemeDark: MapColorScheme,
    val mapTiltDeg: Int,
    val mapZoom: Int,
    // Snapshot render resolution as a percent of the panel pixel size; lower is
    // blurrier but renders faster (a smaller bitmap to upscale).
    val mapRenderPercent: Int,
    val mapRenderMode: MapRenderMode,
    // Location-marker vertical position (0..100): 0 = map centre, 100 = just above
    // the speed overlay. Applied to both backends.
    val mapMarkerPos: Int,
    // Live-map (WebGL) feature toggles. Both default off; they apply to the LIVE
    // backends only (SNAPSHOT's native GL cannot extrude/relief safely). 3D buildings
    // extrude the OpenMapTiles building layer; terrain adds raster-DEM relief.
    val map3dBuildings: Boolean,
    val mapTerrain: Boolean,
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
                accentColor = AccentColor.DYNAMIC,
                speedUnit = SpeedUnitSetting.AUTO,
                temperatureUnit = TemperatureUnitSetting.AUTO,
                clock = ClockSetting.AUTO,
                showClockSeconds = true,
                fullscreen = FullscreenSetting.OFF,
                mapStyle = MapStyleSetting.AUTO,
                mapSchemeLight = MapColorScheme.ACCENT,
                mapSchemeDark = MapColorScheme.ACCENT,
                mapTiltDeg = DEFAULT_MAP_TILT_DEG,
                mapZoom = DEFAULT_MAP_ZOOM,
                mapRenderPercent = DEFAULT_MAP_RENDER_PERCENT,
                mapRenderMode = MapRenderMode.SNAPSHOT,
                mapMarkerPos = DEFAULT_MAP_MARKER_POS,
                map3dBuildings = false,
                mapTerrain = false,
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

    suspend fun setAccentColor(value: AccentColor)

    suspend fun setSpeedUnit(value: SpeedUnitSetting)

    suspend fun setTemperatureUnit(value: TemperatureUnitSetting)

    suspend fun setClock(value: ClockSetting)

    suspend fun setShowClockSeconds(value: Boolean)

    suspend fun setFullscreen(value: FullscreenSetting)

    suspend fun setMapStyle(value: MapStyleSetting)

    suspend fun setMapSchemeLight(value: MapColorScheme)

    suspend fun setMapSchemeDark(value: MapColorScheme)

    suspend fun setMapTilt(value: Int)

    suspend fun setMapZoom(value: Int)

    suspend fun setMapRenderPercent(value: Int)

    suspend fun setMapRenderMode(value: MapRenderMode)

    suspend fun setMapMarkerPos(value: Int)

    suspend fun setMap3dBuildings(value: Boolean)

    suspend fun setMapTerrain(value: Boolean)

    suspend fun setShowCalendar(value: Boolean)

    suspend fun setShowWeather(value: Boolean)

    suspend fun setShowMusic(value: Boolean)

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
        context.displayDataStore.data.map { prefs ->
            DisplaySettings(
                themeMode = prefs[THEME_KEY].toEnumOr(ThemeMode.SYSTEM),
                accentColor = prefs[ACCENT_KEY].toEnumOr(AccentColor.DYNAMIC),
                speedUnit = prefs[SPEED_KEY].toEnumOr(SpeedUnitSetting.AUTO),
                temperatureUnit = prefs[TEMPERATURE_KEY].toEnumOr(TemperatureUnitSetting.AUTO),
                clock = prefs[CLOCK_KEY].toEnumOr(ClockSetting.AUTO),
                showClockSeconds = prefs[SHOW_CLOCK_SECONDS_KEY] ?: true,
                fullscreen = prefs[FULLSCREEN_KEY].toEnumOr(FullscreenSetting.OFF),
                mapStyle = prefs[MAP_STYLE_KEY].toEnumOr(MapStyleSetting.AUTO),
                mapSchemeLight = prefs[MAP_SCHEME_LIGHT_KEY].toEnumOr(MapColorScheme.ACCENT),
                mapSchemeDark = prefs[MAP_SCHEME_DARK_KEY].toEnumOr(MapColorScheme.ACCENT),
                mapTiltDeg = prefs[MAP_TILT_KEY] ?: DEFAULT_MAP_TILT_DEG,
                mapZoom = prefs[MAP_ZOOM_KEY] ?: DEFAULT_MAP_ZOOM,
                mapRenderPercent = prefs[MAP_QUALITY_KEY] ?: DEFAULT_MAP_RENDER_PERCENT,
                mapRenderMode = prefs[MAP_RENDER_MODE_KEY].toMapRenderModeOr(MapRenderMode.SNAPSHOT),
                mapMarkerPos = prefs[MAP_MARKER_POS_KEY] ?: DEFAULT_MAP_MARKER_POS,
                map3dBuildings = prefs[MAP_3D_BUILDINGS_KEY] ?: false,
                mapTerrain = prefs[MAP_TERRAIN_KEY] ?: false,
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

    override suspend fun setAccentColor(value: AccentColor) {
        context.displayDataStore.edit { it[ACCENT_KEY] = value.name }
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

    override suspend fun setMapStyle(value: MapStyleSetting) {
        context.displayDataStore.edit { it[MAP_STYLE_KEY] = value.name }
    }

    override suspend fun setMapSchemeLight(value: MapColorScheme) {
        context.displayDataStore.edit { it[MAP_SCHEME_LIGHT_KEY] = value.name }
    }

    override suspend fun setMapSchemeDark(value: MapColorScheme) {
        context.displayDataStore.edit { it[MAP_SCHEME_DARK_KEY] = value.name }
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

    override suspend fun setMapMarkerPos(value: Int) {
        context.displayDataStore.edit { it[MAP_MARKER_POS_KEY] = value }
    }

    override suspend fun setMap3dBuildings(value: Boolean) {
        context.displayDataStore.edit { it[MAP_3D_BUILDINGS_KEY] = value }
    }

    override suspend fun setMapTerrain(value: Boolean) {
        context.displayDataStore.edit { it[MAP_TERRAIN_KEY] = value }
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

    // Clearing every key makes the read path above fall back to its per-field
    // defaults, which are kept identical to DisplaySettings.Default — so a reset
    // restores the defaults without duplicating the default literals here.
    override suspend fun resetToDefaults() {
        context.displayDataStore.edit { it.clear() }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val ACCENT_KEY = stringPreferencesKey("accent_color")
        val SPEED_KEY = stringPreferencesKey("speed_unit")
        val TEMPERATURE_KEY = stringPreferencesKey("temperature_unit")
        val CLOCK_KEY = stringPreferencesKey("clock")
        val SHOW_CLOCK_SECONDS_KEY = booleanPreferencesKey("show_clock_seconds")
        val FULLSCREEN_KEY = stringPreferencesKey("fullscreen")
        val MAP_STYLE_KEY = stringPreferencesKey("map_style")
        val MAP_SCHEME_LIGHT_KEY = stringPreferencesKey("map_scheme_light")
        val MAP_SCHEME_DARK_KEY = stringPreferencesKey("map_scheme_dark")
        val MAP_TILT_KEY = intPreferencesKey("map_tilt_deg")
        val MAP_ZOOM_KEY = intPreferencesKey("map_zoom")
        val MAP_QUALITY_KEY = intPreferencesKey("map_render_percent")
        val MAP_RENDER_MODE_KEY = stringPreferencesKey("map_render_mode")
        val MAP_MARKER_POS_KEY = intPreferencesKey("map_marker_pos")
        val MAP_3D_BUILDINGS_KEY = booleanPreferencesKey("map_3d_buildings")
        val MAP_TERRAIN_KEY = booleanPreferencesKey("map_terrain")
        val SHOW_CALENDAR_KEY = booleanPreferencesKey("show_calendar")
        val SHOW_WEATHER_KEY = booleanPreferencesKey("show_weather")
        val SHOW_MUSIC_KEY = booleanPreferencesKey("show_music")
    }
}

// Decode a stored enum name to [T], falling back to [fallback] for a missing or
// unrecognised value so the read never throws on a downgrade / renamed entry.
private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
    this?.let { name -> enumEntries<T>().firstOrNull { it.name == name } } ?: fallback

// Decode the render mode, migrating the short-lived three-mode values
// (LIVE_HARDWARE / LIVE_SOFTWARE) back to LIVE, so anyone who picked a live map
// keeps it after the software backend was removed.
private fun String?.toMapRenderModeOr(fallback: MapRenderMode): MapRenderMode =
    when (this) {
        "LIVE_HARDWARE", "LIVE_SOFTWARE" -> MapRenderMode.LIVE
        else -> toEnumOr(fallback)
    }
