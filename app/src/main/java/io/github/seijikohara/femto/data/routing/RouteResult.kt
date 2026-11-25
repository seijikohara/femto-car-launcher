package io.github.seijikohara.femto.data.routing

import java.io.File

/** One computed route. */
internal data class Route(
    /** From origin to destination, along the roads. */
    val points: List<RoutePoint>,
    val distanceMeters: Int,
    /** The travel time the profile estimates. */
    val durationSeconds: Int,
    /**
     * The engine's turn hints along [points], in route order. Null means no turn data: a route file of an earlier
     * build, or hints that could not be read. An empty list means the engine reported no turn. The two stay apart
     * so that a later turn card never reads a missing list as a route without turns.
     */
    val turns: List<RouteTurn>? = null,
)

internal sealed interface RouteResult {
    data class Found(
        val route: Route,
        /** The engine's record of [route] for a later search to the same destination, or null when none was written. */
        val reference: File? = null,
    ) : RouteResult

    /** Tiles the route needs are not installed (and not known to have no data); fetching them may help. */
    data class MissingTiles(
        val names: List<String>,
    ) : RouteResult

    /**
     * The origin or the destination lies in a tile the server has no data for, such as open sea: there is no road to
     * start or end on, and no download can bring one.
     */
    data object NoRoadData : RouteResult

    /** A tile was built for another version of the lookup table than the app ships. */
    data object DataVersionMismatch : RouteResult

    /** The search ran out of time; BRouter's time grows with the square of the distance. */
    data object TimedOut : RouteResult

    /** Anything else, with the engine's message: no road near a point, no connection between them, a broken file. */
    data class Failed(
        val message: String,
    ) : RouteResult
}
