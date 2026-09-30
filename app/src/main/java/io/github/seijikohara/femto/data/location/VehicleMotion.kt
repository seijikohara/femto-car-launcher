@file:OptIn(ExperimentalCoroutinesApi::class) // transformLatest in vehicleMotionFlow and withParkedDwell.

package io.github.seijikohara.femto.data.location

import android.location.Location
import android.os.SystemClock
import io.github.seijikohara.femto.data.common.catchAsDefault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "VehicleMotion"

/**
 * How long a gate waits for the current verdict ([currentOrUnknown]). A live
 * pipeline answers at once, and a cold one within its first seed; the bound
 * only keeps a stalled location stack from holding a gate forever.
 */
internal const val MOTION_VERDICT_TIMEOUT_MS = 2_000L

/**
 * What the latest fix says about the vehicle's motion. [TripState.stationary]
 * alone cannot tell parked from unknown. The trip speed starts at zero in
 * every process, and the first fix only anchors the trip math. NETWORK fixes
 * never reach the trip math at all, because it is GPS-only ([isGpsFix]). So
 * [PARKED] also needs a live GPS fix: the receiver fixing now, not a cached
 * seed and not a network position. [UNKNOWN] covers every reading that cannot
 * tell. Each gate picks its own failure direction from it: the dock's update
 * badge waits for [PARKED], and the install steps hold only on [MOVING].
 */
internal enum class VehicleMotion {
    /** A live GPS fix, and neither it nor the trip speed says the vehicle moves. */
    PARKED,

    /** The trip speed, or a live GPS fix's own speed, is at or above the parked floor. */
    MOVING,

    /** No reading can tell: no GPS fix yet, a cached fix, or a receiver gone quiet. */
    UNKNOWN,
}

/**
 * The motion that [tripState] and the latest [location] (null before any fix)
 * show at [nowElapsedRealtimeNanos] (pass [SystemClock.elapsedRealtimeNanos]).
 * A NETWORK [location] never reads [VehicleMotion.PARKED]; the trip speed can
 * still read [VehicleMotion.MOVING] beside it.
 *
 * A fix is live within [LOCATION_STALE_THRESHOLD_MS] ([isFresh]). That window
 * is the project's single definition of when a position stops describing the
 * present, and the map's live chevron uses it too. The request interval is
 * 250 ms by default and at most 2 s, so a receiver that is fixing stays well
 * inside it. The cached seed a subscription starts with passes only when
 * another app had the receiver fixing moments ago. The fix's own speed covers
 * the first fix of a process, which only anchors the trip math and so leaves
 * the trip speed at zero.
 */
internal fun vehicleMotion(
    location: Location?,
    tripState: TripState,
    nowElapsedRealtimeNanos: Long,
): VehicleMotion =
    when {
        location == null -> VehicleMotion.UNKNOWN
        !tripState.stationary -> VehicleMotion.MOVING
        !location.isLiveGpsFix(nowElapsedRealtimeNanos) -> VehicleMotion.UNKNOWN
        location.hasSpeed() && location.speed >= MIN_MOVING_SPEED_MS -> VehicleMotion.MOVING
        else -> VehicleMotion.PARKED
    }

/**
 * [vehicleMotion], judged each time a new reading (a fix or a trip update)
 * arrives, and emitted only on change. The location flow interleaves NETWORK
 * fixes with GPS ones. Once a GPS fix has arrived, a NETWORK fix neither
 * replaces it nor counts as a new reading, so it cannot flip a parked verdict
 * to unknown and back. Before the first GPS fix, each fix is judged as it
 * comes, so the trip speed can still read [VehicleMotion.MOVING]. The shared
 * location flow replays its latest fix, NETWORK ones included, and every gate
 * reads a fresh collection of it. A gate that starts reading on a NETWORK fix
 * while the car pulls away, or drives through a tunnel with cellular
 * coverage, must still hold. A device with network fixes only reads
 * [VehicleMotion.UNKNOWN], so a gate waiting on its first verdict never waits.
 *
 * A receiver gone quiet sends nothing to judge, so [VehicleMotion.PARKED]
 * ages out on its own: [LOCATION_STALE_THRESHOLD_MS] after it was judged with
 * no new reading, it becomes [VehicleMotion.UNKNOWN], as the fix it rests on
 * stops being live. [VehicleMotion.MOVING] never ages out. A car that drives
 * into a tunnel keeps the verdict of its last fix, so the install steps stay
 * held until a fix shows it stopped.
 */
