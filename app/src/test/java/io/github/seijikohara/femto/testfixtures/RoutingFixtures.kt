package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.common.download.ProbeResult
import io.github.seijikohara.femto.data.common.download.RemoteFile
import io.github.seijikohara.femto.data.routing.EngineOutcome
import io.github.seijikohara.femto.data.routing.EnginePosition
import io.github.seijikohara.femto.data.routing.ROUTE_TIME_LIMIT_MS
import io.github.seijikohara.femto.data.routing.ROUTING_TILE_HOST_PREFIX
import io.github.seijikohara.femto.data.routing.Route
import io.github.seijikohara.femto.data.routing.RoutePlanner
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.routing.RoutingProfileFiles
import io.github.seijikohara.femto.data.routing.RoutingTileStore
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

/** What the fixture's profile asset holds: a lookup version line, enough for the copy and a fake engine. */
internal val FakeRoutingProfileBytes: ByteArray = "---lookupversion:11".toByteArray()

/**
 * The routing stack on a test's own directory: a real [RoutingTileStore] and [RoutePlanner] over [feed] (the fake
 * transport) and [engine] (the fake BRouter), both on [dispatcher]. [installTile], [noDataRecord] and [offerTile] set
 * up what the device holds and what the server would answer.
 */
internal class RoutingFixture(
    private val root: File,
    private val dispatcher: CoroutineDispatcher,
    /** The bytes the store's volume can still allocate. */
    var freeBytes: Long = PLENTY_OF_ROOM_BYTES,
) {
    val feed = FakeRemoteFileFeed()
    val engine = FakeRouteEngine()

    val tiles: RoutingTileStore =
        RoutingTileStore(
            root = File(root, "routing"),
            feed = feed,
            freeBytes = { freeBytes },
            ioDispatcher = dispatcher,
        )

    val profilesDir: File get() = File(root, "routing/profiles")

    fun planner(
        engine: FakeRouteEngine = this.engine,
        profiles: RoutingProfileFiles = RoutingProfileFiles(profilesDir) { FakeRoutingProfileBytes.inputStream() },
        engineDispatcher: CoroutineDispatcher = dispatcher,
        timeLimitMs: Long = ROUTE_TIME_LIMIT_MS,
    ): RoutePlanner =
        RoutePlanner(
            tiles = tiles,
            profiles = profiles,
            engine = engine,
            timeLimitMs = timeLimitMs,
            ioDispatcher = dispatcher,
            engineDispatcher = engineDispatcher,
        )

    fun installTile(name: String) = installRoutingTile(root, name)

    fun noDataRecord(name: String) = writeNoDataRecord(root, name)

    /** The server has a file of [sizeBytes] for the tile [name]. */
    fun offerTile(
        name: String,
        sizeBytes: Long,
    ) {
        feed.probeAnswers[ROUTING_TILE_HOST_PREFIX + name + ".rd5"] =
            ProbeResult.Found(RemoteFile(sizeBytes = sizeBytes, etag = "\"$name\"", lastModifiedEpochMs = null))
    }

    /** The server answers 404 for the tile [name]: no road crosses it. */
    fun goneTile(name: String) {
        feed.probeAnswers[ROUTING_TILE_HOST_PREFIX + name + ".rd5"] = ProbeResult.Gone
    }

    private companion object {
        const val PLENTY_OF_ROOM_BYTES = 1L shl 40
    }
}

/** Put a one-byte tile [name] into the segment directory under [root], as an installed tile. */
internal fun installRoutingTile(
    root: File,
    name: String,
) {
    File(root, "routing/segments/$name.rd5").apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1))
}

/** Write a record of a tile the server has no file for under [root], as a fetch that found none leaves it. */
internal fun writeNoDataRecord(
    root: File,
    name: String,
) {
    File(root, "routing/records/$name.json")
        .apply { parentFile?.mkdirs() }
        .writeText("""{"file":null,"checkedAtEpochMs":0}""")
}

/**
 * What the engine answers for [fakeRoute]: its positions as BRouter stores them, micro-degrees offset by +180°
 * (longitude) and +90° (latitude).
 */
internal fun fakeEngineTrack(): EngineOutcome.Track =
    EngineOutcome.Track(
        positions =
            listOf(
                EnginePosition(offsetMicroLongitude = 312_770_000, offsetMicroLatitude = 123_840_000),
                EnginePosition(offsetMicroLongitude = 312_900_000, offsetMicroLatitude = 123_950_000),
                EnginePosition(offsetMicroLongitude = 313_000_000, offsetMicroLatitude = 124_070_000),
            ),
        distanceMeters = 41_200,
        durationSeconds = 2_950,
    )

/**
 * A route of two equal legs east along the equator, where a degree of longitude is as long as one of latitude: a fix
 * at 0.1° east is halfway, with 11 km and 600 s left.
 */
internal fun fakeEquatorRoute(): Route =
    fakeRoute(
        points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.1), RoutePoint(0.0, 0.2)),
        distanceMeters = 22_000,
        durationSeconds = 1_200,
    )

/**
 * A route east along the equator for 0.1° and back 0.0002° (about 22 m) north of it, as along the two carriageways of
 * one road with a turn at the far end: 22,261 m, about its line's length, and 1,200 s.
 */
internal fun fakeOutAndBackRoute(): Route =
    fakeRoute(
        points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.1), RoutePoint(0.0002, 0.1), RoutePoint(0.0002, 0.0)),
        distanceMeters = 22_261,
        durationSeconds = 1_200,
    )

/**
 * A route east along the equator for 0.1° and back along the same line, as out and back on a road with one
 * carriageway: every point of the line belongs to both passes. 22,239 m, its line's length, and 1,200 s.
 */
internal fun fakeSameRoadOutAndBackRoute(): Route =
    fakeRoute(
        points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.1), RoutePoint(0.0, 0.0)),
        distanceMeters = 22_239,
        durationSeconds = 1_200,
    )

/**
 * A route east along the equator for 0.1° and back 0.002° (about 222 m) north of it, as along two parallel roads: a
 * point midway between the two is as far from one pass as from the other, and too far from both to count as on
 * either.
 */
internal fun fakeParallelRoadsRoute(): Route =
    fakeRoute(
        points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.1), RoutePoint(0.002, 0.1), RoutePoint(0.002, 0.0)),
        distanceMeters = 22_461,
        durationSeconds = 1_200,
    )

/** A short route in the routing tile E130_N30, as the planner returns one. */
internal fun fakeRoute(
    points: List<RoutePoint> =
        listOf(
            RoutePoint(latitude = 33.84, longitude = 132.77),
            RoutePoint(latitude = 33.95, longitude = 132.9),
            RoutePoint(latitude = 34.07, longitude = 133.0),
        ),
    distanceMeters: Int = 41_200,
    durationSeconds: Int = 2_950,
): Route = Route(points = points, distanceMeters = distanceMeters, durationSeconds = durationSeconds)
