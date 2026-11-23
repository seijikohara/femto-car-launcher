package io.github.seijikohara.femto.data.routing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

/** The mean earth radius; the projection below only needs the scale of a metre per degree. */
private const val EARTH_RADIUS_METERS = 6_371_008.8

private const val METERS_PER_DEGREE = EARTH_RADIUS_METERS * PI / 180.0

// A fix this close to the line counts as on it: GPS error and the road's width
// beside BRouter's centre line, with room to spare. A route that comes back
// along the same road leaves its first pass by more than this before it turns,
// so the two passes are separate stretches of line near the fix.
private const val ON_LINE_METERS = 50.0

private const val ON_LINE_SQUARED = ON_LINE_METERS * ON_LINE_METERS

// GPS noise can put a fix a little behind the last one; the search ahead starts
// this far back, which lets the readout dip that much without opening the way
// back to an earlier pass of the same road.
private const val BACKTRACK_METERS = 30.0

// Two points of the line whose distances from a fix differ by less than this are equally near it. A route out and
// back along a road with one carriageway repeats the same line, so a fix off that road is as far from one pass as
// from the other, give or take the arithmetic.
private const val SAME_DISTANCE_METERS = 1.0

/** What is left of a route from one position on it. */
internal data class RouteProgress(
    val remainingMeters: Double,
    /** The route's own estimate scaled by the share of the distance left; it knows no traffic. */
    val remainingSeconds: Double,
)

/**
 * Where a fix lies along a route's line: the length of line behind it, in [RouteLine]'s plane. The next fix's search
 * starts here.
 */
@JvmInline
internal value class LinePosition(
    val travelledMeters: Double,
) {
    companion object {
        /** The line's start, where a route begins before any fix has been placed on it. */
        val Start = LinePosition(0.0)
    }
}

/**
 * A route's line, flattened once so that following it costs one pass over its segments per fix.
 *
 * The flattening is an equirectangular projection centred on the line: cheap, and its error over the distances BRouter
 * routes on the device (about 150 km at most) is a small fraction that the share of the line left cancels, since the
 * travelled and the total length are measured in the same plane. Longitudes are taken the short way round from the
 * line's first point, so a line across the antimeridian stays short.
 */
