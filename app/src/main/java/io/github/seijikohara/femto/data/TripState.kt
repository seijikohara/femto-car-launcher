package io.github.seijikohara.femto.data

import androidx.compose.runtime.Immutable

/**
 * Cumulative trip metrics for the dashboard's speed overlay.
 *
 * `distanceMeters` is the total ground distance covered since the
 * activity (and therefore the launcher process) started. `avgSpeedMs`
 * is the moving average — total distance divided by the time the
 * device was actually moving (jitter at rest is filtered out at the
 * repository level).
 *
 * Both default to zero so the speed overlay always renders; a fresh
 * subscriber sees `Initial` until the first non-trivial location pair
 * has been observed.
 */
@Immutable
data class TripState(
    val distanceMeters: Double,
    val avgSpeedMs: Double,
) {
    companion object {
        val Initial: TripState = TripState(distanceMeters = 0.0, avgSpeedMs = 0.0)
    }
}
