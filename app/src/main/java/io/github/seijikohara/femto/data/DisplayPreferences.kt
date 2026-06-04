package io.github.seijikohara.femto.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    val fullscreen: FullscreenSetting,
) {
    companion object {
        val Default =
            DisplaySettings(
                themeMode = ThemeMode.SYSTEM,
                speedUnit = SpeedUnitSetting.AUTO,
                temperatureUnit = TemperatureUnitSetting.AUTO,
                clock = ClockSetting.AUTO,
                fullscreen = FullscreenSetting.OFF,
            )
    }
}

private val Context.displayDataStore: DataStore<Preferences> by preferencesDataStore(name = "display_preferences")

/**
 * DataStore-backed accessor for [DisplaySettings].
 *
 * Each enum is stored by name and decoded defensively (an unknown / renamed
 * value falls back to the auto default) so a downgrade or a renamed entry never
 * crashes the read path. Modelled on [FontPreferences].
 */
internal class DisplayPreferences(
    private val context: Context,
) {
    val settings: Flow<DisplaySettings> =
        context.displayDataStore.data.map { prefs ->
            DisplaySettings(
                themeMode = prefs[THEME_KEY].toEnumOr(ThemeMode.SYSTEM),
                speedUnit = prefs[SPEED_KEY].toEnumOr(SpeedUnitSetting.AUTO),
                temperatureUnit = prefs[TEMPERATURE_KEY].toEnumOr(TemperatureUnitSetting.AUTO),
                clock = prefs[CLOCK_KEY].toEnumOr(ClockSetting.AUTO),
                fullscreen = prefs[FULLSCREEN_KEY].toEnumOr(FullscreenSetting.OFF),
            )
        }

    suspend fun setThemeMode(value: ThemeMode) = context.displayDataStore.edit { it[THEME_KEY] = value.name }

    suspend fun setSpeedUnit(value: SpeedUnitSetting) = context.displayDataStore.edit { it[SPEED_KEY] = value.name }

    suspend fun setTemperatureUnit(value: TemperatureUnitSetting) =
        context.displayDataStore.edit { it[TEMPERATURE_KEY] = value.name }

    suspend fun setClock(value: ClockSetting) = context.displayDataStore.edit { it[CLOCK_KEY] = value.name }

    suspend fun setFullscreen(value: FullscreenSetting) =
        context.displayDataStore.edit { it[FULLSCREEN_KEY] = value.name }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val SPEED_KEY = stringPreferencesKey("speed_unit")
        val TEMPERATURE_KEY = stringPreferencesKey("temperature_unit")
        val CLOCK_KEY = stringPreferencesKey("clock")
        val FULLSCREEN_KEY = stringPreferencesKey("fullscreen")
    }
}

// Decode a stored enum name to [T], falling back to [fallback] for a missing or
// unrecognised value so the read never throws on a downgrade / renamed entry.
private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
    this?.let { name -> enumEntries<T>().firstOrNull { it.name == name } } ?: fallback
