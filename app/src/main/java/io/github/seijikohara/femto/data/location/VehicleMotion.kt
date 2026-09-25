package io.github.seijikohara.femto.data.location

import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What the latest fix says about the vehicle's motion. [TripState.stationary]
 * alone cannot tell parked from unknown. The trip speed starts at zero in
 * every process, and the first fix only anchors the trip math. NETWORK fixes
 * never reach the trip math at all, because it is GPS-only. So [PARKED] also
 * needs a live GPS fix: the receiver fixing now, not a cached seed and not a
 * network position. [UNKNOWN] covers every reading that cannot tell. Each
 * gate picks its own failure direction from it: the dock's update badge waits
 * for [PARKED], and the install steps hold only on [MOVING].
 */
internal enum class VehicleMotion {
    /** A live GPS fix, and neither it nor the trip speed says the vehicle moves. */
    PARKED,

    /** The trip speed, or a live GPS fix's own speed, is at or above the parked floor. */
    MOVING,

    /** No reading can tell: no fix yet, a cached or network fix, or a receiver gone quiet. */
    UNKNOWN,
}

/**
 * The motion that [tripState] and the location flow's latest [location] (null
 * before any fix) show at [nowElapsedRealtimeNanos] (pass
 * [SystemClock.elapsedRealtimeNanos]).
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
 * [vehicleMotion], evaluated each time a fix or a trip update arrives, and
 * emitted only on change. The clock is read at those moments and no others, so
 * a verdict holds until the next reading instead of decaying on a timer. A
 * cached seed is judged on arrival, when it is as old as it will ever be. A
 * receiver that is fixing is judged again on every fix.
 */
internal fun vehicleMotionFlow(
    locationFlow: Flow<Location?>,
    tripStateFlow: Flow<TripState>,
    nowElapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
): Flow<VehicleMotion> =
    combine(locationFlow, tripStateFlow) { location, tripState ->
        vehicleMotion(location, tripState, nowElapsedRealtimeNanos())
    }.distinctUntilChanged()

private fun Location.isLiveGpsFix(nowElapsedRealtimeNanos: Long): Boolean =
    provider == LocationManager.GPS_PROVIDER && isFresh(nowElapsedRealtimeNanos)
