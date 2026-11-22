package io.github.seijikohara.femto.data.map.offline

import io.github.seijikohara.femto.data.common.download.ProbeResult
import io.github.seijikohara.femto.data.common.download.RemoteFile
import io.github.seijikohara.femto.data.common.download.downloadHttpClient
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The region index; the region files go through the shared file transport, tested in `RemoteFileApiTest`. */
class RegionFeedApiTest {
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

    @Test
    fun `the index and the region files go to the region host and name the app`() =
        runTest {
            server.enqueue(MockResponse().setHeader("Content-Length", 10))
            server.enqueue(MockResponse().setBody("{}"))
            val api = newApi()

            val probe = api.probe(server.url("/europe/monaco-shortbread-1.0.mbtiles").toString())
            api.index(indexTarget(), etag = null)

            assertEquals(ProbeResult.Found(RemoteFile(10, etag = null, lastModifiedEpochMs = null)), probe)
            val requests = List(2) { server.takeRequest() }
            assertEquals(listOf("HEAD", "GET"), requests.map { it.method })
            assertEquals(List(2) { USER_AGENT }, requests.map { it.getHeader("User-Agent") })
        }

    @Test
    fun `an index on another host is refused without a request`() =
        runTest {
            val result = newApi(indexUrl = "$OTHER_HOST/index-v1.json").index(indexTarget(), etag = null)

            assertEquals(IndexResult.Unavailable, result)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `an index etag that could not be sent back is read as null`() =
        runTest {
            // Lenient: OkHttp reads such a value but refuses to send it in a request header.
            server.enqueue(MockResponse().setBody("{}").addHeaderLenient("ETag", UNSENDABLE_ETAG))

            val index = newApi().index(indexTarget(), etag = null)

            assertEquals(IndexResult.Fresh(etag = null), index)
        }

    @Test
    fun `an unchanged index is not rewritten`() =
        runTest {
            val target = cachedIndex("old")
            server.enqueue(MockResponse().setResponseCode(304))

            val result = newApi().index(target, INDEX_ETAG)

            assertEquals(IndexResult.NotModified, result)
            assertEquals(INDEX_ETAG, server.takeRequest().getHeader("If-None-Match"))
            assertEquals("old", target.readText())
        }

    @Test
    fun `a fresh index replaces the cached file and returns its etag`() =
        runTest {
            val target = cachedIndex("old")
            server.enqueue(MockResponse().setBody("new").setHeader("ETag", NEW_INDEX_ETAG))

            val result = newApi().index(target, INDEX_ETAG)

            assertEquals(IndexResult.Fresh(NEW_INDEX_ETAG), result)
            assertEquals("/index-v1.json", server.takeRequest().path)
            assertEquals("new", target.readText())
            assertEquals(listOf(target.name), target.parentFile?.list()?.toList())
        }

    @Test
    fun `an index with an unsendable etag is fetched without revalidation`() =
        runTest {
            val target = cachedIndex("old")
            server.enqueue(MockResponse().setBody("new").setHeader("ETag", NEW_INDEX_ETAG))

            val result = newApi().index(target, UNSENDABLE_ETAG)

            assertEquals(IndexResult.Fresh(NEW_INDEX_ETAG), result)
            assertNull(server.takeRequest().getHeader("If-None-Match"))
            assertEquals("new", target.readText())
        }

    @Test
    fun `a first index creates its directory and sends no etag`() =
        runTest {
            val target = indexTarget()
            server.enqueue(MockResponse().setBody("{}"))

            val result = newApi().index(target, etag = null)

            assertEquals(IndexResult.Fresh(etag = null), result)
            assertNull(server.takeRequest().getHeader("If-None-Match"))
            assertEquals("{}", target.readText())
        }

    @Test
    fun `an index larger than the cap is not kept`() =
        runTest {
            val target = cachedIndex("old")
            server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(MAX_REGION_INDEX_BYTES.toInt() + 1))))

            val result = newApi().index(target, INDEX_ETAG)

            assertEquals(IndexResult.Unavailable, result)
            assertEquals("old", target.readText())
            assertEquals(listOf(target.name), target.parentFile?.list()?.toList())
        }

    @Test
    fun `an index at the cap is kept`() =
        runTest {
            val target = cachedIndex("old")
            server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(MAX_REGION_INDEX_BYTES.toInt()))))

            val result = newApi().index(target, INDEX_ETAG)

            assertEquals(IndexResult.Fresh(etag = null), result)
            assertEquals(MAX_REGION_INDEX_BYTES, target.length())
        }

    @Test
    fun `an index cut short leaves the cached file alone`() =
        runTest {
            val target = cachedIndex("old")
            server.enqueue(
                MockResponse()
                    .setBody(Buffer().write(ByteArray(100_000)))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )

            val result = newApi().index(target, INDEX_ETAG)

            assertEquals(IndexResult.Unavailable, result)
            assertEquals("old", target.readText())
            assertEquals(listOf(target.name), target.parentFile?.list()?.toList())
        }

    @Test
    fun `an index that is redirected or refused is unavailable`() =
        runTest {
            val target = cachedIndex("old")
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/elsewhere/index-v1.json"))
            server.enqueue(MockResponse().setResponseCode(500))
            val api = newApi()

            val results = List(2) { api.index(target, INDEX_ETAG) }

            assertEquals(List(2) { IndexResult.Unavailable }, results)
            assertEquals(2, server.requestCount)
            assertEquals("old", target.readText())
        }

    private fun newApi(indexUrl: String = server.url("/index-v1.json").toString()): RegionFeedApi =
        RegionFeedApi(
            client = downloadHttpClient(),
            userAgent = USER_AGENT,
            hostPrefix = server.url("/").toString(),
            indexUrl = indexUrl,
        )

    private fun indexTarget(): File = File(tempFolder.root, "cache/offline_map/index-v1.json")

    private fun cachedIndex(text: String): File =
        indexTarget().apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    private companion object {
        const val USER_AGENT = "FemtoCarLauncher/test (+https://example.test)"
        const val OTHER_HOST = "https://elsewhere.test"

        // A character outside tab and 0x20–0x7e: OkHttp throws on it in a request header.
        const val UNSENDABLE_ETAG = "\"é\""
        const val INDEX_ETAG = "\"index-1\""
        const val NEW_INDEX_ETAG = "\"index-2\""
    }
}
