package io.github.seijikohara.femto.data

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

class NominatimApiTest {
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
    fun `parses structured address city from a jsonv2 response`() =
        runTest {
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))

            val response = newApi().reverse(LAT, LON)

            assertEquals("新宿区", response?.address?.city)
        }

    @Test
    fun `sends format and addressdetails query parameters`() =
        runTest {
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))

            newApi().reverse(LAT, LON)
            val path = server.takeRequest().path.orEmpty()

            assertTrue(path.contains("format=jsonv2"))
            assertTrue(path.contains("addressdetails=1"))
        }

    @Test
    fun `sends accept-language and the requested coordinates`() =
        runTest {
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))

            newApi().reverse(LAT, LON)
            val path = server.takeRequest().path.orEmpty()

            assertTrue(path.contains("accept-language=ja"))
            assertTrue(path.contains("lat=$LAT"))
            assertTrue(path.contains("lon=$LON"))
        }

    @Test
    fun `sends the configured user-agent header`() =
        runTest {
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))

            newApi().reverse(LAT, LON)
            val request = server.takeRequest()

            assertEquals(USER_AGENT, request.getHeader("User-Agent"))
        }

    @Test
    fun `returns null on a 429 rate-limit response`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(429))

            assertNull(newApi().reverse(LAT, LON))
        }

    @Test
    fun `returns null on a malformed response body`() =
        runTest {
            server.enqueue(MockResponse().setBody("{ not json"))

            assertNull(newApi().reverse(LAT, LON))
        }

    private fun newApi(): NominatimApi =
        NominatimApi(
            client = client,
            baseUrl = server.url("/").toString(),
            userAgent = USER_AGENT,
        )

    private companion object {
        const val LAT = 35.690
        const val LON = 139.700
        const val USER_AGENT = "femto-car-launcher/1.0 (test)"

        const val SHINJUKU_BODY = """
            {
              "display_name": "..., 新宿三丁目, 新宿, 新宿区, 東京都, 160-0022, 日本",
              "address": {
                "neighbourhood": "新宿三丁目",
                "quarter": "新宿",
                "city": "新宿区",
                "ISO3166-2-lvl4": "JP-13",
                "postcode": "160-0022",
                "country": "日本",
                "country_code": "jp"
              }
            }
        """
    }
}
