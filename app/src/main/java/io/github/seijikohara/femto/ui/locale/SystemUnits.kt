package io.github.seijikohara.femto.ui.locale

import androidx.core.text.util.LocalePreferences
import java.util.Locale
import kotlin.math.roundToInt

private const val METERS_PER_MILE = 1609.344

private const val SECONDS_PER_HOUR = 3.6

internal enum class SpeedUnit { KILOMETERS_PER_HOUR, MILES_PER_HOUR }

internal enum class DistanceUnit { METERS, FEET }

internal enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

private val ImperialCountries = setOf("US", "GB", "MM")

/**
 * Fallback set of countries that use Fahrenheit for everyday weather, used when
 * [LocalePreferences] does not resolve Fahrenheit. Deliberately separate from
 * [ImperialCountries]: the UK (GB) reads road distances in miles but reports
 * weather in Celsius, so it must not appear here.
 *
 * Includes the US territories GU, VI, AS, and MP. They follow US weather
 * conventions but are absent from the country table that backs
 * [LocalePreferences.getTemperatureUnit] on the JVM (where `Build.VERSION` is
 * the unit-test stub), so this set is what carries them under the test runner.
 */
private val FahrenheitCountries =
    setOf("US", "BS", "BZ", "KY", "FM", "MH", "PW", "LR", "GU", "VI", "AS", "MP")

/** CLDR Unicode extension key for the measurement-unit (`-u-mu-`) override. */
private const val TEMPERATURE_OVERRIDE_KEY = "mu"

internal fun speedUnitFor(locale: Locale = Locale.getDefault()): SpeedUnit =
    if (locale.country in ImperialCountries) {
        SpeedUnit.MILES_PER_HOUR
    } else {
        SpeedUnit.KILOMETERS_PER_HOUR
    }

internal fun distanceUnitFor(locale: Locale = Locale.getDefault()): DistanceUnit =
    if (locale.country in ImperialCountries) {
        DistanceUnit.FEET
    } else {
        DistanceUnit.METERS
    }

/**
 * Resolve the temperature unit from CLDR via [LocalePreferences] first, then
 * fall back to [FahrenheitCountries].
 *
 * [LocalePreferences.getTemperatureUnit] returns a CLDR token; only
 * `FAHRENHEIT` ("fahrenhe") maps to Fahrenheit here, and `KELVIN` collapses to
 * Celsius for this two-value enum. The token honours an explicit `-u-mu-`
 * override (e.g. `en-US-u-mu-celsius`) on any platform and resolves US
 * territories via ICU on-device (API 33+).
 *
 * The country-set fallback only applies when no explicit `-u-mu-` override is
 * present: an override is the user's deliberate choice and must win over the
 * country default. Without an override, the fallback catches markets the
 * platform country table misses — notably US territories under the JVM test
 * runner, where the table that backs [LocalePreferences] lacks them.
 */
internal fun temperatureUnitFor(locale: Locale = Locale.getDefault()): TemperatureUnit =
    when {
        LocalePreferences.getTemperatureUnit(locale) == LocalePreferences.TemperatureUnit.FAHRENHEIT -> {
            TemperatureUnit.FAHRENHEIT
        }

        locale.getUnicodeLocaleType(TEMPERATURE_OVERRIDE_KEY) != null -> {
            TemperatureUnit.CELSIUS
        }

        locale.country in FahrenheitCountries -> {
            TemperatureUnit.FAHRENHEIT
        }

        else -> {
            TemperatureUnit.CELSIUS
        }
    }

internal fun SpeedUnit.fromMetersPerSecond(mps: Float): Float =
    when (this) {
        SpeedUnit.KILOMETERS_PER_HOUR -> mps * 3.6f
        SpeedUnit.MILES_PER_HOUR -> mps * 2.2369363f
    }

internal fun SpeedUnit.fromKilometersPerHour(kmh: Double): Double =
    when (this) {
        SpeedUnit.KILOMETERS_PER_HOUR -> kmh
        SpeedUnit.MILES_PER_HOUR -> kmh * 0.6213712
    }

/** Convert a metre distance to the odometer unit paired with this speed unit (km or miles). */
internal fun SpeedUnit.tripDistanceFromMeters(meters: Double): Double =
    when (this) {
        SpeedUnit.KILOMETERS_PER_HOUR -> meters / 1000.0
        SpeedUnit.MILES_PER_HOUR -> meters / METERS_PER_MILE
    }

/** Odometer unit label paired with this speed unit. */
internal fun SpeedUnit.distanceLabel(): String =
    when (this) {
        SpeedUnit.KILOMETERS_PER_HOUR -> "km"
        SpeedUnit.MILES_PER_HOUR -> "mi"
    }

internal fun DistanceUnit.fromMeters(meters: Double): Double =
    when (this) {
        DistanceUnit.METERS -> meters
        DistanceUnit.FEET -> meters * 3.2808399
    }

internal fun SpeedUnit.label(): String =
    when (this) {
        SpeedUnit.KILOMETERS_PER_HOUR -> "km/h"
        SpeedUnit.MILES_PER_HOUR -> "mph"
    }

/**
 * Format a wind speed (supplied in km/h by the weather provider) for the
 * dashboard, matching the speed unit the rest of the launcher shows. Imperial
 * locales read wind as mph (paired with the [SpeedOverlay] reading); metric
 * locales keep m/s per `docs/design/dashboard-v2-mockup.html`, which specs m/s
 * as the conventional meteorological wind unit outside imperial markets.
 */
internal fun windLabel(
    windKmh: Double,
    speedUnit: SpeedUnit,
): String =
    when (speedUnit) {
        SpeedUnit.MILES_PER_HOUR -> "${speedUnit.fromKilometersPerHour(windKmh).roundToInt()} mph"
        SpeedUnit.KILOMETERS_PER_HOUR -> "${(windKmh / SECONDS_PER_HOUR).roundToInt()} m/s"
    }

internal fun DistanceUnit.label(): String =
    when (this) {
        DistanceUnit.METERS -> "m"
        DistanceUnit.FEET -> "ft"
    }

internal fun TemperatureUnit.fromCelsius(celsius: Double): Double =
    when (this) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9 / 5 + 32
    }

internal fun TemperatureUnit.label(): String =
    when (this) {
        TemperatureUnit.CELSIUS -> "°C"
        TemperatureUnit.FAHRENHEIT -> "°F"
    }