internal fun vehicleMotionFlow(
    locationFlow: Flow<Location?>,
    tripStateFlow: Flow<TripState>,
    nowElapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
): Flow<VehicleMotion> =
    combine(locationFlow.latestGpsFix(), tripStateFlow) { location, tripState ->
        vehicleMotion(location, tripState, nowElapsedRealtimeNanos())
    }
        // Before the dedup below: every reading must restart the ageing, a
        // repeated verdict included.
        .transformLatest { motion ->
            emit(motion)
            if (motion == VehicleMotion.PARKED) {
                delay(LOCATION_STALE_THRESHOLD_MS)
                emit(VehicleMotion.UNKNOWN)
            }
        }.distinctUntilChanged()

/**
 * A motion verdict, and whether it has read [VehicleMotion.PARKED] without a
 * break for the dwell [withParkedDwell] was asked for.
 */
internal data class MotionDwell(
    val motion: VehicleMotion,
    val parkedThroughDwell: Boolean,
)

/**
 * Each verdict of this flow as it comes, and once it has read
 * [VehicleMotion.PARKED] for [dwellMs] without a break, the same verdict marked
 * as having dwelled. Any other verdict, a single MOVING or UNKNOWN reading,
 * starts the count over; a verdict flow emits on change ([vehicleMotionFlow]),
 * so a steady PARKED keeps counting. Measured on the coroutine clock, the one
 * [vehicleMotionFlow] ages a parked verdict with: monotonic on the device and
 * virtual in tests, never the wall clock, which an AI box can boot with wrong
 * until NTP sets it.
 */
internal fun Flow<VehicleMotion>.withParkedDwell(dwellMs: Long): Flow<MotionDwell> =
    transformLatest { motion ->
        emit(MotionDwell(motion, parkedThroughDwell = false))
        if (motion == VehicleMotion.PARKED) {
            delay(dwellMs)
            emit(MotionDwell(motion, parkedThroughDwell = true))
        }
    }

/**
 * The verdict this flow gives now, or [VehicleMotion.UNKNOWN] when it gives
 * none within [MOTION_VERDICT_TIMEOUT_MS] or fails. Every gate on the install
 * confirmation reads motion through this one function, so none of them can
 * judge the same moment differently. They hold only on [VehicleMotion.MOVING],
 * so a silent or broken location stack leaves them open, the same as a phone
 * without the location grant.
 */
internal suspend fun Flow<VehicleMotion>.currentOrUnknown(): VehicleMotion =
    withTimeoutOrNull(MOTION_VERDICT_TIMEOUT_MS) {
        catchAsDefault(TAG, "vehicle motion", VehicleMotion.UNKNOWN).firstOrNull()
    } ?: VehicleMotion.UNKNOWN

// The latest GPS fix once there is one. Until then, each element passes as it
// is (a NETWORK fix, or the null no-fix signal): the first upstream element
// always yields a value, so the combine above can speak, and a moving trip
// speed is not silenced by a missing GPS fix. After the first GPS fix, an
// element that leaves it in place (a NETWORK fix) is dropped, since it is no
// new reading.
private fun Flow<Location?>.latestGpsFix(): Flow<Location?> =
    runningFold<Location?, Location?>(null) { latest, fix ->
        when {
            fix?.isGpsFix() == true -> fix
            latest?.isGpsFix() == true -> latest
            else -> fix
        }
    }.drop(1)
        .distinctUntilChanged { old, new -> old === new }

private fun Location.isLiveGpsFix(nowElapsedRealtimeNanos: Long): Boolean =
    isGpsFix() && isFresh(nowElapsedRealtimeNanos)
