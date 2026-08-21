package io.github.seijikohara.femto.data.location

import android.location.Location
import kotlin.math.abs

/**
 * Whether a raw platform fix is usable by the launcher's consumers.
 *
 * The platform hands `Location` through verbatim: `setLatitude` and
 * `setElapsedRealtimeNanos` validate nothing, and an aftermarket head unit's
 * HAL — or a mock-location app, which AI-box owners run routinely — is free to
 * fill either field with anything. Two rules cover what has actually been
 * observed (issue #351):
 *
 *  - **Coordinates must be real.** A NaN or out-of-range coordinate reaches
 *    the map bridge as a camera target, throwing the marker off the viewport
 *    until the next fix lands.
 *  - **The boot clock must not regress.** `elapsedRealtimeNanos` is monotonic
 *    for the life of a process, so a fix behind the newest one already
 *    forwarded is a *replay* of an older cached fix, not new information — the
 *    location flow re-seeds `getLastKnownLocation` on every re-subscribe, and
 *    the NETWORK cache is routinely far older than the GPS one. Equal
 *    timestamps pass: a HAL that zeroes them would otherwise deliver exactly
 *    one fix and then go silent.
 *
 * Accuracy is deliberately *not* a gate. Network fixes jump tens to hundreds
 * of metres and are still wanted for map centring (see the [TripRepository]
 * class KDoc, which filters them out of trip accrual only), and a floor would
 * blank the map during cold GNSS acquisition on a unit that reports nothing
 * better.
 *
 * Pure so the policy is JVM-unit-testable apart from the platform listener.
 */
internal fun isUsableFix(
    fix: Location,
    lastAcceptedElapsedNanos: Long?,
): Boolean =
    fix.hasRealCoordinates() &&
        (lastAcceptedElapsedNanos == null || fix.elapsedRealtimeNanos >= lastAcceptedElapsedNanos)

/**
 * Whether this fix's timestamp may become the recency baseline
 * [isUsableFix] compares against, judged at [nowElapsedRealtimeNanos] (pass
 * [android.os.SystemClock.elapsedRealtimeNanos]).
 *
 * The baseline only ever ratchets forward and outlives every collector, so a
 * single fix stamped from the wrong clock — epoch nanos rather than boot
 * nanos, the classic mock-provider bug — would pin it above every genuine fix
 * and blank the whole location stack until the process restarts. A fix cannot
 * have been measured in the future, so a stamp past *now* is exactly that
 * mistake: forward the fix (a bad clock says nothing about the position) but
 * refuse it the baseline. Anchoring on the system clock rather than on the
 * HAL's own word is what [TripRepository] and [isFresh] already do.
 */
internal fun canAnchorRecency(
    fix: Location,
    nowElapsedRealtimeNanos: Long,
): Boolean = fix.elapsedRealtimeNanos in 0..nowElapsedRealtimeNanos

// WGS84 bounds. Mirrored in webmap (camera.ts, MAX_LATITUDE_DEG /
// MAX_LONGITUDE_DEG) as defence in depth on the camera target; this gate is the
// primary one.
private const val MAX_LATITUDE_DEG = 90.0
private const val MAX_LONGITUDE_DEG = 180.0

private fun Location.hasRealCoordinates(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        abs(latitude) <= MAX_LATITUDE_DEG &&
        abs(longitude) <= MAX_LONGITUDE_DEG
