package io.github.seijikohara.femto.data.weather

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MetNorwayApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends the coordinates and the identifying User-Agent`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            newApi().forecast(LAT, LON)
            val request = server.takeRequest()

            assertEquals(LAT, request.requestUrl?.queryParameter("lat")?.toDouble())
            assertEquals(LON, request.requestUrl?.queryParameter("lon")?.toDouble())
            assertEquals(USER_AGENT, request.getHeader("User-Agent"))
        }

    @Test
    fun `replays Last-Modified as If-Modified-Since and reuses the cached forecast on a 304`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY).setHeader("Last-Modified", LAST_MODIFIED))
            server.enqueue(MockResponse().setResponseCode(304))

            val api = newApi()
            val first = assertNotNull(api.forecast(LAT, LON))
            server.takeRequest()

            val second = api.forecast(LAT, LON)
            val conditional = server.takeRequest()

            assertEquals(LAST_MODIFIED, conditional.getHeader("If-Modified-Since"))
            // 304: the cached forecast stands in so the caller need not re-download.
            assertEquals(first, second)
        }

    @Test
    fun `returns null on a 429 throttle response`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(429))

            assertNull(newApi().forecast(LAT, LON))
        }

    private fun newApi(): MetNorwayApi =
        MetNorwayApi(
            client = client,
            baseUrl = server.url("/").toString(),
            userAgent = USER_AGENT,
        )

    private companion object {
        const val LAT = 35.658
        const val LON = 139.7016
        const val USER_AGENT = "FemtoCarLauncher/test (+https://example.com)"
        const val LAST_MODIFIED = "Fri, 01 May 2026 11:00:00 GMT"

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
