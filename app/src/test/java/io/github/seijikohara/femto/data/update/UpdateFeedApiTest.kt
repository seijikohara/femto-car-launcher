package io.github.seijikohara.femto.data.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateFeedApiTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- latest -------------------------------------------------------------

    @Test
    fun `looks up the stable manifest through the latest-release permalink`() =
        runTest {
            server.enqueue(MockResponse().setBody(MANIFEST_BODY))

            newApi().latest(UpdateChannel.STABLE)

            assertEquals("/releases/latest/download/femto-car-launcher-update.json", server.takeRequest().path)
        }

    @Test
    fun `looks up the nightly manifest under the nightly tag`() =
        runTest {
            server.enqueue(MockResponse().setBody(MANIFEST_BODY))

            newApi().latest(UpdateChannel.NIGHTLY)

            assertEquals("/releases/download/nightly/femto-car-launcher-update.json", server.takeRequest().path)
        }

    @Test
    fun `sends the identifying User-Agent`() =
        runTest {
            server.enqueue(MockResponse().setBody(MANIFEST_BODY))

            newApi().latest(UpdateChannel.STABLE)

            assertEquals(USER_AGENT, server.takeRequest().getHeader("User-Agent"))
        }

    @Test
    fun `parses the manifest CI publishes`() =
        runTest {
            server.enqueue(MockResponse().setBody(MANIFEST_BODY))

            val result = newApi().latest(UpdateChannel.STABLE)

            assertEquals(
                FeedResult.Found(
                    UpdateManifest(
                        schemaVersion = 1,
                        channel = "stable",
                        versionCode = 26092401,
                        versionName = "2026.09.24-1",
                        apk =
                            UpdateManifest.Apk(
                                name = "femto-car-launcher-v2026.09.24-1.apk",
                                size = 45310215,
                                sha256 = MANIFEST_SHA256,
                                url = MANIFEST_APK_URL,
                            ),
                    ),
                ),
                result,
            )
        }

    @Test
    fun `a missing manifest is no information`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))

            assertEquals(FeedResult.NoInformation, newApi().latest(UpdateChannel.STABLE))
        }

    @Test
    fun `a body that is not a manifest is no information`() =
        runTest {
            server.enqueue(MockResponse().setBody("<html>not a manifest</html>"))

            assertEquals(FeedResult.NoInformation, newApi().latest(UpdateChannel.STABLE))
        }

    @Test
    fun `a manifest without a sha256 is no information`() =
        runTest {
            // No hash, no install: the manifest must not parse into an offer.
            server.enqueue(MockResponse().setBody(MANIFEST_BODY.replace(Regex("\"sha256\": \"[0-9a-f]+\","), "")))

            assertEquals(FeedResult.NoInformation, newApi().latest(UpdateChannel.STABLE))
        }

    @Test
    fun `a server error is a network failure`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            assertEquals(FeedResult.Unavailable(UpdateFailure.NETWORK), newApi().latest(UpdateChannel.STABLE))
        }

    @Test
    fun `a body cut short is a network failure`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(MANIFEST_BODY).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )

            assertEquals(FeedResult.Unavailable(UpdateFailure.NETWORK), newApi().latest(UpdateChannel.STABLE))
        }

    @Test
    fun `a 429 pauses lookups until the Retry-After horizon passes`() =
        runTest {
            var now = 0L
            val api = newApi(nowMs = { now })
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))

            assertEquals(FeedResult.Unavailable(UpdateFailure.RATE_LIMITED), api.latest(UpdateChannel.STABLE))

            // Inside the horizon: rate-limited again, and no request leaves.
            now = 119_000L
            assertEquals(FeedResult.Unavailable(UpdateFailure.RATE_LIMITED), api.latest(UpdateChannel.STABLE))
            assertEquals(1, server.requestCount)

            // Past it, lookups resume.
            now = 121_000L
            server.enqueue(MockResponse().setBody(MANIFEST_BODY))
            api.latest(UpdateChannel.STABLE)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a 403 without Retry-After pauses lookups for a minute`() =
        runTest {
            var now = 0L
            val api = newApi(nowMs = { now })
            server.enqueue(MockResponse().setResponseCode(403))

            assertEquals(FeedResult.Unavailable(UpdateFailure.RATE_LIMITED), api.latest(UpdateChannel.STABLE))

            now = 59_000L
            api.latest(UpdateChannel.STABLE)
            assertEquals(1, server.requestCount)

            now = 61_000L
            server.enqueue(MockResponse().setBody(MANIFEST_BODY))
            api.latest(UpdateChannel.STABLE)
            assertEquals(2, server.requestCount)
        }

    // --- download -----------------------------------------------------------

    @Test
    fun `download writes the body to the target and leaves no part file`() =
        runTest {
            val body = apkBody(100_000)
            server.enqueue(MockResponse().setBody(body))
            val target = target()

            val downloaded = newApi().download(apkUrl(), target, body.length.toLong()) {}

            assertTrue(downloaded)
            assertContentEquals(body.encodeToByteArray(), target.readBytes())
            assertFalse(partOf(target).exists())
        }

    @Test
    fun `download reports rising progress up to completion`() =
        runTest {
            val body = apkBody(100_000)
            server.enqueue(MockResponse().setBody(body))
            val fractions = mutableListOf<Float>()

            newApi().download(apkUrl(), target(), body.length.toLong()) { fractions += it }

            assertEquals(1f, fractions.last())
            assertEquals(fractions.sorted().distinct(), fractions)
        }

    @Test
    fun `download reads at most one byte past the expected size`() =
        runTest {
            server.enqueue(MockResponse().setBody(apkBody(10_000)))
            val target = target()

            newApi().download(apkUrl(), target, expectedSize = 100) {}

            // One byte over is enough for the caller's size check to reject it.
            assertEquals(101, target.length())
        }

    @Test
    fun `a failed download leaves neither the target nor a part file`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            val target = target()

            val downloaded = newApi().download(apkUrl(), target, expectedSize = 100) {}

            assertFalse(downloaded)
            assertFalse(target.exists())
            assertFalse(partOf(target).exists())
        }

    @Test
    fun `a transfer cut mid-body leaves neither the target nor a part file`() =
        runTest {
            val body = apkBody(100_000)
            server.enqueue(
                MockResponse()
                    .setBody(body)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            val target = target()

            val downloaded = newApi().download(apkUrl(), target, body.length.toLong()) {}

            assertFalse(downloaded)
            assertFalse(target.exists())
            assertFalse(partOf(target).exists())
        }

    private fun newApi(nowMs: () -> Long = { 0L }): UpdateFeedApi =
        UpdateFeedApi(
            client = client,
            feedBase = server.url("/releases").toString(),
            userAgent = USER_AGENT,
            nowMs = nowMs,
        )

    private fun apkUrl(): String = server.url("/releases/download/nightly/app.apk").toString()

    private fun target(): File = File(tempFolder.root, "update/update.apk")

    private fun partOf(target: File): File = File(target.parentFile, target.name + ".part")

    // ASCII stand-in APK bytes; the digits shift against the copy buffer's
    // chunk size, so a dropped or reordered chunk changes the content.
    private fun apkBody(length: Int): String = (0 until length).joinToString(separator = "") { (it % 10).toString() }

    private companion object {
        const val USER_AGENT = "FemtoCarLauncher/test (+https://example.test)"
        const val MANIFEST_SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val MANIFEST_APK_URL =
            "https://example.test/releases/download/v2026.09.24-1/femto-car-launcher-v2026.09.24-1.apk"

        // The shape jq -n writes in .github/actions/update-manifest (pretty-printed).
        const val MANIFEST_BODY = """
            {
              "schemaVersion": 1,
              "channel": "stable",
              "versionCode": 26092401,
              "versionName": "2026.09.24-1",
              "apk": {
                "name": "femto-car-launcher-v2026.09.24-1.apk",
                "size": 45310215,
                "sha256": "$MANIFEST_SHA256",
                "url": "$MANIFEST_APK_URL"
              }
            }
        """
    }
}
