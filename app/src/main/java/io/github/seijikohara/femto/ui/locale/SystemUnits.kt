package io.github.seijikohara.femto.ui.locale

import java.util.Locale

private const val METERS_PER_MILE = 1609.344

internal enum class SpeedUnit { KILOMETERS_PER_HOUR, MILES_PER_HOUR }

internal enum class DistanceUnit { METERS, FEET }

internal enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

private val ImperialCountries = setOf("US", "GB", "MM")

/**
 * Countries that use Fahrenheit for everyday weather. Deliberately separate
 * from [ImperialCountries]: the UK (GB) reads road distances in miles but
 * reports weather in Celsius, so it must not appear here.
 */
private val FahrenheitCountries = setOf("US", "BS", "BZ", "KY", "FM", "MH", "PW", "LR")

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

internal fun temperatureUnitFor(locale: Locale = Locale.getDefault()): TemperatureUnit =
    if (locale.country in FahrenheitCountries) {
        TemperatureUnit.FAHRENHEIT
    } else {
        TemperatureUnit.CELSIUS
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
