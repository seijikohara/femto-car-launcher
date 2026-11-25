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
import kotlin.math.max
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

// The vehicle is drawn on the line while its fix lies within DRAWN_ON_LINE_METERS of the point of the line it was
// matched to, or within the fix's own accuracy where that is wider, and never farther than DRAWN_ON_LINE_MAX_METERS.
// Close to the line, the line is the road the vehicle is on and the GPS error is the only gap; a good fix farther out
// is on another road, such as one beside the route, and shows the vehicle there. Both are a start, to tune on the road.
private const val DRAWN_ON_LINE_METERS = 20.0
private const val DRAWN_ON_LINE_MAX_METERS = 50.0

// Once drawn on the line, the vehicle stays there until its fix lies this much farther out than the gate it joins by,
// still never beyond DRAWN_ON_LINE_MAX_METERS: a fix at the gate's edge would otherwise throw it 20 to 50 m sideways
// and back every second. A start, to tune on the road.
private const val LEAVE_LINE_MARGIN_METERS = 10.0

// A turn stays ahead, at no distance, until the vehicle is this far past its point along the line: on reaching the
// point the vehicle is still in the corner. A start, to tune on the road.
private const val TURN_PASSED_METERS = 15.0

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

    /**
     * Return the point of the line at [at], back in degrees: the projection undone, with the longitude folded back
     * into -180..180 where the line crosses the antimeridian.
     */
    fun pointAt(at: LinePosition): RoutePoint =
        planePointAt(at).let { point ->
            RoutePoint(
                latitude = originLatitude + point.y / METERS_PER_DEGREE,
                longitude = longitudeDelta(originLongitude + point.x / scaleX, origin = 0.0),
            )
        }

    /** Return how far [position] lies from the point of the line at [at], in the line's plane. */
    fun distanceMeters(
        at: LinePosition,
        position: RoutePoint,
    ): Double = planePointAt(at).let { point -> hypot(xOf(position) - point.x, yOf(position) - point.y) }

    /** Return the length of line from its start to point [index] of the route. */
    fun travelledAt(index: Int): LinePosition = LinePosition(travelledAt[index])

    private fun xOf(position: RoutePoint): Double = longitudeDelta(position.longitude, originLongitude) * scaleX

    private fun yOf(position: RoutePoint): Double = (position.latitude - originLatitude) * METERS_PER_DEGREE

    private fun segments(): Sequence<Int> = (0 until xs.size - 1).asSequence()

    // The point of the line at [at] in the plane, on the segment whose span of travelled length holds it; a position
    // past either end is that end. A line of one point is that point, and one of none the plane's origin.
    private fun planePointAt(at: LinePosition): PlanePoint =
        at.travelledMeters.coerceIn(0.0, length).let { travelled ->
            segments().firstOrNull { travelledAt[it + 1] >= travelled }?.let { i ->
                val segment = travelledAt[i + 1] - travelledAt[i]
                val t = if (segment > 0.0) (travelled - travelledAt[i]) / segment else 0.0
                PlanePoint(x = xs[i] + t * (xs[i + 1] - xs[i]), y = ys[i] + t * (ys[i + 1] - ys[i]))
            } ?: PlanePoint(x = xs.firstOrNull() ?: 0.0, y = ys.firstOrNull() ?: 0.0)
        }

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

/** A turn ahead on the route and how far along the line it is from the vehicle. */
internal data class UpcomingTurn(
    val turn: RouteTurn,
    /** In the route's own distance, as [RouteProgress.remainingMeters] counts it; 0 while the vehicle is at the turn. */
    val distanceMeters: Double,
)

/**
 * The point of the line the map draws the vehicle at, and when the fix it was matched from was taken. Fixes in a row
 * can match the same point, past the line's end or on the outside of a corner; the stamp makes each one a new value,
 * so the map hears from every fix and never takes a vehicle at rest on the line for a lost signal.
 */
internal data class OnLinePoint(
    val point: RoutePoint,
    /** [RouteFix.elapsedMs] of the fix that was matched to [point]. */
    val fixElapsedMs: Long,
)

/**
 * What [followFlow] reports at a fix: what is left of the route, what the off-route watch noticed there, where the map
 * draws the vehicle, and the turns ahead.
 */
internal data class RouteFollow(
    val progress: RouteProgress,
    val event: FollowEvent?,
    /**
     * The point of the line the last fix was matched to, with that fix's stamp, while the fix is close enough to it to
     * be drawn there; else null. A vehicle already drawn on the line stays there a little farther out than it joins.
     */
    val onLine: OnLinePoint? = null,
    /**
     * Every turn from the vehicle on, nearest first; null when the route has no turn data. A turn the vehicle has
     * passed stays passed while GPS noise moves the fix a little back, and comes back only after a real jump back.
     */
    val upcomingTurns: List<UpcomingTurn>? = null,
    /** Whether the vehicle has left the route and is not back on it yet ([OffRouteWatch.left]). */
    val offRoute: Boolean = false,
)

