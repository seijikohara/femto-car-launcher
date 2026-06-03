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
 * and emits a running [TripState] each time a new fix lands. Three
 * filters guard against the worst of GPS noise:
 *
 *  - Fixes whose *effective* speed (see below) is below
 *    `MIN_MOVING_SPEED_MS` (≈ 1.8 km/h) are treated as "not moving" —
 *    distance and time both stop accruing. This stops the average from
 *    collapsing toward zero at long stops.
 *  - Fixes whose elapsed-time delta is non-positive or larger than
 *    `MAX_GAP_SECONDS` (a missing-fix gap, e.g. a tunnel) are skipped —
 *    we do not interpolate across them.
 *  - The delta is taken from `elapsedRealtimeNanos`, the monotonic boot
 *    clock, not `Location.time`. AI boxes routinely apply an NTP /
 *    system-clock correction mid-trip; the wall clock can jump
 *    backward or forward, but the boot clock keeps ticking, so it is
 *    the only safe basis for a delta. (Task 5.8.)
 *
 * "Effective speed" decouples accrual from the GPS chip's speed report.
 * Cheap chips and raw `GPS_PROVIDER` HALs leave `Location.speed` at
 * `0.0` and `Location.hasSpeed() == false`. Gating on the reported
 * speed would then freeze distance/average at zero for the whole trip.
 * Instead, when the fix carries no speed we derive one from the
 * position delta (`previous.distanceTo(current) / deltaSeconds`).
 * (Task 2.3.)
 *
 * The accumulators ([lastLocation], [totalMeters], [totalSeconds]) live
 * on the instance, not inside the cold `flow {}`. Under
 * `stateIn(WhileSubscribed)` the upstream collection stops ~5 s after
 * the last subscriber leaves (e.g. the head unit foregrounds another
 * app) and restarts when a subscriber returns; if the accumulators
 * lived in the flow body they would reset to zero on every restart,
 * silently breaking the "since process start" contract. Hoisting them
 * makes the subscription window gate only *UI delivery*, never the
 * accumulated total. WhileSubscribed guarantees a single live upstream
 * collection at a time, so the plain (non-atomic) fields are safe.
 *
 * Accrual still *pauses* while fully backgrounded — there is no eager
 * always-on GPS scope here — but the running total survives the gap and
 * resumes on the next fix. This composes with the later location
 * `shareIn` work. Trip resets are out of scope for this pass.
 */
internal class TripRepository(
    private val locationFlow: Flow<Location?>,
) {
    // Hoisted out of the cold flow {} so the running total survives a
    // WhileSubscribed stop/restart; see the class KDoc.
    private var lastLocation: Location? = null
    private var totalMeters = 0.0
    private var totalSeconds = 0.0
    private var currentSpeedMs = 0.0

    fun stateFlow(): Flow<TripState> =
        flow {
            // Replay the running total to a (re)subscriber instead of a
            // hardcoded Initial, so distance does not appear to reset to
            // zero when the window reopens.
            emit(snapshot())
            locationFlow.collect { current ->
                if (current == null) return@collect
                accrue(current)
                emit(snapshot())
            }
        }.flowOn(Dispatchers.Default)

    private fun accrue(current: Location) {
        val previous = lastLocation
        if (previous != null) {
            val deltaSeconds =
                (current.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / NANOS_PER_SECOND
            if (deltaSeconds in MIN_DELTA_SECONDS..MAX_GAP_SECONDS) {
                val effectiveSpeed = effectiveSpeed(previous, current, deltaSeconds)
                currentSpeedMs = effectiveSpeed
                if (effectiveSpeed >= MIN_MOVING_SPEED_MS) {
                    totalMeters += previous.distanceTo(current).toDouble()
                    totalSeconds += deltaSeconds
                }
            }
        }
        lastLocation = current
    }

    // The chip's reported speed when it has one; otherwise the
    // position-derived speed so speed-less HALs still register motion.
    private fun effectiveSpeed(
        previous: Location,
        current: Location,
        deltaSeconds: Double,
    ): Double =
        if (current.hasSpeed()) {
            current.speed.toDouble()
        } else {
            previous.distanceTo(current).toDouble() / deltaSeconds
        }

    private fun snapshot(): TripState =
        TripState(
            distanceMeters = totalMeters,
            avgSpeedMs = if (totalSeconds > 0.0) totalMeters / totalSeconds else 0.0,
            currentSpeedMs = currentSpeedMs,
        )

    private companion object {
        // ~1.8 km/h — below this the device is treated as stationary.
        const val MIN_MOVING_SPEED_MS = 0.5

        const val NANOS_PER_SECOND = 1_000_000_000.0

        // Must be strictly positive; covers same-instant emissions where
        // the boot clock didn't advance.
        const val MIN_DELTA_SECONDS = 0.001

        // Drop pairs separated by a long gap (parked, lost fix, tunnel).
        const val MAX_GAP_SECONDS = 60.0
    }
}
