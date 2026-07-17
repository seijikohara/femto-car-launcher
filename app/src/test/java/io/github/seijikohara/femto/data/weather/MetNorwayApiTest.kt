package io.github.seijikohara.femto.data.weather

import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MetNorwayApiTest {
    @get:Rule
    val cacheDir = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        // A real disk cache in a temp dir: the ToS-mandated Expires /
        // If-Modified-Since behavior lives in OkHttp's cache layer (mirroring
        // the production client built in HomeViewModelFactory), so the tests
        // exercise it rather than mock around it.
        client =
            OkHttpClient
                .Builder()
                .cache(Cache(cacheDir.newFolder(), CACHE_BYTES))
                .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends four-decimal coordinates and the identifying User-Agent`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            newApi().forecast(35.65858123, 139.70168456)
            val request = server.takeRequest()

            // Full-precision GPS input leaves rounded to exactly four decimals
            // (five or more return 403/400 per the MET terms).
            assertEquals("35.6586", request.requestUrl?.queryParameter("lat"))
            assertEquals("139.7017", request.requestUrl?.queryParameter("lon"))
            assertEquals(USER_AGENT, request.getHeader("User-Agent"))
        }

    @Test
    fun `serves from the HTTP cache until Expires without touching the network`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(FORECAST_BODY)
                    .setHeader("Expires", httpDate(Instant.now().plusSeconds(3600)))
                    .setHeader("Last-Modified", LAST_MODIFIED),
            )

            val api = newApi()
            val first = assertNotNull(api.forecast(LAT, LON))
            val second = assertNotNull(api.forecast(LAT, LON))

            assertEquals(first, second)
            // The second answer came from the cache: the ToS forbids repeating
            // a request before the Expires horizon, and none was made.
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `revalidates with If-Modified-Since after Expires and reuses the body on a 304`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(FORECAST_BODY)
                    .setHeader("Expires", httpDate(Instant.now().minusSeconds(60)))
                    .setHeader("Last-Modified", LAST_MODIFIED),
            )
            server.enqueue(MockResponse().setResponseCode(304))

            val api = newApi()
            val first = assertNotNull(api.forecast(LAT, LON))
            server.takeRequest()

            val second = assertNotNull(api.forecast(LAT, LON))
            val conditional = server.takeRequest()

            // The already-expired entry is revalidated, not re-downloaded: the
            // stored validator rides If-Modified-Since and the 304 answer
            // resolves to the cached body.
            assertEquals(LAST_MODIFIED, conditional.getHeader("If-Modified-Since"))
            assertEquals(first, second)
        }

    @Test
    fun `refuses further requests until the Retry-After horizon passes`() =
        runTest {
            var now = 0L
            val api = newApi(nowMs = { now })
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))

            assertNull(api.forecast(LAT, LON))
            assertEquals(1, server.requestCount)

            // Still inside the horizon: no request may leave the client.
            now = 59_000L
            assertNull(api.forecast(LAT, LON))
            assertEquals(1, server.requestCount)

            // Past the horizon the client resumes normally.
            now = 61_000L
            server.enqueue(MockResponse().setBody(FORECAST_BODY))
            assertNotNull(api.forecast(LAT, LON))
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `retries on the next call after a 429 without Retry-After`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(429))
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            val api = newApi()
            assertNull(api.forecast(LAT, LON))
            // No horizon was announced, so pacing falls to the repository's
            // attempt floor; the API itself does not suppress the next call.
            assertNotNull(api.forecast(LAT, LON))
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `parses a 203 beta-product response body`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(203).setBody(FORECAST_BODY))

            assertNotNull(newApi().forecast(LAT, LON))
        }

    private fun newApi(nowMs: () -> Long = System::currentTimeMillis): MetNorwayApi =
        MetNorwayApi(
            client = client,
            baseUrl = server.url("/").toString(),
            userAgent = USER_AGENT,
            nowMs = nowMs,
        )

    private companion object {
        const val LAT = 35.658
        const val LON = 139.7016
        const val USER_AGENT = "FemtoCarLauncher/test (+https://example.com)"
        const val LAST_MODIFIED = "Fri, 01 May 2026 11:00:00 GMT"
        const val CACHE_BYTES = 1L * 1024 * 1024

        fun httpDate(instant: Instant): String =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(instant)

        const val FORECAST_BODY = """
            {
              "properties": {
                "timeseries": [
                  {
                    "time": "2026-05-01T11:00:00Z",
                    "data": {
                      "instant": {
                        "details": {
                          "air_temperature": 18.5,
                          "wind_speed": 3.5,
                          "relative_humidity": 60.0,
                          "ultraviolet_index_clear_sky": 4.5
                        }
                      },
                      "next_1_hours": { "summary": { "symbol_code": "clearsky_day" } }
                    }
                  }
                ]
              }
            }
        """
    }
}
