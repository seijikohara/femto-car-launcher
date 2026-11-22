package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.data.common.download.FREE_SPACE_MARGIN_BYTES
import io.github.seijikohara.femto.data.common.download.ProbeResult
import io.github.seijikohara.femto.data.common.download.RemoteFile
import io.github.seijikohara.femto.data.common.download.RemoteFileApi
import io.github.seijikohara.femto.data.common.download.RemoteFileFeed
import io.github.seijikohara.femto.data.common.download.TransferResult
import io.github.seijikohara.femto.data.common.download.downloadHttpClient
import io.github.seijikohara.femto.testfixtures.FakeDownloadStop
import io.github.seijikohara.femto.testfixtures.FakeRemoteFileFeed
import io.github.seijikohara.femto.testfixtures.digitBytes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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
import kotlin.test.assertTrue

/**
 * The routing tile store against a real transport and a mock brouter.de. The tests that hold a download midway, to see
 * what other operations do meanwhile, use the fake transport and drive the store with `runCurrent`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutingTileStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    private var now = START_EPOCH_MS

    private var freeBytes = PLENTY_OF_SPACE_BYTES

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

    // A rebuild between the check and the download: the check sees a new file, the download finds none.
    @Test
    fun `a tile gone by the time of its replacement stays installed`() =
        runTest {
            val store = installed()
            server.enqueue(headResponse(NEW_BYTES.size, NEW_ETAG))
            server.enqueue(MockResponse().setResponseCode(404))

            now += ROUTING_TILE_CHECK_INTERVAL_MS
            val result = store.refresh(TILE)

            assertEquals(TileRefreshResult.UNCHANGED, result)
            assertContentEquals(TILE_BYTES, tileFile().readBytes())
            assertEquals(MODIFIED_EPOCH_MS, store.installedTiles().single().dataDateEpochMs)
            assertFalse(partFile().exists() || pendingFile().exists())
        }

    @Test
    fun `a tile gone by the time of its replacement is not checked again for a week`() =
        runTest {
            val store = installed()
            server.enqueue(headResponse(NEW_BYTES.size, NEW_ETAG))
            server.enqueue(MockResponse().setResponseCode(404))
            now += ROUTING_TILE_CHECK_INTERVAL_MS
            store.refresh(TILE)

            now += ROUTING_TILE_CHECK_INTERVAL_MS - 1
            val result = store.refresh(TILE)

            assertEquals(TileRefreshResult.NOT_DUE, result)
            assertEquals(4, server.requestCount)
        }

    // --- check ------------------------------------------------------------------

    @Test
    fun `a check within a week asks nothing`() =
        runTest {
            val store = installed()

            now += ROUTING_TILE_CHECK_INTERVAL_MS - 1
            val result = store.check(TILE)

            assertEquals(TileCheckResult.NotDue, result)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a due check of an unchanged tile asks with the etag and counts`() =
        runTest {
            val store = installed()
            server.enqueue(MockResponse().setResponseCode(304))

            now += ROUTING_TILE_CHECK_INTERVAL_MS
            val result = store.check(TILE)

            assertEquals(TileCheckResult.Unchanged, result)
            val check = List(3) { server.takeRequest() }.last()
            assertEquals("HEAD", check.method)
            assertEquals(ETAG, check.getHeader("If-None-Match"))
            assertEquals(now, store.installedTiles().single().checkedAtEpochMs)
        }

    @Test
    fun `a due check of a rebuilt tile reports its size and downloads nothing`() =
        runTest {
            val store = installed()
            server.enqueue(headResponse(NEW_BYTES.size, NEW_ETAG))
            val checkedBefore = store.installedTiles().single().checkedAtEpochMs

            now += ROUTING_TILE_CHECK_INTERVAL_MS
            val result = store.check(TILE)

            assertEquals(TileCheckResult.Changed(NEW_BYTES.size.toLong()), result)
            assertEquals(3, server.requestCount)
            assertContentEquals(TILE_BYTES, tileFile().readBytes())
            assertEquals(checkedBefore, store.installedTiles().single().checkedAtEpochMs)
        }

    @Test
    fun `a rebuilt tile found by a check is replaced by the refresh that follows`() =
        runTest {
            val store = installed()
            server.enqueue(headResponse(NEW_BYTES.size, NEW_ETAG))
            server.enqueue(headResponse(NEW_BYTES.size, NEW_ETAG))
            server.enqueue(fileResponse(NEW_BYTES, NEW_ETAG))
            now += ROUTING_TILE_CHECK_INTERVAL_MS
            store.check(TILE)

            val result = store.refresh(TILE)

            assertEquals(TileRefreshResult.UPDATED, result)
            assertContentEquals(NEW_BYTES, tileFile().readBytes())
        }

    @Test
    fun `a check without an answer keeps the tile and does not count`() =
        runTest {
            val store = installed()
            server.enqueue(MockResponse().setResponseCode(503))
            val checkedBefore = store.installedTiles().single().checkedAtEpochMs

            now += ROUTING_TILE_CHECK_INTERVAL_MS
            val result = store.check(TILE)

            assertEquals(TileCheckResult.Unavailable, result)
            assertEquals(checkedBefore, store.installedTiles().single().checkedAtEpochMs)
        }

    @Test
    fun `a due tile marked checked counts as checked now without a request`() =
        runTest {
            val store = installed()

            now += ROUTING_TILE_CHECK_INTERVAL_MS
            val marked = store.markChecked(TILE)

            assertTrue(marked)
            assertEquals(2, server.requestCount)
            assertEquals(now, store.installedTiles().single().checkedAtEpochMs)
            assertEquals(TileCheckResult.NotDue, store.check(TILE))
        }

    @Test
    fun `a tile not due or not installed is not marked`() =
        runTest {
            val store = installed()
            val checkedBefore = store.installedTiles().single().checkedAtEpochMs

            now += 1
            assertFalse(store.markChecked(TILE))
            assertFalse(store.markChecked(OTHER_TILE))
            assertEquals(checkedBefore, store.installedTiles().single().checkedAtEpochMs)
        }

    @Test
    fun `a tile that is not installed is not checked`() =
        runTest {
            assertEquals(TileCheckResult.NotInstalled, store().check(TILE))
            assertEquals(0, server.requestCount)
        }

    // --- installed --------------------------------------------------------------

    @Test
    fun `the installed tiles are read again after a download and a delete`() =
        runTest {
            server.enqueue(headResponse(TILE_BYTES.size))
            server.enqueue(fileResponse(TILE_BYTES))
            val store = store()
            val seen = mutableListOf<List<String>>()
            val collector =
                backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
                    store.installed.collect { tiles -> seen += tiles.map { it.name } }
                }
            runCurrent()

            store.fetch(TILE)
            runCurrent()
            store.delete(TILE)
            runCurrent()
            collector.cancel()

            // Consecutive repeats folded: a read can follow every operation, a look that changes nothing included.
            val changes = seen.fold(emptyList<List<String>>()) { acc, tiles ->
                if (acc.lastOrNull() ==
                    tiles
                ) {
                    acc
                } else {
                    acc + listOf(tiles)
                }
            }
            assertEquals(listOf(emptyList(), listOf(TILE), emptyList()), changes)
        }

    // --- free space -------------------------------------------------------------

    @Test
    fun `a tile that would not leave the free space margin is not downloaded`() =
        runTest {
            server.enqueue(headResponse(TILE_BYTES.size))
            freeBytes = FREE_SPACE_MARGIN_BYTES + TILE_BYTES.size - 1

            val result = store().fetch(TILE)

            assertEquals(TileFetchResult.NO_SPACE, result)
            assertEquals(1, server.requestCount)
            assertFalse(partFile().exists() || tileFile().exists())
        }

    @Test
    fun `a resume needs room only for the rest of the tile`() =
        runTest {
            val store = interruptedAfterFiveBytes()
            server.enqueue(headResponse(TILE_BYTES.size))
            server.enqueue(rangeResponse("bytes 5-9/10", TILE_BYTES.copyOfRange(5, 10), ETAG))
            freeBytes = FREE_SPACE_MARGIN_BYTES + 5

            val result = store.fetch(TILE)

            assertEquals(TileFetchResult.INSTALLED, result)
        }

    @Test
    fun `a resume without room for the rest keeps its part`() =
        runTest {
            val store = interruptedAfterFiveBytes()
            server.enqueue(headResponse(TILE_BYTES.size))
            freeBytes = FREE_SPACE_MARGIN_BYTES + 4

            val result = store.fetch(TILE)

            assertEquals(TileFetchResult.NO_SPACE, result)
            assertEquals(3, server.requestCount)
            assertContentEquals(TILE_BYTES.copyOf(5), partFile().readBytes())
        }

    @Test
    fun `a larger file sent instead that would not fit leaves nothing behind`() =
        runTest {
            server.enqueue(headResponse(TILE_BYTES.size))
            server.enqueue(fileResponse(LARGER_BYTES, NEW_ETAG))
            freeBytes = FREE_SPACE_MARGIN_BYTES + TILE_BYTES.size

            val result = store().fetch(TILE)

            assertEquals(TileFetchResult.NO_SPACE, result)
            assertFalse(partFile().exists() || pendingFile().exists() || tileFile().exists())
        }

    @Test
    fun `a part that could not be written is discarded with its record`() =
        runTest {
            val feed = fakeFeed(TILE_FILE).apply { stop = FakeDownloadStop(5, TransferResult.StorageFailed) }

            val result = store(feed).fetch(TILE)

            assertEquals(TileFetchResult.STORAGE_FAILED, result)
            assertFalse(partFile().exists() || pendingFile().exists())
        }

    @Test
    fun `what a running download still has to write counts as taken`() =
        runTest {
            // The fake grows a part halfway and holds it at the gate: the first tile then has 30 of 60 bytes to go.
            val feed = fakeFeed(RemoteFile(60, ETAG, null), RemoteFile(80, ETAG, null))
            val gate = feed.gateDownload()
            freeBytes = FREE_SPACE_MARGIN_BYTES + 100
            val store = store(feed)

            val first = async { store.fetch(TILE) }
            runCurrent()
            val second = async { store.fetch(OTHER_TILE) }
            runCurrent()
            gate.complete(Unit)

            assertEquals(listOf(TileFetchResult.INSTALLED, TileFetchResult.NO_SPACE), awaitAll(first, second))
        }

    // --- concurrency ------------------------------------------------------------

    @Test
    fun `a running download holds up no operation on another tile`() =
        runTest {
            val feed = fakeFeed(TILE_FILE)
            val gate = feed.gateDownload()
            File(segmentDir(), "$OTHER_TILE.rd5").apply { parentFile?.mkdirs() }.writeBytes(TILE_BYTES)
            val store = store(feed)

            val download = async { store.fetch(TILE) }
            runCurrent()
            val other = async { store.fetch(OTHER_TILE).also { store.delete(OTHER_TILE) } }
            runCurrent()

            assertTrue(other.isCompleted && !download.isCompleted)
            gate.complete(Unit)
            assertEquals(listOf(TileFetchResult.INSTALLED, TileFetchResult.INSTALLED), awaitAll(other, download))
        }

    @Test
    fun `a delete waits for the download of the same tile`() =
        runTest {
            val feed = fakeFeed(TILE_FILE)
            val gate = feed.gateDownload()
            val store = store(feed)

            val download = async { store.fetch(TILE) }
            runCurrent()
            val deletion = launch { store.delete(TILE) }
            runCurrent()

            assertFalse(deletion.isCompleted)
            gate.complete(Unit)
            assertEquals(TileFetchResult.INSTALLED, download.await())
            deletion.join()
            assertFalse(tileFile().exists())
        }

    @Test
    fun `deleting everything waits for a running download`() =
        runTest {
            val feed = fakeFeed(TILE_FILE)
            val gate = feed.gateDownload()
            val store = store(feed)

            val download = async { store.fetch(TILE) }
            runCurrent()
            val deletion = launch { store.deleteAll() }
            runCurrent()

            assertFalse(deletion.isCompleted)
            gate.complete(Unit)
            assertEquals(TileFetchResult.INSTALLED, download.await())
            deletion.join()
            assertEquals(emptyList(), routingDir().list().orEmpty().toList())
        }

    // --- cancellation -----------------------------------------------------------

    @Test
    fun `a cancelled download keeps its part and record, and the next fetch resumes`() =
        runTest {
            val body = digitBytes(4 * MIB)
            server.enqueue(headResponse(body.size))
            server.enqueue(fileResponse(body))
            lateinit var fetch: Job
            fetch = launch { store(cancellingOnFirstProgress { fetch.cancel() }).fetch(TILE) }
            fetch.join()
            val held = partFile().length().toInt()
            assertTrue(fetch.isCancelled && held in 1 until body.size && pendingFile().isFile, "held $held bytes")
            server.enqueue(headResponse(body.size))
            val rest = body.copyOfRange(held, body.size)
            server.enqueue(rangeResponse("bytes $held-${body.lastIndex}/${body.size}", rest, ETAG))

            val result = store().fetch(TILE)

            assertEquals(TileFetchResult.INSTALLED, result)
            assertContentEquals(body, tileFile().readBytes())
            assertEquals("bytes=$held-", List(4) { server.takeRequest() }.last().getHeader("Range"))
        }

    // --- download size and progress ---------------------------------------------

    @Test
    fun `the download size adds up what the server announces for each missing tile`() =
        runTest {
            val store = store(fakeFeed(TILE_FILE, TILE_FILE.copy(sizeBytes = 32)))

            assertEquals(TILE_BYTES.size + 32L, store.downloadSizeBytes(listOf(TILE, OTHER_TILE)))
        }

    @Test
    fun `an installed tile adds nothing to the download size and is not asked about`() =
        runTest {
            val store = installed()
            val feed = fakeFeed(TILE_FILE, TILE_FILE.copy(sizeBytes = 32))

            val size = store(feed).downloadSizeBytes(listOf(TILE, OTHER_TILE))

            assertEquals(32L, size)
            assertEquals(listOf("${hostPrefix()}$OTHER_TILE.rd5"), feed.probedUrls)
        }

    @Test
    fun `a tile the server has none for adds nothing to the download size`() =
        runTest {
            val feed = fakeFeed(TILE_FILE).apply { probeAnswers["${hostPrefix()}$OTHER_TILE.rd5"] = ProbeResult.Gone }

            assertEquals(TILE_BYTES.size.toLong(), store(feed).downloadSizeBytes(listOf(TILE, OTHER_TILE)))
        }

    @Test
    fun `the download size is unknown when the server cannot say for one tile`() =
        runTest {
            // The fake transport answers Unavailable for OTHER_TILE, which it has no answer for.
            assertNull(store(fakeFeed(TILE_FILE)).downloadSizeBytes(listOf(TILE, OTHER_TILE)))
        }

    @Test
    fun `a fetch reports its progress against the file's size`() =
        runTest {
            val store = store(fakeFeed(TILE_FILE))
            val reports = mutableListOf<Pair<Long, Long>>()

            store.fetch(TILE) { receivedBytes, totalBytes -> reports += receivedBytes to totalBytes }

            assertEquals(listOf(5L to 10L, 10L to 10L), reports)
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

    private fun TestScope.store(feed: RemoteFileFeed = transport()): RoutingTileStore =
        RoutingTileStore(
            root = routingDir(),
            feed = feed,
            freeBytes = { freeBytes },
            hostPrefix = hostPrefix(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            clock = { now },
        )

    private fun transport(): RemoteFileApi = RemoteFileApi(downloadHttpClient(), USER_AGENT, hostPrefix())

    // The real transport, cancelled from its first progress report, which comes once a full step is on disk.
    private fun cancellingOnFirstProgress(cancel: () -> Unit): RemoteFileFeed =
        transport().let { api ->
            object : RemoteFileFeed by api {
                override suspend fun download(
                    url: String,
                    part: File,
                    expected: RemoteFile,
                    onRestart: suspend (RemoteFile) -> Boolean,
                    onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
                ): TransferResult =
                    api.download(url, part, expected, onRestart) { receivedBytes, totalBytes ->
                        onProgress(receivedBytes, totalBytes)
                        cancel()
                    }
            }
        }

    // A fake brouter.de that has [files] for TILE and OTHER_TILE, in that order.
    private fun fakeFeed(vararg files: RemoteFile): FakeRemoteFileFeed =
        FakeRemoteFileFeed().apply {
            listOf(TILE, OTHER_TILE).zip(files).forEach { (name, file) ->
                probeAnswers["${hostPrefix()}$name.rd5"] = ProbeResult.Found(file)
            }
        }

    private fun hostPrefix(): String = server.url("/").toString()

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

    private fun pendingFile(): File = File(routingDir(), "downloads/$TILE.json")

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
        val LARGER_BYTES = "abcdefghijkl".toByteArray()
        val TILE_FILE = RemoteFile(TILE_BYTES.size.toLong(), ETAG, null)
        const val MIB = 1024 * 1024
        const val PLENTY_OF_SPACE_BYTES = 1_000_000_000_000L
    }
}
