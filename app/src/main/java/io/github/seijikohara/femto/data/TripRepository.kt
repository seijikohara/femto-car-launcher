package io.github.seijikohara.femto.data

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * In-memory trip aggregator.
 *
 * Subscribes to the shared location flow, walks the sequence of fixes,
 * and emits a running [TripState] each time a new fix lands. Two simple
 * filters guard against the worst of GPS noise:
 *
 *  - Fixes reporting `speed < MIN_MOVING_SPEED_MS` (≈ 1.8 km/h) are
 *    treated as "not moving" — distance and time both stop accruing.
 *    This stops the average from collapsing toward zero at long stops.
 *  - Fixes whose timestamp delta is non-positive or larger than
 *    `MAX_GAP_MS` (a missing-fix gap, e.g. a tunnel) are skipped — we
 *    do not interpolate across them.
 *
 * Trip resets are out of scope for this pass; the aggregator lives for
 * the lifetime of the [ViewModel] subscription.
 */
internal class TripRepository(
    private val locationFlow: Flow<Location?>,
) {
    fun stateFlow(): Flow<TripState> =
        flow {
            emit(TripState.Initial)
            var lastLocation: Location? = null
            var totalMeters = 0.0
            var totalSeconds = 0.0
            locationFlow.collect { current ->
                if (current == null) return@collect
                val previous = lastLocation
                if (previous != null && current.speed >= MIN_MOVING_SPEED_MS) {
                    val deltaSeconds = (current.time - previous.time) / 1000.0
                    if (deltaSeconds in MIN_DELTA_SECONDS..MAX_GAP_SECONDS) {
                        totalMeters += previous.distanceTo(current).toDouble()
                        totalSeconds += deltaSeconds
                    }
                }
                lastLocation = current
                val avg = if (totalSeconds > 0.0) totalMeters / totalSeconds else 0.0
                emit(TripState(distanceMeters = totalMeters, avgSpeedMs = avg))
            }
        }.flowOn(Dispatchers.Default)

    private companion object {
        // ~1.8 km/h — below this the device is treated as stationary.
        const val MIN_MOVING_SPEED_MS = 0.5f

        // Must be strictly positive; covers same-second emissions where
        // the timestamp didn't tick over.
        const val MIN_DELTA_SECONDS = 0.001

        // Drop pairs separated by a long gap (parked, lost fix, tunnel).
        const val MAX_GAP_SECONDS = 60.0
    }
}
