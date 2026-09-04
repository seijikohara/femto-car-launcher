package io.github.seijikohara.femto.data.location

/**
 * Cumulative trip metrics for the dashboard's speed overlay.
 *
 * A trip runs from one boundary to the next — the user's reset tap or, with
 * [TripAutoResetSetting] timed, the first fix after the car sat parked past the
 * configured gap; the totals survive process restarts (persisted via
 * [TripStateStore]). `distanceMeters` is the total ground distance covered
 * this trip.
 * `avgSpeedMs` is the overall trip average — total distance divided by the
 * total tracked time, including time spent stopped (only untracked gaps, e.g.
 * the app backgrounded or the process dead, are excluded at the repository
 * level). `currentSpeedMs` is the latest instantaneous effective speed: the
 * reported fix speed when the GPS chip supplies one, otherwise the
 * position-derived speed so the hero numeral still moves on speed-less HALs.
 * `startedAtEpochMs` is the wall-clock time of the trip's first accepted GPS
 * fix — null until one lands after a boundary (or on the very first run).
 *
 * All metrics default to zero so the speed overlay always renders; a fresh
 * subscriber sees the running total (or `Initial` before the first
 * non-trivial location pair has been observed).
 */
internal data class TripState(
    val distanceMeters: Double,
    val avgSpeedMs: Double,
    val currentSpeedMs: Double,
    val startedAtEpochMs: Long? = null,
) {
    /**
     * Whether the vehicle is parked, i.e. below the [MIN_MOVING_SPEED_MS] floor
     * the repository already uses to reject GPS drift. One definition of "parked"
     * for every reader: the dashboard gates its scrolling text on it, so the card
     * and the full-screen player cannot disagree about when the car is moving.
     */
    val stationary: Boolean get() = currentSpeedMs < MIN_MOVING_SPEED_MS

    companion object {
        val Initial: TripState = TripState(distanceMeters = 0.0, avgSpeedMs = 0.0, currentSpeedMs = 0.0)
    }
}
