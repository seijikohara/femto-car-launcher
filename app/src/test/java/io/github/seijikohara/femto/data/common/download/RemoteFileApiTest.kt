package io.github.seijikohara.femto.data.common.download

import io.github.seijikohara.femto.testfixtures.digitBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import kotlin.test.fail

class RemoteFileApiTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- download -----------------------------------------------------------

    @Test
    fun `a fresh download writes the whole file and reports it complete`() =
        runTest {
            server.enqueue(fileResponse(FILE_BYTES))
            val part = part()

            val result = downloadInto(part)

            assertEquals(TransferResult.Complete(FILE), result)
            assertContentEquals(FILE_BYTES, part.readBytes())
            assertNull(server.takeRequest().getHeader("Range"))
        }

    @Test
    fun `every request names the app and asks for the identity encoding`() =
        runTest {
            server.enqueue(MockResponse().setHeader("Content-Length", FILE_BYTES.size))
            server.enqueue(fileResponse(FILE_BYTES))
            server.enqueue(MockResponse().setResponseCode(304))
            val api = newApi()

            api.probe(fileUrl())
            api.download(fileUrl(), part(), FILE, onRestart = { true }, onProgress = { _, _ -> })
            api.revalidate(fileUrl(), FILE)

            val requests = List(3) { server.takeRequest() }
            assertEquals(listOf("HEAD", "GET", "HEAD"), requests.map { it.method })
            assertEquals(List(3) { USER_AGENT }, requests.map { it.getHeader("User-Agent") })
            assertEquals(List(3) { "identity" }, requests.map { it.getHeader("Accept-Encoding") })
        }

    @Test
    fun `a resume asks for the missing bytes with the saved etag and appends them`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(rangeResponse("bytes 5-9/10", FILE_BYTES.copyOfRange(5, 10)))

            val result = downloadInto(part)

            val request = server.takeRequest()
            assertEquals("bytes=5-", request.getHeader("Range"))
            assertEquals(ETAG, request.getHeader("If-Range"))
            assertEquals(TransferResult.Complete(FILE), result)
            assertContentEquals(FILE_BYTES, part.readBytes())
        }

    @Test
    fun `a resume sends the etag back exactly as the server sent it`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(rangeResponse("bytes 5-9/10", FILE_BYTES.copyOfRange(5, 10), etag = WEAK_ETAG))

            downloadInto(part, expected = FILE.copy(etag = WEAK_ETAG))

            assertEquals(WEAK_ETAG, server.takeRequest().getHeader("If-Range"))
        }

    @Test
    fun `a resume with an unsendable etag asks for the whole file instead`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(fileResponse(FILE_BYTES))
            val restarts = mutableListOf<RemoteFile>()

            val result =
                downloadInto(
                    part,
                    expected = FILE.copy(etag = UNSENDABLE_ETAG),
                    onRestart = { file ->
                        restarts += file
                        true
                    },
                )

            val request = server.takeRequest()
            assertNull(request.getHeader("Range"))
            assertNull(request.getHeader("If-Range"))
            assertEquals(listOf(FILE), restarts)
            assertEquals(TransferResult.Complete(FILE), result)
            assertContentEquals(FILE_BYTES, part.readBytes())
        }

    @Test
    fun `a full answer to a resume restarts from zero when the caller agrees`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(fileResponse(NEW_BYTES, etag = NEW_ETAG))
            val restarts = mutableListOf<RemoteFile>()

            val result =
                downloadInto(
                    part,
                    onRestart = { file ->
                        restarts += file
                        true
                    },
                )

            assertEquals(listOf(NEW_FILE), restarts)
            assertEquals(TransferResult.Complete(NEW_FILE), result)
            assertContentEquals(NEW_BYTES, part.readBytes())
        }

    @Test
    fun `a full answer to a resume is refused when the caller declines and the part is deleted`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(fileResponse(NEW_BYTES, etag = NEW_ETAG))

            val result = downloadInto(part, onRestart = { false })

            assertEquals(TransferResult.Refused(NEW_FILE), result)
            assertFalse(part.exists())
        }

    @Test
    fun `a changed size on the first request asks the caller before writing`() =
        runTest {
            val part = part()
            val body = digitBytes(12)
            server.enqueue(fileResponse(body))
            var partExistedAtRestart: Boolean? = null

            val result =
                downloadInto(
                    part,
                    onRestart = {
                        partExistedAtRestart = part.exists()
                        true
                    },
                )

            assertEquals(false, partExistedAtRestart)
            assertEquals(TransferResult.Complete(RemoteFile(12, ETAG, null)), result)
            assertContentEquals(body, part.readBytes())
        }

    @Test
    fun `a changed etag on the first request is refused when the caller declines`() =
        runTest {
            val part = part()
            server.enqueue(fileResponse(FILE_BYTES, etag = NEW_ETAG))

            val result = downloadInto(part, onRestart = { false })

            assertEquals(TransferResult.Refused(RemoteFile(10, NEW_ETAG, null)), result)
            assertFalse(part.exists())
        }

    @Test
    fun `a part without an etag is discarded and the whole file is asked for`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            val untagged = RemoteFile(10, etag = null, lastModifiedEpochMs = null)
            server.enqueue(fileResponse(FILE_BYTES, etag = null))
            val restarts = mutableListOf<RemoteFile>()

            val result =
                downloadInto(
                    part,
                    expected = untagged,
                    onRestart = { file ->
                        restarts += file
                        true
                    },
                )

            val request = server.takeRequest()
            assertNull(request.getHeader("Range"))
            assertNull(request.getHeader("If-Range"))
            assertEquals(listOf(untagged), restarts)
            assertEquals(TransferResult.Complete(untagged), result)
            assertContentEquals(FILE_BYTES, part.readBytes())
        }

    @Test
    fun `a full answer without a length is not kept`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(MockResponse().setChunkedBody(Buffer().write(FILE_BYTES), 4).setHeader("ETag", ETAG))

            val result = downloadInto(part)

            assertEquals(TransferResult.Unavailable, result)
            assertFalse(part.exists())
        }

    @Test
    fun `a range that starts elsewhere than the part is not appended`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(rangeResponse("bytes 0-9/10", FILE_BYTES))

            val result = downloadInto(part)

            assertEquals(TransferResult.Unavailable, result)
            assertFalse(part.exists())
        }

    @Test
    fun `a range of another total is not appended`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(rangeResponse("bytes 5-9/12", FILE_BYTES.copyOfRange(5, 10)))

            val result = downloadInto(part)

            assertEquals(TransferResult.Unavailable, result)
            assertFalse(part.exists())
        }

    @Test
    fun `a range of another version is not appended`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            // A caching proxy that holds two versions of the file answers with a range of the other one.
            server.enqueue(rangeResponse("bytes 5-9/10", FILE_BYTES.copyOfRange(5, 10), etag = OTHER_VERSION_ETAG))

            val result = downloadInto(part)

            assertEquals(ETAG, server.takeRequest().getHeader("If-Range"))
            assertEquals(TransferResult.Unavailable, result)
            assertFalse(part.exists())
        }

    @Test
    fun `a range without an etag is judged by its range alone`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(rangeResponse("bytes 5-9/10", FILE_BYTES.copyOfRange(5, 10), etag = null))

            val result = downloadInto(part)

            assertEquals(TransferResult.Complete(FILE), result)
            assertContentEquals(FILE_BYTES, part.readBytes())
        }

    @Test
    fun `a range that does not parse is not appended`() =
        runTest {
            val outcomes =
                UNREADABLE_RANGES.map { contentRange ->
                    val part = partHolding(FILE_BYTES.copyOf(5))
                    server.enqueue(rangeResponse(contentRange, FILE_BYTES.copyOfRange(5, 10)))
                    downloadInto(part) to part.exists()
                }

            assertEquals(List(UNREADABLE_RANGES.size) { TransferResult.Unavailable to false }, outcomes)
        }

    @Test
    fun `a content range above two gibibytes is read exactly`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(rangeResponse("bytes 5-9/$LARGE_SIZE", FILE_BYTES.copyOfRange(5, 10)))
            val progress = mutableListOf<Pair<Long, Long>>()

            val result =
                downloadInto(
                    part,
                    expected = RemoteFile(LARGE_SIZE, ETAG, null),
                    onProgress = { received, total -> progress += received to total },
                )

            assertEquals(TransferResult.Interrupted, result)
            assertContentEquals(FILE_BYTES, part.readBytes())
            assertEquals(listOf(10L to LARGE_SIZE), progress)
        }

    @Test
    fun `a range the server cannot satisfy deletes the part`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(MockResponse().setResponseCode(416))

            val result = downloadInto(part)

            assertEquals(TransferResult.Unavailable, result)
            assertFalse(part.exists())
        }

    @Test
    fun `a dropped connection keeps the part and reports an interruption`() =
        runTest {
            val body = digitBytes(100_000)
            val part = partHolding(body.copyOf(50_000))
            server.enqueue(
                rangeResponse("bytes 50000-99999/100000", body.copyOfRange(50_000, 100_000))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )

            val result = downloadInto(part, expected = RemoteFile(100_000, ETAG, null))

            assertEquals(TransferResult.Interrupted, result)
            val kept = part.readBytes()
            assertTrue(kept.size in 50_000 until 100_000, "kept ${kept.size} bytes")
            assertContentEquals(body.copyOf(kept.size), kept)
        }

    @Test
    fun `a body shorter than announced is an interruption`() =
        runTest {
            val part = part()
            server.enqueue(
                fileResponse(FILE_BYTES.copyOf(5))
                    .setHeader("Content-Length", FILE_BYTES.size)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
            )

            val result = downloadInto(part)

            assertEquals(TransferResult.Interrupted, result)
            assertContentEquals(FILE_BYTES.copyOf(5), part.readBytes())
        }

    @Test
    fun `a connection that cannot be made keeps the part`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            val api = newApi()
            val url = fileUrl()
            server.shutdown()

            val result =
                api.download(
                    url,
                    part,
                    FILE,
                    onRestart = { fail("no answer, no restart") },
                    onProgress = { _, _ -> },
                )

            assertEquals(TransferResult.Interrupted, result)
            assertContentEquals(FILE_BYTES.copyOf(5), part.readBytes())
        }

    @Test
    fun `a part that is already complete is reported without a request`() =
        runTest {
            val part = partHolding(FILE_BYTES)

            val result = downloadInto(part)

            assertEquals(TransferResult.Complete(FILE), result)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `a missing file is gone and its part is deleted`() =
        runTest {
            val outcomes =
                GONE_CODES.map { code ->
                    val part = partHolding(FILE_BYTES.copyOf(5))
                    server.enqueue(MockResponse().setResponseCode(code))
                    downloadInto(part) to part.exists()
                }

            assertEquals(List(GONE_CODES.size) { TransferResult.Gone to false }, outcomes)
        }

    @Test
    fun `a redirect is not followed and keeps the part`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/elsewhere/monaco.mbtiles"))
            server.enqueue(fileResponse(FILE_BYTES))

            val result = downloadInto(part)

            assertEquals(TransferResult.Unavailable, result)
            assertEquals(1, server.requestCount)
            assertContentEquals(FILE_BYTES.copyOf(5), part.readBytes())
        }

    @Test
    fun `a server error keeps the part`() =
        runTest {
            val outcomes =
                PART_KEEPING_CODES.map { code ->
                    val part = partHolding(FILE_BYTES.copyOf(5))
                    server.enqueue(MockResponse().setResponseCode(code))
                    downloadInto(part) to part.readBytes().toList()
                }

            val kept = TransferResult.Unavailable to FILE_BYTES.copyOf(5).toList()
            assertEquals(List(PART_KEEPING_CODES.size) { kept }, outcomes)
        }

    @Test
    fun `a url on another host is refused without a request and keeps the part`() =
        runTest {
            val part = partHolding(FILE_BYTES.copyOf(5))
            val api = newApi()

            val probe = api.probe("$OTHER_HOST/europe/monaco.mbtiles")
            val transfer =
                api.download(
                    "$OTHER_HOST/europe/monaco.mbtiles",
                    part,
                    FILE,
                    onRestart = { fail("no request, no restart") },
                    onProgress = { _, _ -> },
                )
            val check = api.revalidate("$OTHER_HOST/europe/monaco.mbtiles", FILE)

            assertEquals(
                Triple(ProbeResult.Unavailable, TransferResult.Unavailable, RevalidationResult.Unavailable),
                Triple(probe, transfer, check),
            )
            assertEquals(0, server.requestCount)
            assertContentEquals(FILE_BYTES.copyOf(5), part.readBytes())
        }

    @Test
    fun `cancellation leaves the part in place`() =
        runTest {
            val body = digitBytes(4 * MIB)
            server.enqueue(fileResponse(body))
            val part = part()
            val api = newApi()
            val url = fileUrl()
            lateinit var job: Job

            job =
                launch {
                    api.download(
                        url,
                        part,
                        RemoteFile(body.size.toLong(), ETAG, null),
                        onRestart = { fail("same file, no restart") },
                        onProgress = { _, _ -> job.cancel() },
                    )
                }
            job.join()

            assertTrue(job.isCancelled)
            val kept = part.readBytes()
            assertTrue(kept.isNotEmpty() && kept.size < body.size, "kept ${kept.size} bytes")
            assertContentEquals(body.copyOf(kept.size), kept)
        }

    @Test
    fun `progress is reported in bytes and ends at the total`() =
        runTest {
            val body = digitBytes(3 * MIB + 12_345)
            val size = body.size.toLong()
            server.enqueue(fileResponse(body))
            val progress = mutableListOf<Pair<Long, Long>>()

            downloadInto(
                part(),
                expected = RemoteFile(size, ETAG, null),
                onProgress = { received, total -> progress += received to total },
            )

            assertEquals(size to size, progress.last())
            assertTrue(progress.all { (_, total) -> total == size })
            // Each report but the last waits for a full step; the last closes the transfer.
            val received = listOf(0L) + progress.map { it.first }
            val steps = received.zipWithNext { previous, next -> next - previous }
            assertTrue(steps.dropLast(1).all { it >= DOWNLOAD_PROGRESS_STEP_BYTES }, "$received")
            assertTrue(steps.last() > 0, "$received")
        }

    @Test
    fun `an unwritable part reports a storage failure`() =
        runTest {
            server.enqueue(fileResponse(FILE_BYTES))
            // A plain file where the part's directory should be: nothing can be written under it.
            val part = File(tempFolder.newFile("occupied"), "monaco.mbtiles.part")

            val result = downloadInto(part)

            assertEquals(TransferResult.StorageFailed, result)
        }

    // --- probe --------------------------------------------------------------

    @Test
    fun `a probe reads size etag and date`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Length", LARGE_SIZE)
                    .setHeader("ETag", ETAG)
                    .setHeader("Last-Modified", MODIFIED_HEADER),
            )

            val result = newApi().probe(fileUrl())

            assertEquals(ProbeResult.Found(RemoteFile(LARGE_SIZE, ETAG, MODIFIED_EPOCH_MS)), result)
            assertEquals("HEAD", server.takeRequest().method)
        }

    @Test
    fun `a probe with an unreadable date has no date`() =
        runTest {
            server.enqueue(MockResponse().setHeader("Content-Length", 10).setHeader("Last-Modified", "yesterday"))

            val result = newApi().probe(fileUrl())

            assertEquals(ProbeResult.Found(RemoteFile(10, etag = null, lastModifiedEpochMs = null)), result)
        }

    @Test
    fun `an etag that could not be sent back is read as null`() =
        runTest {
            // Lenient: OkHttp reads such a value but refuses to send it in a request header.
            server.enqueue(MockResponse().setHeader("Content-Length", 10).addHeaderLenient("ETag", UNSENDABLE_ETAG))

            val probe = newApi().probe(fileUrl())

            assertEquals(ProbeResult.Found(RemoteFile(10, etag = null, lastModifiedEpochMs = null)), probe)
        }

    @Test
    fun `a probe without a length is size unknown`() =
        runTest {
            server.enqueue(MockResponse().removeHeader("Content-Length"))
            server.enqueue(MockResponse().setHeader("Content-Length", 0))
            val api = newApi()

            val results = List(2) { api.probe(fileUrl()) }

            assertEquals(List(2) { ProbeResult.SizeUnknown }, results)
        }

    @Test
    fun `a probe of a missing file is gone`() =
        runTest {
            GONE_CODES.forEach { server.enqueue(MockResponse().setResponseCode(it)) }
            val api = newApi()

            val results = GONE_CODES.map { api.probe(fileUrl()) }

            assertEquals(List(GONE_CODES.size) { ProbeResult.Gone }, results)
        }

    @Test
    fun `a probe that is redirected or refused is unavailable`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/elsewhere/monaco.mbtiles"))
            server.enqueue(MockResponse().setResponseCode(500))
            val api = newApi()

            val results = List(2) { api.probe(fileUrl()) }

            assertEquals(List(2) { ProbeResult.Unavailable }, results)
            assertEquals(2, server.requestCount)
        }

    // --- revalidate ---------------------------------------------------------

    @Test
    fun `a revalidation sends the kept etag in a conditional look`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(304))

            val result = newApi().revalidate(fileUrl(), FILE)

            assertEquals(RevalidationResult.NotModified, result)
            val request = server.takeRequest()
            assertEquals("HEAD", request.method)
            assertEquals(ETAG, request.getHeader("If-None-Match"))
        }

    @Test
    fun `a revalidation answered with the kept file is not modified`() =
        runTest {
            // A server that ignores If-None-Match, and a kept file whose ETag cannot be sent back.
            server.enqueue(MockResponse().setHeader("Content-Length", 10).setHeader("ETag", ETAG))
            server.enqueue(MockResponse().setHeader("Content-Length", 10))
            val api = newApi()

            val results =
                listOf(
                    api.revalidate(fileUrl(), FILE),
                    api.revalidate(fileUrl(), FILE.copy(etag = null)),
                )

            assertEquals(List(2) { RevalidationResult.NotModified }, results)
            assertNull(List(2) { server.takeRequest() }.last().getHeader("If-None-Match"))
        }

    @Test
    fun `a revalidation answered with another file reports it`() =
        runTest {
            server.enqueue(MockResponse().setHeader("Content-Length", 12).setHeader("ETag", NEW_ETAG))

            val result = newApi().revalidate(fileUrl(), FILE)

            assertEquals(RevalidationResult.Changed(NEW_FILE), result)
        }

    @Test
    fun `a revalidation of a missing file is gone`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))

            assertEquals(RevalidationResult.Gone, newApi().revalidate(fileUrl(), FILE))
        }

    private fun newApi(): RemoteFileApi =
        RemoteFileApi(
            client = downloadHttpClient(),
            userAgent = USER_AGENT,
            hostPrefix = server.url("/").toString(),
        )

    private suspend fun downloadInto(
        part: File,
        expected: RemoteFile = FILE,
        onRestart: suspend (RemoteFile) -> Boolean = { fail("unexpected restart to $it") },
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): TransferResult = newApi().download(fileUrl(), part, expected, onRestart, onProgress)

    private fun fileUrl(): String = server.url("/europe/monaco-shortbread-1.0.mbtiles").toString()

    private fun part(): File = File(tempFolder.root, "regions/monaco.mbtiles.part")

    private fun partHolding(bytes: ByteArray): File =
        part().apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }

    private fun fileResponse(
        bytes: ByteArray,
        etag: String? = ETAG,
    ): MockResponse =
        MockResponse()
            .setBody(Buffer().write(bytes))
            .apply { etag?.let { setHeader("ETag", it) } }

    private fun rangeResponse(
        contentRange: String?,
        bytes: ByteArray,
        etag: String? = ETAG,
    ): MockResponse =
        MockResponse()
            .setResponseCode(206)
            .setBody(Buffer().write(bytes))
            .apply { etag?.let { setHeader("ETag", it) } }
            .apply { contentRange?.let { setHeader("Content-Range", it) } }

    private companion object {
        const val USER_AGENT = "FemtoCarLauncher/test (+https://example.test)"
        const val OTHER_HOST = "https://elsewhere.test"
        const val MIB = 1024 * 1024

        const val ETAG = "\"abc\""
        const val WEAK_ETAG = "W/\"abc\""

        // A character outside tab and 0x20–0x7e: OkHttp throws on it in a request header.
        const val UNSENDABLE_ETAG = "\"é\""
        const val NEW_ETAG = "\"def\""
        const val OTHER_VERSION_ETAG = "\"xyz\""

        // The size of the largest file in the snapshot: past Int, so any Int on the path truncates it.
        const val LARGE_SIZE = 58_444_771_328L

        const val MODIFIED_HEADER = "Wed, 18 Nov 2026 21:14:02 GMT"
        val MODIFIED_EPOCH_MS = Instant.parse("2026-11-18T21:14:02Z").toEpochMilli()

        val GONE_CODES = listOf(404, 410)

        // Answers that say nothing about the part: a busy, rate-limiting or refusing server.
        val PART_KEEPING_CODES = listOf(503, 429, 403)

        // No header, an unknown total, no total, a total past Long, an end before the start.
        val UNREADABLE_RANGES =
            listOf(
                null,
                "bytes 5-9/*",
                "bytes 5-9",
                "bytes 5-9/99999999999999999999",
                "bytes 5-4/10",
            )

        val FILE_BYTES = digitBytes(10)
        val FILE = RemoteFile(sizeBytes = 10, etag = ETAG, lastModifiedEpochMs = null)
        val NEW_BYTES = "abcdefghijkl".encodeToByteArray()
        val NEW_FILE = RemoteFile(sizeBytes = 12, etag = NEW_ETAG, lastModifiedEpochMs = null)
    }
}