internal class RouteLine(
    route: Route,
) {
    private val distanceMeters = route.distanceMeters.toDouble()
    private val durationSeconds = route.durationSeconds.toDouble()
    private val originLongitude = route.points.firstOrNull()?.longitude ?: 0.0
    private val originLatitude =
        route.points.takeIf { it.isNotEmpty() }?.let { points ->
            (points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2.0
        } ?: 0.0
    private val scaleX = cos(originLatitude * PI / 180.0) * METERS_PER_DEGREE
    private val xs = route.points.map { longitudeDelta(it.longitude, originLongitude) * scaleX }.toDoubleArray()
    private val ys = route.points.map { (it.latitude - originLatitude) * METERS_PER_DEGREE }.toDoubleArray()

    // The length of line from its start to each point.
    private val travelledAt =
        (1 until xs.size)
            .runningFold(0.0) { travelled, i -> travelled + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) }
            .toDoubleArray()

    private val length = travelledAt.lastOrNull() ?: 0.0

    /**
     * Return where [position] lies along the line, searching on from [previous] (where the last fix lay): the nearest
     * point of the first stretch of line ahead that passes within [ON_LINE_METERS] of the fix. Where the route comes
     * back along the same road, the pass the vehicle is on comes first, so a fix that drifts toward the other pass stays
     * on its own. The search starts [BACKTRACK_METERS] behind [previous], for GPS noise. A fix no stretch ahead comes
     * near — a detour, a gap in the fixes, a vehicle that turned back — goes to the nearest point of the whole line
     * ([nearestOverall]).
     */
    fun follow(
        position: RoutePoint,
        previous: LinePosition,
    ): LinePosition {
        val x = xOf(position)
        val y = yOf(position)
        val from = (previous.travelledMeters - BACKTRACK_METERS).coerceAtLeast(0.0)
        return segments()
            .filter { travelledAt[it + 1] >= from }
            .map { nearestOn(it, x, y, from) }
            .dropWhile { it.distanceSquared > ON_LINE_SQUARED }
            .takeWhile { it.distanceSquared <= ON_LINE_SQUARED }
            .minByOrNull { it.distanceSquared }
            ?.let { LinePosition(it.travelled) }
            ?: nearestOverall(x, y, previous)
    }

    // The point of the whole line nearest (x, y). Where passes are equally near ([SAME_DISTANCE_METERS]), the one
    // nearest [previous] along the line wins: after a detour on the way back along the road the route went out on,
    // the nearest point alone cannot tell the passes apart, and the way out, once taken, would hold every later
    // fix inside the search's backtrack, each a step further back along it.
    private fun nearestOverall(
        x: Double,
        y: Double,
        previous: LinePosition,
    ): LinePosition =
        segments()
            .map { nearestOn(it, x, y, from = 0.0) }
            .toList()
            .let { matches ->
                matches.minOfOrNull { it.distanceSquared }?.let { nearestSquared ->
                    matches
                        .filter { sqrt(it.distanceSquared) <= sqrt(nearestSquared) + SAME_DISTANCE_METERS }
                        .minByOrNull { abs(it.travelled - previous.travelledMeters) }
                }
            }?.let { LinePosition(it.travelled) }
            ?: LinePosition.Start

    /**
     * Return what is left of the route from [at]: the route's distance and duration scaled by the share of the line
     * ahead. Scaling BRouter's distance, rather than measuring the line, makes the readout start at the distance the
     * route reported. A line without length leaves nothing.
     */
    fun progressAt(at: LinePosition): RouteProgress =
        (if (length > 0.0) ((length - at.travelledMeters) / length).coerceIn(0.0, 1.0) else 0.0).let { share ->
            RouteProgress(remainingMeters = distanceMeters * share, remainingSeconds = durationSeconds * share)
        }

    /**
     * Return the distance from [position] to the nearest point of the whole line, in the line's plane. A line without
     * a segment has nowhere to leave, so every position is on it.
     */
    fun offLineMeters(position: RoutePoint): Double {
        val x = xOf(position)
        val y = yOf(position)
        return segments().minOfOrNull { nearestOn(it, x, y, from = 0.0).distanceSquared }?.let(::sqrt) ?: 0.0
    }

    private fun xOf(position: RoutePoint): Double = longitudeDelta(position.longitude, originLongitude) * scaleX

    private fun yOf(position: RoutePoint): Double = (position.latitude - originLatitude) * METERS_PER_DEGREE

    private fun segments(): Sequence<Int> = (0 until xs.size - 1).asSequence()

    // The point of segment [i] nearest (x, y), kept at or past [from] along the line.
    private fun nearestOn(
        i: Int,
        x: Double,
        y: Double,
        from: Double,
    ): LineMatch {
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        val segment = travelledAt[i + 1] - travelledAt[i]
        val lengthSquared = dx * dx + dy * dy
        val earliest = if (segment > 0.0) ((from - travelledAt[i]) / segment).coerceIn(0.0, 1.0) else 0.0
        val t =
            if (lengthSquared > 0.0) {
                (((x - xs[i]) * dx + (y - ys[i]) * dy) / lengthSquared).coerceIn(earliest, 1.0)
            } else {
                0.0
            }
        val offsetX = xs[i] + t * dx - x
        val offsetY = ys[i] + t * dy - y
        return LineMatch(
            distanceSquared = offsetX * offsetX + offsetY * offsetY,
            travelled = travelledAt[i] + t * segment,
        )
    }
}

/** What [followFlow] reports at a fix: what is left of the route, and what the off-route watch noticed there. */
internal data class RouteFollow(
    val progress: RouteProgress,
    val event: FollowEvent?,
)

/**
 * What is left of this route from each fix in [fixes], each placed on the line from where the last one lay
 * ([RouteLine.follow]), and whether the vehicle has left the route ([OffRouteWatch]); the whole route until the first
 * fix. Emits when the progress changes or the watch raises an event. The line is flattened once, when the flow is
 * collected, and each fix costs one more pass over it, for its distance from the whole line.
 */
internal fun Route.followFlow(fixes: Flow<RouteFix?>): Flow<RouteFollow> =
    flow {
        val line = RouteLine(this@followFlow)
        emitAll(
            fixes
                .filterNotNull()
                .runningFold(FollowStep(LinePosition.Start, line.progressAt(LinePosition.Start), OffRouteWatch())) {
                    step,
                    fix,
                    ->
                    val position = line.follow(fix.point, step.position)
                    val progress = line.progressAt(position)
                    FollowStep(
                        position = position,
                        progress = progress,
                        watch = step.watch.advance(fix, line.offLineMeters(fix.point), progress.remainingMeters),
                    )
                }.map { step -> RouteFollow(step.progress, step.watch.event) }
                .distinctUntilChanged(),
        )
    }

// The follow's state between fixes: where the last fix lay on the line, what was left from there, and the watch.
private data class FollowStep(
    val position: LinePosition,
    val progress: RouteProgress,
    val watch: OffRouteWatch,
)

// A segment's point nearest a fix: the squared distance to it, and the length of line behind it.
private data class LineMatch(
    val distanceSquared: Double,
    val travelled: Double,
)

// The longitude difference folded into -180..180, so a line across the antimeridian stays short.
private fun longitudeDelta(
    longitude: Double,
    origin: Double,
): Double = ((longitude - origin) % 360.0 + 540.0) % 360.0 - 180.0