/**
 * What is left of this route from each fix in [fixes], each placed on the line from where the last one lay
 * ([RouteLine.follow]), whether the vehicle has left the route ([OffRouteWatch]), the point of the line it is drawn at
 * while the fix is close to it, and the turns ahead; the whole route, and every turn, until the first fix. Emits when
 * any of it changes, which a fix drawn on the line always does ([OnLinePoint] carries its stamp). The line is flattened
 * once, when the flow is collected, and each fix costs a few more passes over it: for its distance from the whole line,
 * and for the point it was matched to.
 */
internal fun Route.followFlow(fixes: Flow<RouteFix?>): Flow<RouteFollow> =
    flow {
        val line = RouteLine(this@followFlow)
        // Where along the line each turn lies, worked out once with the line.
        val turnsAlong = turns?.map { turn -> TurnAlong(turn, line.travelledAt(turn.pointIndex)) }
        emitAll(
            fixes
                .filterNotNull()
                .runningFold(
                    FollowStep(
                        position = LinePosition.Start,
                        progress = line.progressAt(LinePosition.Start),
                        watch = OffRouteWatch(),
                        onLine = null,
                        passed = LinePosition.Start,
                    ),
                ) { step, fix ->
                    val position = line.follow(fix.point, step.position)
                    val progress = line.progressAt(position)
                    FollowStep(
                        position = position,
                        progress = progress,
                        watch = step.watch.advance(fix, line.offLineMeters(fix.point), progress.remainingMeters),
                        onLine =
                            OnLinePoint(line.pointAt(position), fixElapsedMs = fix.elapsedMs)
                                .takeIf {
                                    line.distanceMeters(position, fix.point) <=
                                        drawnOnLineMeters(fix, wasOnLine = step.onLine != null)
                                },
                        passed =
                            if (position.travelledMeters < step.passed.travelledMeters - BACKTRACK_METERS) {
                                position
                            } else {
                                LinePosition(max(step.passed.travelledMeters, position.travelledMeters))
                            },
                    )
                }.map { step ->
                    RouteFollow(
                        progress = step.progress,
                        event = step.watch.event,
                        onLine = step.onLine,
                        upcomingTurns = turnsAlong?.let { line.turnsAhead(it, step.passed, step.progress) },
                        offRoute = step.watch.left,
                    )
                }.distinctUntilChanged(),
        )
    }

// How far from the point of the line it was matched to [fix] may lie and still be drawn there: DRAWN_ON_LINE_METERS,
// or the fix's own accuracy where that is wider, and LEAVE_LINE_MARGIN_METERS more while the last fix was drawn there
// ([wasOnLine]); never past DRAWN_ON_LINE_MAX_METERS. A fix that reports no accuracy counts as exact.
private fun drawnOnLineMeters(
    fix: RouteFix,
    wasOnLine: Boolean,
): Double =
    max(DRAWN_ON_LINE_METERS, (fix.accuracyMeters ?: 0f).toDouble())
        .plus(if (wasOnLine) LEAVE_LINE_MARGIN_METERS else 0.0)
        .coerceAtMost(DRAWN_ON_LINE_MAX_METERS)

// The turns of [turnsAlong] the vehicle, with [progress] left, has not passed by the farthest it has been ([passed]),
// each with its distance along the line. The distance is what is left from the vehicle less what is left from the
// turn, both scaled as progressAt scales them, so a turn at the end is exactly as far as the distance left.
private fun RouteLine.turnsAhead(
    turnsAlong: List<TurnAlong>,
    passed: LinePosition,
    progress: RouteProgress,
): List<UpcomingTurn> =
    turnsAlong
        .filter { passed.travelledMeters - it.at.travelledMeters <= TURN_PASSED_METERS }
        .map {
            UpcomingTurn(
                turn = it.turn,
                distanceMeters = (progress.remainingMeters - progressAt(it.at).remainingMeters).coerceAtLeast(0.0),
            )
        }

// A turn of the route and where along its line the turn's point lies.
private data class TurnAlong(
    val turn: RouteTurn,
    val at: LinePosition,
)

// The follow's state between fixes: where the last fix lay on the line, what was left from there, the watch, the
// point the vehicle is drawn at on the line, if any, which also says whether the next fix keeps the leave margin, and
// how far along the line the vehicle has been.
private data class FollowStep(
    val position: LinePosition,
    val progress: RouteProgress,
    val watch: OffRouteWatch,
    val onLine: OnLinePoint?,
    // The farthest along the line the vehicle has been, which decides the turns it has passed: GPS noise moves a fix
    // back within the follower's own backtrack (BACKTRACK_METERS), and a turn once passed must not come back from it.
    // A position farther back than that behind this mark is a real jump back, a detour or a vehicle that turned round,
    // and the mark starts again there, so the turns ahead are worked out afresh.
    val passed: LinePosition,
)

// A segment's point nearest a fix: the squared distance to it, and the length of line behind it.
private data class LineMatch(
    val distanceSquared: Double,
    val travelled: Double,
)

// A point in [RouteLine]'s plane, in metres east and north of its origin.
private data class PlanePoint(
    val x: Double,
    val y: Double,
)

// The longitude difference folded into -180..180, so a line across the antimeridian stays short.
private fun longitudeDelta(
    longitude: Double,
    origin: Double,
): Double = ((longitude - origin) % 360.0 + 540.0) % 360.0 - 180.0
