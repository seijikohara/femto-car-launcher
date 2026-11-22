package io.github.seijikohara.femto.data.routing

import java.io.File

/** The offsets that keep the engine's micro-degrees positive, as BRouter stores a position. */
private const val LONGITUDE_OFFSET_MICRODEGREES = 180_000_000
private const val LATITUDE_OFFSET_MICRODEGREES = 90_000_000
private const val MICRODEGREES_PER_DEGREE = 1_000_000.0

private val MissingTileMessage = Regex("datafile (\\S+)\\.rd5 not found")

/** What one search needs. */
internal data class EngineRequest(
    val profile: File,
    val segmentDir: File,
    val origin: RoutePoint,
    val destination: RoutePoint,
    /** The memory, in MB, the engine sizes its caches for. */
    val memoryClassMb: Int,
)

/** A position as the engine stores it: micro-degrees offset by +180° (longitude) and +90° (latitude). */
internal data class EnginePosition(
    val offsetMicroLongitude: Int,
    val offsetMicroLatitude: Int,
)

/** What a search produced, in plain values. */
internal sealed interface EngineOutcome {
    data class Track(
        val positions: List<EnginePosition>,
        val distanceMeters: Int,
        val durationSeconds: Int,
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
            RouteResult.Found(Route(positions.map(EnginePosition::toRoutePoint), distanceMeters, durationSeconds))
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

private fun EnginePosition.toRoutePoint(): RoutePoint =
    RoutePoint(
        latitude = (offsetMicroLatitude - LATITUDE_OFFSET_MICRODEGREES) / MICRODEGREES_PER_DEGREE,
        longitude = (offsetMicroLongitude - LONGITUDE_OFFSET_MICRODEGREES) / MICRODEGREES_PER_DEGREE,
    )
