package io.github.seijikohara.femto.data.routing

/** What the driver does at a turn. [code] is BRouter's JSON command number, which the route file keeps too. */
internal enum class TurnKind(
    val code: Int,
) {
    STRAIGHT(1),
    LEFT(2),
    SLIGHT_LEFT(3),
    SHARP_LEFT(4),
    RIGHT(5),
    SLIGHT_RIGHT(6),
    SHARP_RIGHT(7),
    KEEP_LEFT(8),
    KEEP_RIGHT(9),
    U_TURN_LEFT(10),
    U_TURN_RIGHT(11),
    OFF_ROUTE(12),

    /** A roundabout of right-hand traffic (BRouter's RNDB). */
    ROUNDABOUT_COUNTERCLOCKWISE(13),

    /** A roundabout of left-hand traffic (BRouter's RNLB). */
    ROUNDABOUT_CLOCKWISE(14),
    U_TURN(15),
    BEELINE(16),
    EXIT_LEFT(17),
    EXIT_RIGHT(18),
    ;

    companion object {
        /** The kind that the engine's command number [code] names, or null for a number this build does not know. */
        fun ofCodeOrNull(code: Int): TurnKind? = entries.firstOrNull { it.code == code }
    }
}

/** One turn hint of a route, as the engine reports it and as the route file keeps it. */
internal data class RouteTurn(
    /** The index in [Route.points] of the point the turn is at; a roundabout's is its entry. */
    val pointIndex: Int,
    val kind: TurnKind,
    /** Which exit to take at a roundabout, counted from the entry, from 1; 0 at any other turn. */
    val exitNumber: Int,
    /**
     * The engine's angle in degrees, negative to the left. At a roundabout it is the angle driven around the ring,
     * not the change of heading, and can pass 180 either way.
     */
    val angleDegrees: Int,
)

/**
 * This list when it fits a line of [pointCount] points (every index on the line, in route order), or null. A list that
 * does not fit is dropped whole: one with a gap would make a later turn card skip a turn.
 */
internal fun List<RouteTurn>.onLineOrNull(pointCount: Int): List<RouteTurn>? =
    takeIf { turns ->
        turns.all { it.pointIndex in 0 until pointCount } &&
            turns.zipWithNext().all { (earlier, later) -> earlier.pointIndex <= later.pointIndex }
    }
