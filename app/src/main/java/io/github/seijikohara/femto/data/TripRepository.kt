package io.github.seijikohara.femto.data

import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * In-memory trip aggregator.
 *
 * Subscribes to the shared location flow, walks the sequence of fixes,
 * and emits a running [TripState] each time a new fix lands. Several
 * filters guard against the worst of GPS noise:
 *
 *  - Only `GPS_PROVIDER` (and test) fixes feed the trip math.
 *    `NETWORK_PROVIDER` fixes (cell-tower / Wi-Fi) are accurate enough
 *    for map centring but jump tens-to-hundreds of metres between
 *    updates; mixing them in injected phantom distance and the
 *    impossible average speeds reported on-device, so they are filtered
 *    out before the accrual sequence (the GPS anchor is never advanced
 *    by a network fix).
 *  - A fix whose *effective* speed (see below) exceeds
 *    `MAX_PLAUSIBLE_SPEED_MS` (≈ 396 km/h) is treated as noise and
 *    dropped entirely — neither published as the current speed nor
 *    accrued — so one glitch can never poison the running totals.
 *  - Fixes whose *effective* speed is below `MIN_MOVING_SPEED_MS`
 *    (≈ 1.8 km/h) are treated as "not moving": distance and time both
 *    stop accruing, so the average does not collapse toward zero at long
 *    stops.
 *  - Fixes whose elapsed-time delta is non-positive or larger than
 *    `MAX_GAP_SECONDS` (a missing-fix gap, e.g. a tunnel) are skipped —
 *    we do not interpolate across them.
 *  - The delta is taken from `elapsedRealtimeNanos`, the monotonic boot
 *    clock, not `Location.time`. AI boxes routinely apply an NTP /
 *    system-clock correction mid-trip; the wall clock can jump
 *    backward or forward, but the boot clock keeps ticking, so it is
 *    the only safe basis for a delta.
 *
 * "Effective speed" decouples accrual from the GPS chip's speed report.
 * Cheap chips and raw `GPS_PROVIDER` HALs leave `Location.speed` at
 * `0.0` and `Location.hasSpeed() == false`. Gating on the reported
 * speed would then freeze distance/average at zero for the whole trip.
 * Instead, when the fix carries no speed we derive one from the
 * position delta (`previous.distanceTo(current) / deltaSeconds`) — but
 * only when `deltaSeconds` is at least `MIN_TRUSTWORTHY_DELTA_SECONDS`.
 * A sub-second gap (two near-simultaneous fixes) would divide a real
 * position delta into an absurd speed, so below that floor the
 * position-derived path yields no reading and the fix contributes
 * nothing.
 *
 * The accumulators ([lastLocation], [totalMeters], [totalSeconds]) live
 * on the instance, not inside the cold `flow {}`. Under
 * `stateIn(WhileSubscribed)` the upstream collection stops ~5 s after
 * the last subscriber leaves (e.g. the head unit foregrounds another
 * app) and restarts when a subscriber returns; if the accumulators
 * lived in the flow body they would reset to zero on every restart,
 * silently breaking the "since process start" contract. Hoisting them
 * makes the subscription window gate only *UI delivery*, never the
 * accumulated total.
 *
 * Location fixes and explicit [reset] requests are merged into a single
 * stream consumed by one sequential `collect`, so the plain (non-atomic)
 * accumulator fields are only ever read and written from that one
 * sequence — a reset can never interleave with an accrual.
 *
 * Accrual still *pauses* while fully backgrounded — there is no eager
 * always-on GPS scope here — but the running total survives the gap and
 * resumes on the next fix.
 */
