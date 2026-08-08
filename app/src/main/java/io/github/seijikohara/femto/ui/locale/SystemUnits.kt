package io.github.seijikohara.femto.ui.locale

import androidx.core.text.util.LocalePreferences
import io.github.seijikohara.femto.data.display.SpeedUnitSetting
import io.github.seijikohara.femto.data.display.TemperatureUnitSetting
import java.util.Locale
import kotlin.math.roundToInt

private const val METERS_PER_MILE = 1609.344

private const val SECONDS_PER_HOUR = 3.6

private const val MM_PER_INCH = 25.4

internal enum class SpeedUnit { KILOMETERS_PER_HOUR, MILES_PER_HOUR }

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

/** Resolve a user speed-unit setting to a concrete unit; AUTO follows [locale]. */
internal fun SpeedUnitSetting.resolved(locale: Locale = Locale.getDefault()): SpeedUnit =
    when (this) {
        SpeedUnitSetting.AUTO -> speedUnitFor(locale)
        SpeedUnitSetting.KILOMETERS -> SpeedUnit.KILOMETERS_PER_HOUR
        SpeedUnitSetting.MILES -> SpeedUnit.MILES_PER_HOUR
    }

/** Resolve a user temperature-unit setting to a concrete unit; AUTO follows [locale]. */
internal fun TemperatureUnitSetting.resolved(locale: Locale = Locale.getDefault()): TemperatureUnit =
    when (this) {
        TemperatureUnitSetting.AUTO -> temperatureUnitFor(locale)
        TemperatureUnitSetting.CELSIUS -> TemperatureUnit.CELSIUS
        TemperatureUnitSetting.FAHRENHEIT -> TemperatureUnit.FAHRENHEIT
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

internal fun SpeedUnit.label(): String =
    when (this) {
        SpeedUnit.KILOMETERS_PER_HOUR -> "km/h"
        SpeedUnit.MILES_PER_HOUR -> "mph"
    }

/**
 * Convert a wind speed (supplied in km/h by the weather provider) to the value
 * shown on the dashboard, in the unit paired with [speedUnit] (see
 * [windUnitLabel]) and rounded to a whole number. Imperial locales read wind as
 * mph (paired with the [SpeedOverlay] reading); metric locales keep m/s.
 */
internal fun windValue(
    windKmh: Double,
    speedUnit: SpeedUnit,
): Int =
    when (speedUnit) {
        SpeedUnit.MILES_PER_HOUR -> speedUnit.fromKilometersPerHour(windKmh).roundToInt()
        SpeedUnit.KILOMETERS_PER_HOUR -> (windKmh / SECONDS_PER_HOUR).roundToInt()
    }

/**
 * Wind unit glyph paired with this speed unit: mph in imperial locales, m/s in
 * metric locales — the conventional meteorological wind unit outside imperial
 * markets, a deliberate dashboard-v2 design decision.
 */
internal fun windUnitLabel(speedUnit: SpeedUnit): String =
    when (speedUnit) {
        SpeedUnit.MILES_PER_HOUR -> "mph"
        SpeedUnit.KILOMETERS_PER_HOUR -> "m/s"
    }

/**
 * Format a wind speed as a combined "value unit" string. The dashboard weather
 * card splits the two ([windValue] + [windUnitLabel]) to dim the unit; the
 * maximize panels keep this combined form.
 */
internal fun windLabel(
    windKmh: Double,
    speedUnit: SpeedUnit,
): String = "${windValue(windKmh, speedUnit)} ${windUnitLabel(speedUnit)}"

/**
 * Precipitation unit glyph paired with this speed unit: inches in imperial
 * locales, millimetres elsewhere. Derived from the speed setting rather than a
 * setting of its own, the same way [windUnitLabel] is — a driver who reads miles
 * reads rainfall in inches.
 */
internal fun precipitationUnitLabel(speedUnit: SpeedUnit): String =
    when (speedUnit) {
        SpeedUnit.MILES_PER_HOUR -> "in"
        SpeedUnit.KILOMETERS_PER_HOUR -> "mm"
    }

/**
 * Format a precipitation amount (supplied in millimetres by the weather
 * provider) for the unit paired with [speedUnit], without the unit glyph.
 * Inches take a second decimal: the conventional imperial resolution is
 * hundredths, and a single decimal would collapse every ordinary shower to
 * "0.0".
 */
internal fun precipitationValueLabel(
    mm: Double,
    speedUnit: SpeedUnit,
): String =
    when (speedUnit) {
        SpeedUnit.MILES_PER_HOUR -> "%.2f".format(mm / MM_PER_INCH)
        SpeedUnit.KILOMETERS_PER_HOUR -> "%.1f".format(mm)
    }

/**
 * Smallest amount, in millimetres, that [precipitationValueLabel] can print as a
 * non-zero number in the unit paired with [speedUnit] — one step of the last
 * decimal it renders.
 *
 * The cut-off follows the display unit because it exists to stop rounding from
 * inventing rain: 0.09 mm renders as "0.1", and 0.2 mm renders as "0.01 in", so
 * a fixed metric threshold would let a trace read as real rainfall on one side
 * and print "0.00 in" on the other.
 */
internal fun precipitationDryThresholdMm(speedUnit: SpeedUnit): Double =
    when (speedUnit) {
        SpeedUnit.MILES_PER_HOUR -> MM_PER_INCH / 100
        SpeedUnit.KILOMETERS_PER_HOUR -> 0.1
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
