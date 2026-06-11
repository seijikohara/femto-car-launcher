package io.github.seijikohara.femto.data.weather

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenMeteoApiTest {
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
    fun `sends the requested coordinates and forecast parameters`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            newApi().forecast(LAT, LON)
            val path = server.takeRequest().path.orEmpty()

            assertTrue(path.contains("latitude=$LAT"))
            assertTrue(path.contains("longitude=$LON"))
            assertTrue(path.contains("forecast_days=5"))
            assertTrue(path.contains("timezone=auto"))
        }

    @Test
    fun `appends the apikey query parameter when a key is configured`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            OpenMeteoApi(
                client = client,
                baseUrl = server.url("/").toString(),
                apiKey = "secret-key",
            ).forecast(LAT, LON)

            assertEquals("secret-key", server.takeRequest().requestUrl?.queryParameter("apikey"))
        }

    @Test
    fun `omits the apikey query parameter when no key is configured`() =
        runTest {
            server.enqueue(MockResponse().setBody(FORECAST_BODY))

            newApi().forecast(LAT, LON)

            assertNull(server.takeRequest().requestUrl?.queryParameter("apikey"))
        }

    @Test
    fun `returns null on a 429 rate-limit response`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(429))

            assertNull(newApi().forecast(LAT, LON))
        }

    private fun newApi(): OpenMeteoApi = OpenMeteoApi(client = client, baseUrl = server.url("/").toString())

    private companion object {
        const val LAT = 35.690
        const val LON = 139.700

        // Minimal valid current block: temperature_2m + weathercode are required,
        // every other field (and the hourly / daily blocks) is optional.
        const val FORECAST_BODY = """
            {
              "current": {
                "time": "2026-05-01T11:00",
                "temperature_2m": 18.5,
                "weathercode": 0
              }
            }
        """
    }
}