internal class TripRepository(
    private val locationFlow: Flow<Location?>,
    // The accrual sequence runs here. Production uses the default compute pool;
    // tests inject a TestDispatcher so an explicit [reset] (a non-suspending
    // tryEmit) and the resulting emission are observed deterministically under
    // runTest's virtual clock.
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    // Hoisted out of the cold flow {} so the running total survives a
    // WhileSubscribed stop/restart; see the class KDoc.
    private var lastLocation: Location? = null
    private var totalMeters = 0.0
    private var totalSeconds = 0.0
    private var currentSpeedMs = 0.0

    // Push channel for an explicit trip reset. extraBufferCapacity = 1 lets a
    // single tryEmit from the UI thread land without a suspended collector. The
    // reset is applied inside the merged collect (below), on the same single
    // sequence as accrual, so the accumulators are never mutated concurrently.
    private val resetSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun stateFlow(): Flow<TripState> =
        flow {
            // Replay the running total to a (re)subscriber instead of a
            // hardcoded Initial, so distance does not appear to reset to
            // zero when the window reopens.
            emit(snapshot())
            // Trip math is GPS-only: drop null fixes and NETWORK_PROVIDER fixes
            // (cell-tower / Wi-Fi) before they reach the accrual sequence, so the
            // GPS anchor is never advanced by a network jump and a dropped fix
            // never produces a redundant emission.
            val fixes: Flow<TripSignal> =
                locationFlow
                    .filterNotNull()
                    .filterNot { it.provider == LocationManager.NETWORK_PROVIDER }
                    .map { TripSignal.Fix(it) }
            val resets: Flow<TripSignal> = resetSignals.map { TripSignal.Reset }
            merge(fixes, resets).collect { signal ->
                when (signal) {
                    is TripSignal.Fix -> {
                        accrue(signal.location)
                        emit(snapshot())
                    }

                    TripSignal.Reset -> {
                        resetAccumulators()
                        emit(snapshot())
                    }
                }
            }
        }.flowOn(dispatcher)

    /**
     * Clear the running trip totals (Distance, Avg, and the current speed).
     *
     * Pushes a reset through [resetSignals] so the cleared [TripState] reaches
     * collectors immediately, rather than waiting for the next location fix
     * (the device may be parked for minutes after the user taps reset).
     */
    fun reset() {
        resetSignals.tryEmit(Unit)
    }

    // Receives only GPS (non-network, non-null) fixes; network fixes are filtered
    // upstream in stateFlow so they never advance the anchor.
    private fun accrue(current: Location) {
        val previous = lastLocation
        if (previous != null) {
            val deltaSeconds =
                (current.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / NANOS_PER_SECOND
            if (deltaSeconds in MIN_DELTA_SECONDS..MAX_GAP_SECONDS) {
                val speed = effectiveSpeed(previous, current, deltaSeconds)
                // Drop implausible speeds (teleport jumps, bad chip reports,
                // sub-second gaps): do not publish or accrue them.
                if (speed != null && speed <= MAX_PLAUSIBLE_SPEED_MS) {
                    currentSpeedMs = speed
                    // Count every tracked interval toward the average's time base,
                    // including time spent stopped, so AVG is the overall trip
                    // average (distance / elapsed) rather than a moving-only average.
                    // Long gaps are already excluded by the deltaSeconds window above.
                    totalSeconds += deltaSeconds
                    if (speed >= MIN_MOVING_SPEED_MS) {
                        totalMeters += previous.distanceTo(current).toDouble()
                    }
                }
            }
        }
        lastLocation = current
    }

    // The chip's reported speed when it has one; otherwise the position-derived
    // speed, but only when the elapsed-time base is long enough to trust.
    // Returns null when a speed-less fix arrives too soon after the previous one
    // for the derived speed to be meaningful.
    private fun effectiveSpeed(
        previous: Location,
        current: Location,
        deltaSeconds: Double,
    ): Double? =
        when {
            current.hasSpeed() -> {
                current.speed.toDouble()
            }

            deltaSeconds >= MIN_TRUSTWORTHY_DELTA_SECONDS -> {
                previous.distanceTo(current).toDouble() / deltaSeconds
            }

            else -> {
                null
            }
        }

    private fun resetAccumulators() {
        // Drop the anchor too so the trip restarts cleanly from the next fix
        // rather than charging the gap between the reset and that fix.
        lastLocation = null
        totalMeters = 0.0
        totalSeconds = 0.0
        currentSpeedMs = 0.0
    }

    private fun snapshot(): TripState =
        TripState(
            distanceMeters = totalMeters,
            avgSpeedMs = if (totalSeconds > 0.0) totalMeters / totalSeconds else 0.0,
            currentSpeedMs = currentSpeedMs,
        )

    // Merged input to the single accrual sequence: a GPS location fix or a reset.
    private sealed interface TripSignal {
        data class Fix(
            val location: Location,
        ) : TripSignal

        data object Reset : TripSignal
    }

    private companion object {
        // ~1.8 km/h — below this the device is treated as stationary.
        const val MIN_MOVING_SPEED_MS = 0.5

        const val NANOS_PER_SECOND = 1_000_000_000.0

        // Must be strictly positive; covers same-instant emissions where
        // the boot clock didn't advance.
        const val MIN_DELTA_SECONDS = 0.001

        // Drop pairs separated by a long gap (parked, lost fix, tunnel).
        const val MAX_GAP_SECONDS = 60.0

        // Minimum elapsed time before a position-derived speed is trusted.
        // Below this a sub-second inter-fix gap manufactures an impossible
        // speed from a real position delta.
        const val MIN_TRUSTWORTHY_DELTA_SECONDS = 0.5

        // Plausible vehicle-speed ceiling (~396 km/h). Any effective speed
        // above this is GPS noise and is dropped from all three metrics.
        const val MAX_PLAUSIBLE_SPEED_MS = 110.0
    }
}
