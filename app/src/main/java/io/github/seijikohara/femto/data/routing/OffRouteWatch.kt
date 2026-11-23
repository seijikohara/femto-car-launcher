package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.data.location.LOCATION_STALE_THRESHOLD_MS
import io.github.seijikohara.femto.data.location.MIN_MOVING_SPEED_MS
import kotlin.math.max

/**
 * How far from the line a fix may lie and still count as on the route: GPS error and the width of a large road beside
 * BRouter's centre line. A poor fix widens it ([offRouteThresholdMeters]).
 */
internal const val OFF_ROUTE_FLOOR_METERS = 50.0

/** How long a moving vehicle stays off the line before it counts as having left the route. */
internal const val OFF_ROUTE_HOLD_MS = 5_000L

/**
 * How long a vehicle that left the route stays within [OFF_ROUTE_FLOOR_METERS] of the line before it counts as back on
 * it. Being back cancels a running reroute, so a single fix that strays onto the line must not.
 */
internal const val OFF_ROUTE_BACK_HOLD_MS = 2_000L

/**
 * How long a vehicle moves without a stop before the watch arms without ever having been on the line. The line starts
 * at the road BRouter matched the start to, which can lie a car park's width from where the route was set.
 */
internal const val OFF_ROUTE_ARM_MOVING_MS = 30_000L

/**
 * The distance left below which the watch stays quiet. The line ends at the road BRouter matched the destination to,
 * not at the point the driver chose, so the last stretch to that point leaves the line on purpose.
 */
internal const val OFF_ROUTE_QUIET_NEAR_END_METERS = 300.0

/** One fix as the route follow reads it; each reading is null when the fix does not carry it. */
internal data class RouteFix(
    val point: RoutePoint,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    /** Degrees clockwise from north. */
    val bearingDegrees: Float?,
    /** When the fix was taken, in milliseconds on the boot clock (`elapsedRealtimeNanos / 1_000_000`). */
    val elapsedMs: Long,
)

/** Whether the fix's own speed says the vehicle moves; a fix without a speed never does. */
internal val RouteFix.moving: Boolean
    get() = speedMetersPerSecond?.let { it >= MIN_MOVING_SPEED_MS } == true

/** What the route follow noticed at one fix. */
internal sealed interface FollowEvent {
    /** The vehicle has left the route; [fix] is where it was when the watch decided so. */
    data class LeftRoute(
        val fix: RouteFix,
    ) : FollowEvent

    /** The vehicle is back on the route after a [LeftRoute]. */
    data object BackOnRoute : FollowEvent
}

/**
 * Whether the vehicle has left the route, judged one fix at a time ([advance]). Immutable: each fix makes the next
 * watch, and [event] is what that fix raised.
 *
 * Only fixes with a speed are judged. The location stack interleaves the network provider's fixes, which carry no
 * speed and a wide accuracy, with the GPS ones; judged, a single one would reset the hold or count as back on the
 * line. A vehicle counts as off the route when it moves and its fixes stay farther from the line than the threshold
 * for [OFF_ROUTE_HOLD_MS]. The watch arms only once a fix has been within [OFF_ROUTE_FLOOR_METERS] of the line, however
 * accurate, or after [OFF_ROUTE_ARM_MOVING_MS] of moving, and stays quiet within [OFF_ROUTE_QUIET_NEAR_END_METERS] of
 * the end. A stop restarts the hold, and so does a
 * gap of more than [LOCATION_STALE_THRESHOLD_MS] between fixes, as after a tunnel or a replayed old fix. Still off the
 * route after [FollowEvent.LeftRoute], it raises the event again after another hold. After it, the vehicle is back on
 * the route once consecutive fixes have stayed within [OFF_ROUTE_FLOOR_METERS] of the line for
 * [OFF_ROUTE_BACK_HOLD_MS]; the widened threshold does not count here either.
 */
internal data class OffRouteWatch(
    val armed: Boolean = false,
    /** When the last judged fix was taken, on the boot clock; null before the first. */
    val lastFixMs: Long? = null,
    /** When the vehicle started moving without a stop, or null while it stands. */
    val movingSinceMs: Long? = null,
    /** When the vehicle went off the line while moving, or null while it is on the line, stands, or nears the end. */
    val offSinceMs: Long? = null,
    /** Whether [FollowEvent.LeftRoute] has been raised and [FollowEvent.BackOnRoute] has not been since. */
    val left: Boolean = false,
    /** When the vehicle, having left, came back within [OFF_ROUTE_FLOOR_METERS] of the line, or null. */
    val backSinceMs: Long? = null,
    /** The event the fix that made this watch raised, or null. */
    val event: FollowEvent? = null,
)

/** The threshold for a fix [accuracyMeters] from the truth: [OFF_ROUTE_FLOOR_METERS], or twice the accuracy. */
internal fun offRouteThresholdMeters(accuracyMeters: Float?): Double =
    max(OFF_ROUTE_FLOOR_METERS, 2.0 * (accuracyMeters ?: 0f))

/**
 * Judge [fix], which lies [offLineMeters] from the nearest point of the line, with [remainingMeters] of the route left
 * from where it is placed on the line.
 */
internal fun OffRouteWatch.advance(
    fix: RouteFix,
    offLineMeters: Double,
    remainingMeters: Double,
): OffRouteWatch {
    val gapMs = lastFixMs?.let { fix.elapsedMs - it }
    if (fix.speedMetersPerSecond == null || (gapMs != null && gapMs < 0)) return copy(event = null)
    val continuous = gapMs != null && gapMs <= LOCATION_STALE_THRESHOLD_MS
    // The widened threshold keeps a poor fix from counting as having left; it must not let one count as being on the
    // line, which arms the watch and, after leaving, brings the vehicle back.
    val onFloor = offLineMeters <= OFF_ROUTE_FLOOR_METERS
    val onLine = offLineMeters <= offRouteThresholdMeters(fix.accuracyMeters)
    val movingSince = movingSinceMs?.takeIf { continuous } ?: fix.elapsedMs
    val nowMoving = fix.moving
    val nowArmed = armed || onFloor || (nowMoving && fix.elapsedMs - movingSince >= OFF_ROUTE_ARM_MOVING_MS)
    val offSince =
        (offSinceMs?.takeIf { continuous } ?: fix.elapsedMs)
            .takeIf { !onLine && nowMoving && remainingMeters > OFF_ROUTE_QUIET_NEAR_END_METERS }
    val backSince = (backSinceMs?.takeIf { continuous } ?: fix.elapsedMs).takeIf { left && onFloor }
    val next =
        OffRouteWatch(
            armed = nowArmed,
            lastFixMs = fix.elapsedMs,
            movingSinceMs = movingSince.takeIf { nowMoving },
            offSinceMs = offSince,
            left = left,
            backSinceMs = backSince,
        )
    return when {
        nowArmed && offSince != null && fix.elapsedMs - offSince >= OFF_ROUTE_HOLD_MS -> {
            next.copy(offSinceMs = fix.elapsedMs, left = true, backSinceMs = null, event = FollowEvent.LeftRoute(fix))
        }

        backSince != null && fix.elapsedMs - backSince >= OFF_ROUTE_BACK_HOLD_MS -> {
            next.copy(left = false, backSinceMs = null, event = FollowEvent.BackOnRoute)
        }

        else -> {
            next
        }
    }
}
