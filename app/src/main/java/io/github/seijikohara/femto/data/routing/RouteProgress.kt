package io.github.seijikohara.femto.data.routing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** The mean earth radius; the projection below only needs the scale of a metre per degree. */
private const val EARTH_RADIUS_METERS = 6_371_008.8

private const val METERS_PER_DEGREE = EARTH_RADIUS_METERS * PI / 180.0

/** What is left of a route from one position on it. */
internal data class RouteProgress(
    val remainingMeters: Double,
    /** The route's own estimate scaled by the share of the distance left; it knows no traffic. */
    val remainingSeconds: Double,
)

/** The whole route still ahead, as at its start. */
internal fun Route.fullProgress(): RouteProgress = RouteProgress(distanceMeters.toDouble(), durationSeconds.toDouble())

/**
 * What is left of the route from [position]: the position is projected onto the nearest point of the line, and the
 * route's distance and duration are scaled by the share of the line ahead of that point. Scaling BRouter's distance,
 * rather than measuring the line, makes the readout start at the distance the route reported. A fix beside the road
 * counts where it projects; nothing here notices a fix that has left the route (phase 4 of #389 does).
 */
internal fun Route.progressAt(position: RoutePoint): RouteProgress =
    remainingShare(points, position).let { share ->
        RouteProgress(remainingMeters = distanceMeters * share, remainingSeconds = durationSeconds * share)
    }

/**
 * The share of [line]'s length beyond the point of [line] nearest [position], from 1 at the start to 0 at the end.
 *
 * The line is flattened with an equirectangular projection centred on [position]: cheap, and its error over the
 * distances BRouter routes on the device (about 150 km at most) is a small fraction that the ratio cancels, since the
 * travelled and the total length are measured in the same plane.
 */
internal fun remainingShare(
    line: List<RoutePoint>,
    position: RoutePoint,
): Double {
    val scaleX = cos(position.latitude * PI / 180.0) * METERS_PER_DEGREE
    return line
        .map { point ->
            FlatPoint(
                x = longitudeDelta(point.longitude, position.longitude) * scaleX,
                y = (point.latitude - position.latitude) * METERS_PER_DEGREE,
            )
        }.zipWithNext()
        .fold(LineWalk()) { walk, (start, end) -> walk.along(start, end) }
        .let { walk ->
            if (walk.length > 0.0) ((walk.length - walk.travelledAtNearest) / walk.length).coerceIn(0.0, 1.0) else 0.0
        }
}

private data class FlatPoint(
    val x: Double,
    val y: Double,
)

// The walk along the line so far: its length, and the nearest point to the position (the origin) found on it.
private data class LineWalk(
    val length: Double = 0.0,
    val nearestSquared: Double = Double.POSITIVE_INFINITY,
    val travelledAtNearest: Double = 0.0,
)

private fun LineWalk.along(
    start: FlatPoint,
    end: FlatPoint,
): LineWalk {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    // The position is the origin, so the projection's parameter is the start's offset along the segment, negated.
    val t = if (lengthSquared > 0.0) (-(start.x * dx + start.y * dy) / lengthSquared).coerceIn(0.0, 1.0) else 0.0
    val px = start.x + t * dx
    val py = start.y + t * dy
    val segment = sqrt(lengthSquared)
    // Strictly nearer: on a tie, as where a route doubles back on itself, the earlier pass wins.
    val nearer = px * px + py * py < nearestSquared
    return LineWalk(
        length = length + segment,
        nearestSquared = if (nearer) px * px + py * py else nearestSquared,
        travelledAtNearest = if (nearer) length + t * segment else travelledAtNearest,
    )
}

// The longitude difference folded into -180..180, so a line across the antimeridian stays short.
private fun longitudeDelta(
    longitude: Double,
    origin: Double,
): Double = ((longitude - origin) % 360.0 + 540.0) % 360.0 - 180.0
