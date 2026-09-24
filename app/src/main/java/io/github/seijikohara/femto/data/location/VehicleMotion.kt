package io.github.seijikohara.femto.data.location

import android.location.Location

/**
 * What the latest fix says about the vehicle's motion. [TripState.stationary]
 * alone cannot tell: the speed behind it starts at zero and is not persisted,
 * so it reads "parked" until the first fix of a drive lands. [UNKNOWN] keeps
 * that case apart, and each gate picks its own failure direction from it: a
 * gate that must know the vehicle is parked waits for [PARKED], and a gate
 * that must only stay out of a moving vehicle's way holds off on [MOVING]
 * alone.
 */
internal enum class VehicleMotion {
    /** A fix is in, and the speed is below the parked floor ([TripState.stationary]). */
    PARKED,

    /** A fix is in, and the speed is at or above the parked floor. */
    MOVING,

    /** No fix has arrived, so the speed says nothing yet. */
    UNKNOWN,
}

/** The motion [tripState] shows, given the location flow's latest [location] (null before any fix). */
internal fun vehicleMotion(
    location: Location?,
    tripState: TripState,
): VehicleMotion =
    when {
        location == null -> VehicleMotion.UNKNOWN
        tripState.stationary -> VehicleMotion.PARKED
        else -> VehicleMotion.MOVING
    }
