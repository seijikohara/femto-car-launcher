package io.github.seijikohara.femto.data.routing

import java.io.File
import kotlin.math.abs

/** The offsets that keep the engine's micro-degrees positive, as BRouter stores a position. */
private const val LONGITUDE_OFFSET_MICRODEGREES = 180_000_000
private const val LATITUDE_OFFSET_MICRODEGREES = 90_000_000

/** BRouter's resolution: it stores and returns every position in whole micro-degrees. */
internal const val MICRODEGREES_PER_DEGREE = 1_000_000.0

private val MissingTileMessage = Regex("datafile (\\S+)\\.rd5 not found")

/** What one search needs. */
internal data class EngineRequest(
    val profile: File,
    val segmentDir: File,
    val origin: RoutePoint,
    val destination: RoutePoint,
    /** The memory, in MB, the engine sizes its caches for. */
    val memoryClassMb: Int,
    /** The vehicle's heading at [origin], in degrees clockwise from north, so the route does not open with a U-turn. */
    val startHeadingDegrees: Int? = null,
    /**
     * An earlier route to the same destination, as [referenceOut] wrote it: the search stops where it rejoins that
     * route. The engine ignores a reference built for another destination or under other profile settings.
     */
    val referenceIn: File? = null,
    /** Where the engine writes a found route as the reference for a later search to the same destination. */
    val referenceOut: File? = null,
    /** The profile's parameters by name, overriding the profile's own values ([engineParameters]). */
    val parameters: Map<String, String> = emptyMap(),
    /**
     * Which of BRouter's routes to return: 0 is the best one, and each later one steers away from the roads of the
     * ones before it, which the engine computes again first.
     */
    val alternativeIndex: Int = 0,
)

/** A position as the engine stores it: micro-degrees offset by +180° (longitude) and +90° (latitude). */
internal data class EnginePosition(
    val offsetMicroLongitude: Int,
    val offsetMicroLatitude: Int,
)

/** One turn hint as the engine reports it, in plain values. */
internal data class EngineTurn(
    /** The index in [EngineOutcome.Track.positions] of the point the turn is at. */
    val positionIndex: Int,
    /** BRouter's JSON command number ([TurnKind.code]). */
    val command: Int,
    /** BRouter's exit number: 0 off a roundabout; its sign follows the side of traffic. */
    val exitNumber: Int,
    val angleDegrees: Int,
)

/** What a search produced, in plain values. */
internal sealed interface EngineOutcome {
    data class Track(
        val positions: List<EnginePosition>,
        val distanceMeters: Int,
        val durationSeconds: Int,
        /** The engine's turn hints along [positions]; null when they could not be read, empty when it reported none. */
        val turns: List<EngineTurn>? = null,
    ) : EngineOutcome

    /** The engine's own message: it reports every failure as text. */
    data class Error(
        val message: String,
    ) : EngineOutcome
}

/** One prepared search. [run] blocks; [terminate], from another thread, makes a running search stop soon. */
internal interface EngineSearch {
    fun run(timeLimitMs: Long): EngineOutcome

    fun terminate()
}

/** The seam between the planner and the engine, so JVM tests can stand in for BRouter. */
internal fun interface RouteEngine {
    /** Prepare a search, which reads the profile; blocking, and throws when the profile cannot be read. */
    fun prepare(request: EngineRequest): EngineSearch
}

internal fun EngineOutcome.toRouteResult(): RouteResult =
    when (this) {
        is EngineOutcome.Track if positions.size < 2 -> {
            RouteResult.Failed("track without a line")
        }

        is EngineOutcome.Track -> {
            RouteResult.Found(
                Route(
                    points = positions.map(EnginePosition::toRoutePoint),
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    turns = turns?.toRouteTurnsOrNull(pointCount = positions.size),
                ),
            )
        }

        is EngineOutcome.Error -> {
            routeFailureOf(message)
        }
    }

// BRouter has no error codes, so its messages are matched: the missing tile is an IllegalArgumentException's message,
// the version mismatch an IOException's toString(), and a timeout names the pass that ran out of time.
private fun routeFailureOf(message: String): RouteResult =
    MissingTileMessage.find(message)?.let { RouteResult.MissingTiles(listOf(it.groupValues[1])) }
        ?: when {
            "lookup version mismatch" in message -> RouteResult.DataVersionMismatch
            " timeout after " in message -> RouteResult.TimedOut
            else -> RouteResult.Failed(message)
        }

// A hint never fails a route: one the app cannot place leaves the route without turn data, whole, because a list with
// a gap would make a later turn card skip a turn. The exit number loses its sign, which only says the side of traffic.
private fun List<EngineTurn>.toRouteTurnsOrNull(pointCount: Int): List<RouteTurn>? =
    map { row ->
        TurnKind.ofCodeOrNull(row.command)?.let { kind ->
            RouteTurn(
                pointIndex = row.positionIndex,
                kind = kind,
                exitNumber = abs(row.exitNumber),
                angleDegrees = row.angleDegrees,
            )
        }
    }.takeIf { null !in it }
        ?.requireNoNulls()
        ?.onLineOrNull(pointCount)

private fun EnginePosition.toRoutePoint(): RoutePoint =
    RoutePoint(
        latitude = (offsetMicroLatitude - LATITUDE_OFFSET_MICRODEGREES) / MICRODEGREES_PER_DEGREE,
        longitude = (offsetMicroLongitude - LONGITUDE_OFFSET_MICRODEGREES) / MICRODEGREES_PER_DEGREE,
    )
