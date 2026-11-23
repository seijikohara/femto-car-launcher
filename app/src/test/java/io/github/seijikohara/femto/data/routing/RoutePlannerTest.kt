package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.FakeRouteEngine
import io.github.seijikohara.femto.testfixtures.FakeRoutingProfileBytes
import io.github.seijikohara.femto.testfixtures.RoutingFixture
import io.github.seijikohara.femto.testfixtures.fakeEngineTrack
import io.github.seijikohara.femto.testfixtures.fakeRoute
import io.github.seijikohara.femto.testfixtures.installRoutingTile
import io.github.seijikohara.femto.testfixtures.writeNoDataRecord
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun `no route has run before the first one`() =
        runTest {
            assertNull(planner(FakeRouteEngine()).lastRunOrNull())
        }

    @Test
    fun `the last route's outcome and computing time are kept`() =
        runTest {
            installTile("E130_N30")
            // Each read of the clock moves it on by 1.5 s: the search takes one step of it.
            val clock = longArrayOf(0L)
            val planner =
                RoutingFixture(tempFolder.root, StandardTestDispatcher(testScheduler)).planner(
                    engine = FakeRouteEngine(outcome = TRACK),
                    nanoTime = { clock[0].also { clock[0] += 1_500_000_000L } },
                )

            planner.route(MATSUYAMA, IMABARI)

            assertEquals(RouteRun(RouteRunOutcome.FOUND, computingMs = 1_500L), planner.lastRunOrNull())
        }

    @Test
    fun `a route that needs data is kept as such, and a later one replaces it`() =
        runTest {
            val planner = planner(FakeRouteEngine(outcome = TRACK))
            planner.route(MATSUYAMA, IMABARI)
            assertEquals(RouteRunOutcome.MISSING_TILES, planner.lastRunOrNull()?.outcome)

            installTile("E130_N30")
            planner.route(MATSUYAMA, IMABARI)

            assertEquals(RouteRunOutcome.FOUND, planner.lastRunOrNull()?.outcome)
        }

    // --- The reference route and the heading -----------------------------------

    @Test
    fun `a route asks the engine to write a reference in the reference directory and returns it`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(outcome = TRACK, writesReference = true)

            val result = planner(engine).route(MATSUYAMA, IMABARI)

            val reference = assertNotNull(assertIs<RouteResult.Found>(result).reference)
            assertTrue(reference.isFile)
            assertEquals(referenceDir(), reference.parentFile)
            assertEquals(reference, engine.requests.single().referenceOut)
        }

    @Test
    fun `a reroute hands the engine the reference and the heading`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(outcome = TRACK)
            val reference = File(referenceDir(), "earlier.bin")

            planner(engine).route(MATSUYAMA, IMABARI, startHeadingDegrees = 90, reference = reference)

            val request = engine.requests.single()
            assertEquals(reference to 90, request.referenceIn to request.startHeadingDegrees)
        }

    @Test
    fun `a found route whose engine wrote no reference returns none`() =
        runTest {
            installTile("E130_N30")

            val result = planner(FakeRouteEngine(outcome = TRACK)).route(MATSUYAMA, IMABARI)

            assertEquals(RouteResult.Found(ROUTE, reference = null), result)
            assertEquals(emptyList(), referenceFiles())
        }

    @Test
    fun `a failed search leaves no reference file`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(outcome = EngineOutcome.Error("no track found"), writesReference = true)

            planner(engine).route(MATSUYAMA, IMABARI)

            assertEquals(emptyList(), referenceFiles())
        }

    @Test
    fun `prune keeps only the given references`() =
        runTest {
            installTile("E130_N30")
            val planner = planner(FakeRouteEngine(outcome = TRACK, writesReference = true))
            val first = assertNotNull(assertIs<RouteResult.Found>(planner.route(MATSUYAMA, IMABARI)).reference)
            val second = assertNotNull(assertIs<RouteResult.Found>(planner.route(MATSUYAMA, IMABARI)).reference)
            listOf(first, second).forEach { it.setLastModified(NOW_EPOCH_MS - 2 * REFERENCE_PRUNE_GRACE_MS) }

            planner.pruneReferences(listOf(second))

            assertEquals(listOf(second), referenceFiles())
            assertFalse(first.exists())
        }

    // A search beside the prune may just have written the reference its route is about to keep.
    @Test
    fun `prune keeps a reference written within the last minute`() =
        runTest {
            val planner = planner(FakeRouteEngine())
            val old = referenceFile("old.bin", writtenAtEpochMs = NOW_EPOCH_MS - REFERENCE_PRUNE_GRACE_MS - 1_000L)
            val fresh = referenceFile("fresh.bin", writtenAtEpochMs = NOW_EPOCH_MS - REFERENCE_PRUNE_GRACE_MS + 1_000L)

            planner.pruneReferences(keep = emptyList())

            assertEquals(listOf(fresh), referenceFiles())
            assertFalse(old.exists())
        }

    // As the time-limit test above: the engine blocks a real thread, so the limit runs in real time.
    @Test
    fun `the time limit given to route bounds that search`() =
        runTest {
            installTile("E130_N30")
            val engine = FakeRouteEngine(holdsUntilTerminated = true)

            val result =
                planner(engine, engineDispatcher = Dispatchers.Default)
                    .route(MATSUYAMA, IMABARI, timeLimitMs = SHORT_LIMIT_MS)

            assertEquals(RouteResult.TimedOut, result)
            assertEquals(listOf(SHORT_LIMIT_MS), engine.timeLimits)
        }

    // The fixture's feed is never asked: the planner only reads which tiles are on disk.
    private fun TestScope.planner(
        engine: FakeRouteEngine,
        profiles: RoutingProfileFiles = RoutingProfileFiles(profilesDir()) { FakeRoutingProfileBytes.inputStream() },
        engineDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        timeLimitMs: Long = ROUTE_TIME_LIMIT_MS,
    ): RoutePlanner =
        RoutingFixture(tempFolder.root, StandardTestDispatcher(testScheduler))
            .planner(engine, profiles, engineDispatcher, timeLimitMs)

    private fun profilesDir(): File = File(tempFolder.root, "routing/profiles")

    private fun referenceDir(): File = File(tempFolder.root, "routing-references")

    private fun referenceFiles(): List<File> = referenceDir().listFiles().orEmpty().toList()

    private fun referenceFile(
        name: String,
        writtenAtEpochMs: Long,
    ): File =
        File(referenceDir().apply { mkdirs() }, name).apply {
            writeBytes(byteArrayOf(1))
            setLastModified(writtenAtEpochMs)
        }

    private fun installTile(name: String) = installRoutingTile(tempFolder.root, name)

    private fun noDataRecord(name: String) = writeNoDataRecord(tempFolder.root, name)

    private companion object {
        val MATSUYAMA = RoutePoint(latitude = 33.84, longitude = 132.77)
        val IMABARI = RoutePoint(latitude = 34.07, longitude = 133.0)

        // The engine's answer for ROUTE, in its own positions.
        val TRACK = fakeEngineTrack()
        val ROUTE = fakeRoute()

        val PROFILE_BYTES = FakeRoutingProfileBytes
        const val SHORT_LIMIT_MS = 200L

        // The planner's wall clock in these tests, the routing fixture's.
        const val NOW_EPOCH_MS = RoutingFixture.FIXTURE_EPOCH_MS
    }
}
