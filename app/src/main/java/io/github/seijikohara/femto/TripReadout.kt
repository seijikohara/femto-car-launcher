package io.github.seijikohara.femto

import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.distanceLabel
import io.github.seijikohara.femto.ui.locale.fromMetersPerSecond
import io.github.seijikohara.femto.ui.locale.label
import io.github.seijikohara.femto.ui.locale.tripDistanceFromMeters
import kotlin.math.roundToInt

/**
 * Display-ready trip strings for the background-ranging notification, formatted
 * with the same [SpeedUnit] helpers the on-screen `SpeedOverlay` uses so the
 * notification and the dashboard never disagree on units or rounding. This
 * bridge type lives at the app root (not under `data/`) because it composes the
 * `ui/locale` formatting SSOT, which `data/` may not import.
 */
internal data class TripReadout(
    val speed: String,
    val distance: String,
    val averageSpeed: String,
)

/** Format the running trip metrics for [speedUnit] (km/h + km, or mph + mi). */
internal fun TripState.tripReadout(speedUnit: SpeedUnit): TripReadout =
    TripReadout(
        speed = formatSpeed(currentSpeedMs, speedUnit),
        distance = formatDistance(distanceMeters, speedUnit),
        averageSpeed = formatSpeed(avgSpeedMs, speedUnit),
    )

private fun formatSpeed(
    metersPerSecond: Double,
    speedUnit: SpeedUnit,
): String = "${speedUnit.fromMetersPerSecond(metersPerSecond.toFloat()).roundToInt()} ${speedUnit.label()}"

// One decimal of odometer precision, localised separator (the dashboard does the
// same); roundToInt would lose the sub-unit progress a slow trip shows.
private fun formatDistance(
    meters: Double,
    speedUnit: SpeedUnit,
): String = "%.1f %s".format(speedUnit.tripDistanceFromMeters(meters), speedUnit.distanceLabel())
