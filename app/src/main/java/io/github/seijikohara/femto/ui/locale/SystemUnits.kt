package io.github.seijikohara.femto.ui.locale

import java.util.Locale

internal enum class SpeedUnit { KILOMETERS_PER_HOUR, MILES_PER_HOUR }

internal enum class DistanceUnit { METERS, FEET }

private val ImperialCountries = setOf("US", "GB", "MM")

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

internal fun SpeedUnit.fromMetersPerSecond(mps: Float): Float =
    when (this) {
        SpeedUnit.KILOMETERS_PER_HOUR -> mps * 3.6f
        SpeedUnit.MILES_PER_HOUR -> mps * 2.2369363f
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
