package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.common.download.ProbeResult
import io.github.seijikohara.femto.data.common.download.RemoteFile
import io.github.seijikohara.femto.data.common.download.RevalidationResult
import io.github.seijikohara.femto.data.routing.ActiveRoute
import io.github.seijikohara.femto.data.routing.EngineOutcome
import io.github.seijikohara.femto.data.routing.EnginePosition
import io.github.seijikohara.femto.data.routing.EngineTurn
import io.github.seijikohara.femto.data.routing.KeptRoute
import io.github.seijikohara.femto.data.routing.ROUTE_TIME_LIMIT_MS
import io.github.seijikohara.femto.data.routing.ROUTING_TILE_HOST_PREFIX
import io.github.seijikohara.femto.data.routing.Route
import io.github.seijikohara.femto.data.routing.RouteConditions
import io.github.seijikohara.femto.data.routing.RouteFix
import io.github.seijikohara.femto.data.routing.RoutePlanner
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.routing.RouteTurn
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
    /** The wall clock of the store, which dates each check of a tile, and of the planner, which ages each reference. */
    var nowEpochMs: Long = FIXTURE_EPOCH_MS,
) {
    val feed = FakeRemoteFileFeed()
    val engine = FakeRouteEngine()

    val tiles: RoutingTileStore =
        RoutingTileStore(
            root = File(root, "routing"),
            feed = feed,
            freeBytes = { freeBytes },
            ioDispatcher = dispatcher,
            clock = { nowEpochMs },
        )

    val profilesDir: File get() = File(root, "routing/profiles")

    /** Where the planner keeps the engine's references, as the app's cache directory holds them. */
    val referenceDir: File get() = File(root, "routing-references")

    fun planner(
        engine: FakeRouteEngine = this.engine,
        profiles: RoutingProfileFiles = RoutingProfileFiles(profilesDir) { FakeRoutingProfileBytes.inputStream() },
        engineDispatcher: CoroutineDispatcher = dispatcher,
        timeLimitMs: Long = ROUTE_TIME_LIMIT_MS,
        nanoTime: () -> Long = System::nanoTime,
    ): RoutePlanner =
        RoutePlanner(
            tiles = tiles,
            profiles = profiles,
            referenceDir = referenceDir,
            engine = engine,
            timeLimitMs = timeLimitMs,
            ioDispatcher = dispatcher,
            engineDispatcher = engineDispatcher,
            nanoTime = nanoTime,
            nowEpochMs = { nowEpochMs },
        )

    fun installTile(name: String) = installRoutingTile(root, name)

    /** Install the one-byte tile [name] with a record of the server's file, last checked at [checkedAtEpochMs]. */
    fun installCheckedTile(
        name: String,
        checkedAtEpochMs: Long,
    ) {
        installRoutingTile(root, name)
        File(root, "routing/records/$name.json")
            .apply { parentFile?.mkdirs() }
            .writeText(
                """{"file":{"sizeBytes":1,"etag":"\"$name\"","lastModifiedEpochMs":null},""" +
                    """"checkedAtEpochMs":$checkedAtEpochMs}""",
            )
    }

    /** The server has rebuilt the tile [name] into a file of [sizeBytes]. */
    fun rebuildTile(
        name: String,
        sizeBytes: Long,
    ) {
        feed.revalidateAnswers[tileUrl(name)] =
            RevalidationResult.Changed(
                RemoteFile(sizeBytes = sizeBytes, etag = "\"$name-new\"", lastModifiedEpochMs = null),
            )
    }

    /** The server still has the tile [name] as it was installed. */
    fun keepTile(name: String) {
        feed.revalidateAnswers[tileUrl(name)] = RevalidationResult.NotModified
    }

    fun tileUrl(name: String): String = ROUTING_TILE_HOST_PREFIX + name + ".rd5"

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

    companion object {
        private const val PLENTY_OF_ROOM_BYTES = 1L shl 40

        /** The fixture's wall clock at the start of a test: 2026-11-22T09:00:00Z. */
        const val FIXTURE_EPOCH_MS = 1_795_338_000_000L
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
internal fun fakeEngineTrack(turns: List<EngineTurn>? = null): EngineOutcome.Track =
    EngineOutcome.Track(
        positions =
            listOf(
                EnginePosition(offsetMicroLongitude = 312_770_000, offsetMicroLatitude = 123_840_000),
                EnginePosition(offsetMicroLongitude = 312_900_000, offsetMicroLatitude = 123_950_000),
                EnginePosition(offsetMicroLongitude = 313_000_000, offsetMicroLatitude = 124_070_000),
            ),
        distanceMeters = 41_200,
        durationSeconds = 2_950,
        turns = turns,
    )

private object RoutingResources

/** The text of the engine output [fileName] under `app/src/test/resources/routing/`, byte for byte. */
internal fun brouterJson(fileName: String): String =
    requireNotNull(RoutingResources::class.java.getResourceAsStream("/routing/$fileName")) {
        "Missing test resource routing/$fileName"
    }.use { it.readBytes().decodeToString() }

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

/** A fix at [point] taken at [elapsedMs] on the boot clock, moving at 10 m/s unless [speedMetersPerSecond] says not. */
internal fun fakeRouteFix(
    point: RoutePoint,
    elapsedMs: Long,
    speedMetersPerSecond: Float? = 10f,
    accuracyMeters: Float? = null,
    bearingDegrees: Float? = null,
): RouteFix =
    RouteFix(
        point = point,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
        elapsedMs = elapsedMs,
    )

/** This route session without its choice between routes, for tests about the route being followed. */
internal fun ActiveRoute.withoutChoice(): ActiveRoute =
    copy(candidates = emptyList(), selected = 0, choosing = false, finding = false, searchCompleted = false)

/**
 * A route kept across a restart: [fakeRoute], whose points lie on whole micro-degrees as an engine route's do, to a
 * destination picked to more digits than a micro-degree holds, avoiding tolls.
 */
internal fun fakeKeptRoute(): KeptRoute =
    KeptRoute(
        route = fakeRoute(),
        destination = RoutePoint(latitude = 34.0712345678, longitude = 133.0098765432),
        conditions = RouteConditions(avoidTolls = true),
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
    turns: List<RouteTurn>? = null,
): Route = Route(points = points, distanceMeters = distanceMeters, durationSeconds = durationSeconds, turns = turns)
