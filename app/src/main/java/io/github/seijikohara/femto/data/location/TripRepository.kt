package io.github.seijikohara.femto.data.location

import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

/**
 * Trip aggregator with restart-surviving totals.
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
 *  - A fix that is already older than `MAX_GAP_SECONDS` when it arrives
 *    (the cached `getLastKnownLocation` seed replayed on subscribe — it
 *    can be hours old after a restart) never enters the chain. Letting
 *    it anchor would let the parked-gap rule charge the whole dead
 *    period to the restored average, and would hand the track log a
 *    phantom point at a long-gone position.
 *  - A fix whose *effective* speed (see below) exceeds
 *    `MAX_PLAUSIBLE_SPEED_MS` (≈ 396 km/h) is treated as noise and
 *    dropped entirely — neither published as the current speed nor
 *    accrued — so one glitch can never poison the running totals.
 *  - Fixes whose *effective* speed is below [MIN_MOVING_SPEED_MS]
 *    (≈ 1.8 km/h) are treated as "not moving": distance stops accruing,
 *    but their time still counts toward the average's time base, so AVG
 *    is the overall trip average (distance / elapsed incl. stops), not
 *    a moving-only average.
 *  - Fixes whose elapsed-time delta is non-positive or larger than
 *    `MAX_GAP_SECONDS` are not interpolated across. A long gap that
 *    began at a standstill (distance-filtered updates stop while
 *    parked) still counts as stopped time — the car verifiably sat
 *    still — while a gap that began while moving (tunnel, backgrounded
 *    mid-drive) is skipped entirely: its distance is unknowable, and
 *    counting only its time would corrupt the average.
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
 * position-derived path yields no reading and the fix *defers*: the
 * anchor stays put, the delta keeps growing across subsequent fixes,
 * and accrual happens in one step once the accumulated delta clears
 * the floor. (Advancing the anchor instead would reset the delta on
 * every fix and freeze distance forever at sub-second cadence.)
 *
 * The accumulators ([lastLocation], [totalMeters], [totalSeconds]) live
 * on the instance, not inside the cold `flow {}`. Under
 * `stateIn(WhileSubscribed)` the upstream collection stops ~5 s after
 * the last subscriber leaves (e.g. the head unit foregrounds another
 * app) and restarts when a subscriber returns; if the accumulators
 * lived in the flow body they would reset to zero on every restart.
 * Hoisting them makes the subscription window gate only *UI delivery*,
 * never the accumulated total.
 *
 * The totals also survive process death: they are seeded once per
 * process from [TripStateStore] before the first emission, and written
 * back synchronously from inside the single accrual sequence — throttled
 * to one write per `PERSIST_MIN_INTERVAL_NANOS` while fixes flow,
 * write-through on a reset (so the zeros are durable before the next
 * signal, and a crash after that write can never resurrect the old
 * totals or regress the trip id behind already-committed track points),
 * and once more when the subscription window closes. Writing on the
 * accrual sequence keeps every write totally ordered with the mutations
 * it snapshots — the reset's zeros can never be overtaken by an earlier
 * periodic snapshot — and DataStore does its own IO off this dispatcher,
 * so a write suspends the sequence only briefly (at most once per 5 s).
 * The GPS anchor is deliberately NOT restored: `elapsedRealtimeNanos` is
 * boot-relative, so the first fix of the new process re-anchors and the
 * dead period contributes neither time nor distance (the same semantics
 * as an unknown-onset gap). The user's reset tap is therefore the only
 * deliberate way a trip ends; a restart merely pauses it.
 *
 * A trip spans reset to reset. [TripState.startedAtEpochMs] marks the
 * wall-clock time of the first GPS fix accepted after a reset (not the
 * tap itself — the car may sit parked long after it), which is also the
 * first point the track logger sees for the trip: [trackTap] is invoked
 * for every RECORDABLE fix from inside the merged collect (never the
 * upstream producer — that would read the trip id on a different
 * coroutine and race a concurrent reset), tagged with the current trip
 * id, a monotonic counter that increments on each reset and delimits
 * reset-to-reset trips in the track log. A fix whose effective speed is
 * an implausible teleport is not recordable: it is excluded from the
 * track log for the same reason the trip math refuses it.
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
    private val store: TripStateStore,
    // Invoked for every recordable GPS fix from inside the single accrual
    // sequence (see class KDoc). Production wires the track logger; the default
    // keeps tests that only exercise trip math quiet.
    private val trackTap: TripFixTap = TripFixTap { _, _ -> },
    // The accrual sequence runs here. Production uses the default compute pool;
    // tests inject a TestDispatcher so an explicit [reset] (a non-suspending
    // tryEmit) and the resulting emission are observed deterministically under
    // runTest's virtual clock.
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Boot-clock source for the arrival-staleness filter; tests inject a fixed
    // value so fixture nanos stay deterministic.
    private val nowElapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
    // Hoisted out of the cold flow {} so the running total survives a
    // WhileSubscribed stop/restart; see the class KDoc.
    private var lastLocation: Location? = null
    private var totalMeters = 0.0
    private var totalSeconds = 0.0
    private var currentSpeedMs = 0.0
    private var startedAtEpochMs: Long? = null
    private var tripId = 0L

    // Whether currentSpeedMs has been published at least once this trip.
    // Distinguishes a real standstill (0.0 from a stationary fix) from the
    // not-yet-established default 0.0 at trip start / after a reset, so the
    // parked-gap rule never misreads an unknown-onset gap as parked.
    private var speedEstablished = false

    // One-shot per process: the persisted totals seed the accumulators before
    // the first emission. Guarded inside the single collect sequence, so a
    // WhileSubscribed restart can never re-apply them.
    private var restored = false

    // Boot-clock time of the last periodic write; null forces a write on the
    // first accepted fix (capturing the freshly set trip start).
    private var lastPersistElapsedNanos: Long? = null

    // Push channel for an explicit trip reset. extraBufferCapacity = 1 lets a
    // single tryEmit from the UI thread land without a suspended collector. The
    // reset is applied inside the merged collect (below), on the same single
    // sequence as accrual, so the accumulators are never mutated concurrently.
    private val resetSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun stateFlow(): Flow<TripState> =
        flow {
            restoreOnce()
            // Replay the running total to a (re)subscriber instead of a
            // hardcoded Initial, so distance does not appear to reset to
            // zero when the window reopens.
            emit(snapshot())
            // Trip math is GPS-only: drop null fixes, NETWORK_PROVIDER fixes
            // (cell-tower / Wi-Fi), and stale cached seeds before they reach the
            // accrual sequence, so the GPS anchor is never advanced by a network
            // jump and a dropped fix never produces a redundant emission.
            val fixes: Flow<TripSignal> =
                locationFlow
                    .filterNotNull()
                    .filterNot { it.provider == LocationManager.NETWORK_PROVIDER }
                    .filterNot { arrivedStale(it) }
                    .map { TripSignal.Fix(it) }
            val resets: Flow<TripSignal> = resetSignals.map { TripSignal.Reset }
            try {
                merge(fixes, resets).collect { signal ->
                    when (signal) {
                        is TripSignal.Fix -> {
                            // Tap here, on the single sequence, so the trip id the
                            // track logger reads is the one this fix accrues into,
                            // totally ordered with resets (see class KDoc). Skip
                            // implausible teleports the trip math rejects.
                            if (accrue(signal.location)) {
                                trackTap.onFix(signal.location, tripId)
                            }
                            emit(snapshot())
                            maybePersist(signal.location.elapsedRealtimeNanos)
                        }

                        TripSignal.Reset -> {
                            resetAccumulators()
                            emit(snapshot())
                            // Write-through (not a fire-and-forget enqueue) so the
                            // zeros and the bumped trip id are durable before the
                            // next signal: a crash after this can never resurrect
                            // the old totals or reuse the previous trip's id.
                            store.write(persisted())
                        }
                    }
                }
            } finally {
                // Flush the last accrued state when the subscription window closes
                // (UI gone / service stopped) — the periodic throttle alone could
                // leave up to PERSIST_MIN_INTERVAL_NANOS unwritten. NonCancellable
                // because the close path is a cancellation.
                withContext(NonCancellable) { store.write(persisted()) }
            }
        }.flowOn(dispatcher)

    /**
     * Clear the running trip totals (Distance, Avg, and the current speed) and
     * start the next reset-to-reset trip.
     *
     * Pushes a reset through [resetSignals] so the cleared [TripState] reaches
     * collectors immediately, rather than waiting for the next location fix
     * (the device may be parked for minutes after the user taps reset).
     */
    fun reset() {
        resetSignals.tryEmit(Unit)
    }

    private suspend fun restoreOnce() {
        if (restored) return
        val persisted = store.read()
        totalMeters = persisted.totalMeters
        totalSeconds = persisted.totalSeconds
        startedAtEpochMs = persisted.startedAtEpochMs
        tripId = persisted.tripId
        restored = true
    }

    // A cached getLastKnownLocation seed can be hours old at arrival; anything
    // older than the accrual gap window never enters the chain (class KDoc).
    private fun arrivedStale(fix: Location): Boolean =
        (nowElapsedRealtimeNanos() - fix.elapsedRealtimeNanos) > MAX_GAP_SECONDS * NANOS_PER_SECOND

    // Receives only GPS (non-network, non-null, non-stale) fixes; the rest are
    // filtered upstream in stateFlow so they never advance the anchor. Returns
    // whether the fix is recordable — true for any trustworthy position, false
    // only for an implausible teleport (which neither accrues nor is logged).
    private fun accrue(current: Location): Boolean {
        if (startedAtEpochMs == null) {
            // The trip starts at its first accepted fix — the same fix the
            // track logger records first — not at the reset tap; see class KDoc.
            startedAtEpochMs = current.time
        }
        val previous = lastLocation
        if (previous == null) {
            lastLocation = current
            return true
        }
        val deltaSeconds =
            (current.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / NANOS_PER_SECOND
        if (deltaSeconds !in MIN_DELTA_SECONDS..MAX_GAP_SECONDS) {
            // Non-advancing clock or a long gap: re-anchor without
            // interpolating distance across it. A gap that began at a
            // standstill is the parked case (distance-filtered updates stop
            // while stationary), so the gap itself counts as stopped time in
            // the average's time base; a gap that began while moving
            // (tunnel, backgrounded mid-drive) is skipped entirely — its
            // distance is unknowable, and counting only its time would
            // corrupt the average. The anchor fix's own reported speed is
            // the most direct evidence of how the gap began; speed-less
            // chips fall back to the last published effective speed, and an
            // onset with no established speed at all (trip start / just
            // after a reset) is treated as unknown and skipped — the old
            // conservative behaviour.
            val anchorStationary =
                when {
                    previous.hasSpeed() -> previous.speed < MIN_MOVING_SPEED_MS
                    speedEstablished -> currentSpeedMs < MIN_MOVING_SPEED_MS
                    else -> false
                }
            if (deltaSeconds > MAX_GAP_SECONDS && anchorStationary) {
                totalSeconds += deltaSeconds
            }
            lastLocation = current
            return true
        }
        // A speed-less fix below the trust floor keeps the anchor: the delta then
        // keeps growing toward MIN_TRUSTWORTHY_DELTA_SECONDS instead of resetting
        // on every fix. Without this, a sub-second fix cadence (interval < 500 ms
        // on a chip that never reports speed) would freeze distance forever. The
        // position is still a real fix, so it stays recordable.
        val speed = effectiveSpeed(previous, current, deltaSeconds) ?: return true
        lastLocation = current
        // Drop implausible speeds (teleport jumps, bad chip reports): re-anchor
        // but do not publish, accrue, or record them. The test is two-sided
        // because a chip can report a NEGATIVE speed, which used to be
        // published verbatim — flipping TripState.stationary with it, and so
        // the parked-only music marquee. `!in` also rejects NaN, which a
        // one-sided `>` comparison silently admits (issue #351).
        if (speed !in 0.0..MAX_PLAUSIBLE_SPEED_MS) return false
        currentSpeedMs = speed
        speedEstablished = true
        // Count every tracked interval toward the average's time base,
        // including time spent stopped, so AVG is the overall trip average
        // (distance / elapsed incl. stops) rather than a moving-only average.
        // Long gaps are already excluded by the deltaSeconds window above.
        totalSeconds += deltaSeconds
        if (speed >= MIN_MOVING_SPEED_MS) {
            totalMeters += previous.distanceTo(current).toDouble()
        }
        return true
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

    // Write-through on the accrual sequence, throttled so DataStore is rewritten
    // at most once per PERSIST_MIN_INTERVAL_NANOS. DataStore runs its own IO off
    // this dispatcher, so the suspension here is brief.
    private suspend fun maybePersist(fixElapsedRealtimeNanos: Long) {
        val last = lastPersistElapsedNanos
        if (last != null && fixElapsedRealtimeNanos - last < PERSIST_MIN_INTERVAL_NANOS) return
        lastPersistElapsedNanos = fixElapsedRealtimeNanos
        store.write(persisted())
    }

    private fun resetAccumulators() {
        // Drop the anchor too so the trip restarts cleanly from the next fix
        // rather than charging the gap between the reset and that fix.
        lastLocation = null
        totalMeters = 0.0
        totalSeconds = 0.0
        currentSpeedMs = 0.0
        speedEstablished = false
        startedAtEpochMs = null
        // The next reset-to-reset trip begins; the track log keys points on this.
        tripId += 1
    }

    private fun snapshot(): TripState =
        TripState(
            distanceMeters = totalMeters,
            avgSpeedMs = if (totalSeconds > 0.0) totalMeters / totalSeconds else 0.0,
            currentSpeedMs = currentSpeedMs,
            startedAtEpochMs = startedAtEpochMs,
        )

    private fun persisted(): PersistedTrip =
        PersistedTrip(
            totalMeters = totalMeters,
            totalSeconds = totalSeconds,
            startedAtEpochMs = startedAtEpochMs,
            tripId = tripId,
        )

    // Merged input to the single accrual sequence: a GPS location fix or a reset.
    private sealed interface TripSignal {
        data class Fix(
            val location: Location,
        ) : TripSignal

        data object Reset : TripSignal
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0

        // Must be strictly positive; covers same-instant emissions where
        // the boot clock didn't advance.
        const val MIN_DELTA_SECONDS = 0.001

        // Minimum elapsed time before a position-derived speed is trusted.
        // Below this a sub-second inter-fix gap manufactures an impossible
        // speed from a real position delta.
        const val MIN_TRUSTWORTHY_DELTA_SECONDS = 0.5

        // Plausible vehicle-speed ceiling (~396 km/h). Any effective speed
        // above this is GPS noise and is dropped from all three metrics.
        const val MAX_PLAUSIBLE_SPEED_MS = 110.0

        // Throttle for periodic persistence: car power dies without warning
        // (ignition off), so write-through must be periodic — 5 s bounds the
        // loss window while keeping DataStore rewrites rare.
        const val PERSIST_MIN_INTERVAL_NANOS = 5_000_000_000L
    }
}

// Callback for every GPS fix accepted into the trip chain, tagged with the
// reset-to-reset trip id. Runs on the single accrual sequence: implementations
// must hand work off (queue / trySend) rather than block.
internal fun interface TripFixTap {
    fun onFix(
        location: Location,
        tripId: Long,
    )
}

// ~1.8 km/h — below this the device is treated as stationary. Top-level so the
// speed overlay's snap-to-zero shares the same definition of "stationary" as
// the trip math (SSOT for the floor).
internal const val MIN_MOVING_SPEED_MS = 0.5

// Drop fix pairs separated by a longer gap (parked, lost fix, tunnel), and
// treat the same window as "one continuous drive" everywhere. Top-level so the
// GPX exporter's segment split shares the trip math's definition of a gap
// (SSOT for the window).
internal const val MAX_GAP_SECONDS = 60.0
