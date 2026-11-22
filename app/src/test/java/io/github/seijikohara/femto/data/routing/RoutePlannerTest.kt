package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.FakeRemoteFileFeed
import io.github.seijikohara.femto.testfixtures.FakeRouteEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutePlannerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `a tile that is not installed is named before the engine runs`() =
        runTest {
            val engine = FakeRouteEngine()

            val result = planner(engine).route(MATSUYAMA, IMABARI)

            assertEquals(RouteResult.MissingTiles(listOf("E130_N30")), result)
            assertEquals(emptyList(), engine.requests)
        }

    @Test
    fun `a tile the server has no data for is not missing`() =
        runTest {
            noDataRecord("E130_N30")

            val result = planner(FakeRouteEngine(outcome = TRACK)).route(MATSUYAMA, IMABARI)

            assertEquals(RouteResult.Found(ROUTE), result)
        }

    // The mid-Atlantic case: the precheck lets the tile through, and BRouter then cannot open it.
    @Test
    fun `a point on a tile without data has no road data`() =
        runTest {
            noDataRecord("E130_N30")
            val engine = FakeRouteEngine(outcome = EngineOutcome.Error("datafile E130_N30.rd5 not found"))

            val result = planner(engine).route(MATSUYAMA, IMABARI)

            assertEquals(RouteResult.NoRoadData, result)
        }

    @Test
    fun `a tile the engine could not open is missing when nothing says it has no data`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(outcome = EngineOutcome.Error("datafile E135_N30.rd5 not found"))

            val result = planner(engine).route(MATSUYAMA, IMABARI)

            assertEquals(RouteResult.MissingTiles(listOf("E135_N30")), result)
        }

    @Test
    fun `a found track becomes a route in degrees`() =
        runTest {
            installTile("E130_N30")

            val result = planner(FakeRouteEngine(outcome = TRACK)).route(MATSUYAMA, IMABARI)

            assertEquals(RouteResult.Found(ROUTE), result)
        }

    @Test
    fun `a track without a line is a failed search`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(outcome = TRACK.copy(positions = TRACK.positions.take(1)))

            val result = planner(engine).route(MATSUYAMA, IMABARI)

            assertTrue(result is RouteResult.Failed, "was $result")
        }

    @Test
    fun `the engine reads the copied profile, the segment directory and the two points`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(outcome = TRACK)

            planner(engine).route(MATSUYAMA, IMABARI)

            val request = engine.requests.single()
            assertEquals(File(profilesDir(), CAR_PROFILE), request.profile)
            assertEquals(PROFILE_BYTES.decodeToString(), request.profile.readText())
            assertEquals(PROFILE_BYTES.decodeToString(), File(profilesDir(), "lookups.dat").readText())
            assertEquals(File(tempFolder.root, "routing/segments"), request.segmentDir)
            assertEquals(MATSUYAMA to IMABARI, request.origin to request.destination)
            assertEquals(listOf(ROUTE_TIME_LIMIT_MS), engine.timeLimits)
        }

    @Test
    fun `a profile that cannot be copied is a failed search`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(outcome = TRACK)
            val profiles = RoutingProfileFiles(profilesDir()) { throw IOException("asset missing") }

            val result = planner(engine, profiles = profiles).route(MATSUYAMA, IMABARI)

            assertEquals(RouteResult.Failed("asset missing"), result)
            assertEquals(emptyList(), engine.requests)
        }

    // The engine blocks a real thread here, so its time limit runs on that dispatcher in real time.
    @Test
    fun `a search past the time limit terminates the engine`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(holdsUntilTerminated = true)

            val result =
                planner(engine, engineDispatcher = Dispatchers.Default, timeLimitMs = SHORT_LIMIT_MS)
                    .route(MATSUYAMA, IMABARI)

            assertEquals(RouteResult.TimedOut, result)
            assertTrue(engine.terminated)
        }

    @Test
    fun `a cancelled caller terminates the engine`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(holdsUntilTerminated = true)
            val planner = planner(engine, engineDispatcher = Dispatchers.Default)
            val search = launch { planner.route(MATSUYAMA, IMABARI) }
            engine.started.await()

            search.cancelAndJoin()

            assertTrue(engine.terminated)
        }

    private fun TestScope.planner(
        engine: FakeRouteEngine,
        profiles: RoutingProfileFiles = RoutingProfileFiles(profilesDir()) { PROFILE_BYTES.inputStream() },
        engineDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        timeLimitMs: Long = ROUTE_TIME_LIMIT_MS,
    ): RoutePlanner =
        RoutePlanner(
            tiles = store(),
            profiles = profiles,
            engine = engine,
            timeLimitMs = timeLimitMs,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            engineDispatcher = engineDispatcher,
        )

    // The feed is never asked: the planner only reads which tiles are on disk.
    private fun TestScope.store(): RoutingTileStore =
        RoutingTileStore(
            root = File(tempFolder.root, "routing"),
            feed = FakeRemoteFileFeed(),
            freeBytes = { 0L },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    private fun profilesDir(): File = File(tempFolder.root, "routing/profiles")

    private fun installTile(name: String) {
        File(tempFolder.root, "routing/segments/$name.rd5").apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(1))
    }

    private fun noDataRecord(name: String) {
        File(tempFolder.root, "routing/records/$name.json")
            .apply { parentFile?.mkdirs() }
            .writeText("""{"file":null,"checkedAtEpochMs":0}""")
    }

    private companion object {
        val MATSUYAMA = RoutePoint(latitude = 33.84, longitude = 132.77)
        val IMABARI = RoutePoint(latitude = 34.07, longitude = 133.0)

        // Positions as BRouter stores them: micro-degrees offset by +180° (longitude) and +90° (latitude).
        val TRACK =
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
        val ROUTE =
            Route(
                points =
                    listOf(
                        RoutePoint(latitude = 33.84, longitude = 132.77),
                        RoutePoint(latitude = 33.95, longitude = 132.9),
                        RoutePoint(latitude = 34.07, longitude = 133.0),
                    ),
                distanceMeters = 41_200,
                durationSeconds = 2_950,
            )

        val PROFILE_BYTES = "---lookupversion:11".toByteArray()
        const val SHORT_LIMIT_MS = 200L
    }
}
