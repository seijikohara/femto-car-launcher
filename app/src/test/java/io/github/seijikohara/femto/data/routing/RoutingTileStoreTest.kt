package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.data.map.offline.RegionFeedApi
import io.github.seijikohara.femto.data.map.offline.regionHttpClient
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The routing tile store against a real transport and a mock brouter.de. */
class RoutingTileStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    private var now = START_EPOCH_MS

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- fetch ----------------------------------------------------------------

    @Test
    fun `a tile the server has is downloaded into the segment directory`() =
        runTest {
            server.enqueue(headResponse(TILE_BYTES.size))
            server.enqueue(fileResponse(TILE_BYTES))
            val store = store()

            val result = store.fetch(TILE)

            assertEquals(TileFetchResult.INSTALLED, result)
            assertContentEquals(TILE_BYTES, tileFile().readBytes())
            assertEquals(listOf("$TILE.rd5"), segmentDir().list()?.toList())
            val requests = List(2) { server.takeRequest() }
            assertEquals(listOf("HEAD", "GET"), requests.map { it.method })
            assertEquals(listOf("/$TILE.rd5", "/$TILE.rd5"), requests.map { it.path })
            assertEquals(List(2) { USER_AGENT }, requests.map { it.getHeader("User-Agent") })
        }

    @Test
    fun `an installed tile is listed with its size and answers without a request`() =
        runTest {
            server.enqueue(headResponse(TILE_BYTES.size))
            server.enqueue(fileResponse(TILE_BYTES))
            val store = store()
            store.fetch(TILE)

            val again = store.fetch(TILE)

            assertEquals(TileFetchResult.INSTALLED, again)
            assertEquals(2, server.requestCount)
            assertEquals(
                listOf(InstalledRoutingTile(TILE, TILE_BYTES.size.toLong(), MODIFIED_EPOCH_MS, START_EPOCH_MS)),
                store.installedTiles(),
            )
        }

    @Test
    fun `a tile the server does not have is recorded and not asked again`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            val store = store()

            val results = List(2) { store.fetch(TILE) }

            assertEquals(List(2) { TileFetchResult.NO_DATA }, results)
            assertEquals(1, server.requestCount)
            assertEquals(emptyList(), store.missingTiles(listOf(TILE)))
        }

    @Test
    fun `a tile without data is asked again once its check is due`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(404))
            val store = store()
            store.fetch(TILE)

            now += ROUTING_TILE_CHECK_INTERVAL_MS
            store.fetch(TILE)

            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a body shorter than announced is not installed and its part is kept`() =
        runTest {
            val store = interruptedAfterFiveBytes()

            assertFalse(tileFile().exists())
            assertContentEquals(TILE_BYTES.copyOf(5), partFile().readBytes())
            assertEquals(listOf(TILE), store.missingTiles(listOf(TILE)))
        }

    @Test
    fun `a resume asks for the rest with the etag and installs the whole tile`() =
        runTest {
            val store = interruptedAfterFiveBytes()
            server.enqueue(headResponse(TILE_BYTES.size))
            server.enqueue(rangeResponse("bytes 5-9/10", TILE_BYTES.copyOfRange(5, 10), ETAG))

            val result = store.fetch(TILE)

            assertEquals(TileFetchResult.INSTALLED, result)
            assertContentEquals(TILE_BYTES, tileFile().readBytes())
            val resume = List(4) { server.takeRequest() }.last()
            assertEquals("bytes=5-", resume.getHeader("Range"))
            assertEquals(ETAG, resume.getHeader("If-Range"))
        }

    @Test
    fun `a range of another file discards the part and installs nothing`() =
        runTest {
            val store = interruptedAfterFiveBytes()
            server.enqueue(headResponse(TILE_BYTES.size))
            server.enqueue(rangeResponse("bytes 5-9/10", NEW_BYTES.copyOfRange(5, 10), NEW_ETAG))

            val result = store.fetch(TILE)

            assertEquals(TileFetchResult.NETWORK_FAILED, result)
            assertFalse(tileFile().exists())
            assertFalse(partFile().exists())
        }

    @Test
    fun `a whole file in answer to a resume starts the part over`() =
        runTest {
            val store = interruptedAfterFiveBytes()
            server.enqueue(headResponse(TILE_BYTES.size))
            server.enqueue(fileResponse(TILE_BYTES))

            val result = store.fetch(TILE)

            assertEquals(TileFetchResult.INSTALLED, result)
            assertContentEquals(TILE_BYTES, tileFile().readBytes())
        }

    @Test
    fun `a part of an older file is not resumed`() =
        runTest {
            val store = interruptedAfterFiveBytes()
            server.enqueue(headResponse(NEW_BYTES.size, NEW_ETAG))
            server.enqueue(fileResponse(NEW_BYTES, NEW_ETAG))

            val result = store.fetch(TILE)

            assertEquals(TileFetchResult.INSTALLED, result)
            assertContentEquals(NEW_BYTES, tileFile().readBytes())
            assertNull(List(4) { server.takeRequest() }.last().getHeader("Range"))
        }

    // --- refresh ----------------------------------------------------------------

    @Test
    fun `a refresh within a week asks nothing`() =
        runTest {
            val store = installed()

            now += ROUTING_TILE_CHECK_INTERVAL_MS - 1
            val result = store.refresh(TILE)

            assertEquals(TileRefreshResult.NOT_DUE, result)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a due refresh asks with the etag and keeps a tile that has not changed`() =
        runTest {
            val store = installed()
            server.enqueue(MockResponse().setResponseCode(304))

            now += ROUTING_TILE_CHECK_INTERVAL_MS
            val result = store.refresh(TILE)

            assertEquals(TileRefreshResult.UNCHANGED, result)
            val check = List(3) { server.takeRequest() }.last()
            assertEquals("HEAD", check.method)
            assertEquals(ETAG, check.getHeader("If-None-Match"))
            assertContentEquals(TILE_BYTES, tileFile().readBytes())
            assertEquals(now, store.installedTiles().single().checkedAtEpochMs)
        }

    @Test
    fun `a due refresh replaces a tile the server has rebuilt`() =
        runTest {
            val store = installed()
            server.enqueue(headResponse(NEW_BYTES.size, NEW_ETAG))
            server.enqueue(fileResponse(NEW_BYTES, NEW_ETAG))

            now += ROUTING_TILE_CHECK_INTERVAL_MS
            val result = store.refresh(TILE)

            assertEquals(TileRefreshResult.UPDATED, result)
            assertContentEquals(NEW_BYTES, tileFile().readBytes())
        }

    @Test
    fun `a failed replacement keeps the installed tile`() =
        runTest {
            val store = installed()
            server.enqueue(headResponse(NEW_BYTES.size, NEW_ETAG))
            server.enqueue(MockResponse().setResponseCode(503))

            now += ROUTING_TILE_CHECK_INTERVAL_MS
            val result = store.refresh(TILE)

            assertEquals(TileRefreshResult.NETWORK_FAILED, result)
            assertContentEquals(TILE_BYTES, tileFile().readBytes())
        }

    @Test
    fun `a tile that is not installed is not refreshed`() =
        runTest {
            assertEquals(TileRefreshResult.NOT_INSTALLED, store().refresh(TILE))
            assertEquals(0, server.requestCount)
        }

    // --- delete -----------------------------------------------------------------

    @Test
    fun `a deleted tile is missing again`() =
        runTest {
            val store = installed()

            store.delete(TILE)

            assertEquals(emptyList(), store.installedTiles())
            assertEquals(listOf(TILE), store.missingTiles(listOf(TILE)))
        }

    @Test
    fun `deleting everything removes tiles, records and parts`() =
        runTest {
            val store = interruptedAfterFiveBytes()
            server.enqueue(MockResponse().setResponseCode(404))
            store.fetch(OTHER_TILE)

            store.deleteAll()

            assertEquals(emptyList(), routingDir().list().orEmpty().toList())
            assertEquals(listOf(TILE, OTHER_TILE), store.missingTiles(listOf(TILE, OTHER_TILE)))
        }

    private fun TestScope.store(): RoutingTileStore =
        RoutingTileStore(
            root = routingDir(),
            feed = RegionFeedApi(regionHttpClient(), USER_AGENT, hostPrefix = server.url("/").toString()),
            hostPrefix = server.url("/").toString(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            clock = { now },
        )

    private suspend fun TestScope.installed(): RoutingTileStore {
        server.enqueue(headResponse(TILE_BYTES.size))
        server.enqueue(fileResponse(TILE_BYTES))
        return store().also { assertEquals(TileFetchResult.INSTALLED, it.fetch(TILE)) }
    }

    // A first download whose body ends after five of its ten bytes.
    private suspend fun TestScope.interruptedAfterFiveBytes(): RoutingTileStore {
        server.enqueue(headResponse(TILE_BYTES.size))
        server.enqueue(
            fileResponse(TILE_BYTES.copyOf(5))
                .setHeader("Content-Length", TILE_BYTES.size)
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
        )
        return store().also { assertEquals(TileFetchResult.NETWORK_FAILED, it.fetch(TILE)) }
    }

    private fun routingDir(): File = File(tempFolder.root, "routing")

    private fun segmentDir(): File = File(routingDir(), "segments")

    private fun tileFile(): File = File(segmentDir(), "$TILE.rd5")

    private fun partFile(): File = File(routingDir(), "downloads/$TILE.rd5.part")

    private fun headResponse(
        size: Int,
        etag: String = ETAG,
    ): MockResponse =
        MockResponse()
            .setHeader("Content-Length", size)
            .setHeader("ETag", etag)
            .setHeader("Last-Modified", MODIFIED_HEADER)

    private fun fileResponse(
        bytes: ByteArray,
        etag: String = ETAG,
    ): MockResponse =
        MockResponse()
            .setBody(Buffer().write(bytes))
            .setHeader("ETag", etag)
            .setHeader("Last-Modified", MODIFIED_HEADER)

    private fun rangeResponse(
        contentRange: String,
        bytes: ByteArray,
        etag: String,
    ): MockResponse =
        MockResponse()
            .setResponseCode(206)
            .setBody(Buffer().write(bytes))
            .setHeader("ETag", etag)
            .setHeader("Content-Range", contentRange)

    private companion object {
        const val TILE = "E130_N30"
        const val OTHER_TILE = "E160_N30"
        const val USER_AGENT = "FemtoCarLauncher/test (+https://example.test)"
        const val ETAG = "\"6b023f45-a\""
        const val NEW_ETAG = "\"6b0b79c5-a\""
        const val MODIFIED_HEADER = "Sun, 22 Nov 2026 01:03:01 GMT"
        val MODIFIED_EPOCH_MS = Instant.parse("2026-11-22T01:03:01Z").toEpochMilli()
        val START_EPOCH_MS = Instant.parse("2026-11-22T09:00:00Z").toEpochMilli()
        val TILE_BYTES = "0123456789".toByteArray()
        val NEW_BYTES = "abcdefghij".toByteArray()
    }
}
