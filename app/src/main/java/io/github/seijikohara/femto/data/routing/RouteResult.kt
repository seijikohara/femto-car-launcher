package io.github.seijikohara.femto.data.routing

/** One computed route. */
internal data class Route(
    /** From origin to destination, along the roads. */
    val points: List<RoutePoint>,
    val distanceMeters: Int,
    /** The travel time the profile estimates. */
    val durationSeconds: Int,
)

internal sealed interface RouteResult {
    data class Found(
        val route: Route,
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
