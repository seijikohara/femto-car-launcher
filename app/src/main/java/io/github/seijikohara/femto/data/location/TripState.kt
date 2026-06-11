package io.github.seijikohara.femto.data.location

import androidx.compose.runtime.Immutable

/**
 * Cumulative trip metrics for the dashboard's speed overlay.
 *
 * `distanceMeters` is the total ground distance covered since the
 * activity (and therefore the launcher process) started. `avgSpeedMs`
 * is the overall trip average — total distance divided by the total
 * tracked time, including time spent stopped (only untracked gaps, e.g.
 * the app backgrounded, are excluded at the repository level).
 * `currentSpeedMs` is the latest instantaneous
 * effective speed: the reported fix speed when the GPS chip supplies
 * one, otherwise the position-derived speed so the hero numeral still
 * moves on speed-less HALs.
 *
 * All default to zero so the speed overlay always renders; a fresh
 * subscriber sees the running total (or `Initial` before the first
 * non-trivial location pair has been observed).
 */
@Immutable
data class TripState(
    val distanceMeters: Double,
    val avgSpeedMs: Double,
    val currentSpeedMs: Double,
) {
    companion object {
        val Initial: TripState = TripState(distanceMeters = 0.0, avgSpeedMs = 0.0, currentSpeedMs = 0.0)
    }
}
