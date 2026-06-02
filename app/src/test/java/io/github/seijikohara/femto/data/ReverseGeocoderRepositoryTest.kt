@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data

import android.location.Location
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReverseGeocoderRepositoryTest {
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
    fun `emits the composed short address for a fix`() =
        runTest {
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))

            newRepo(flowOf(fakeLocation())).addressFlow().test {
                val address = awaitItem()
                assertEquals("新宿区", address?.locality)
                assertEquals("東京都新宿区新宿三丁目", address?.displayString())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dedupes consecutive near locations to a single network call`() =
        runTest {
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))

            // Two fixes within the 100 m bucket collapse to one emission and
            // one request.
            val flow = flowOf(fakeLocation(), fakeLocation(latitude = 35.65805))
            newRepo(flow).addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, server.requestCount)
        }

    @Test
    fun `does not issue a second request when revisiting a cached bucket`() =
        runTest {
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))
            // The far fix gets its own response so the near-bucket revisit is
            // the only call that must hit the cache instead of the network.
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))

            val near = fakeLocation()
            val far = fakeLocation(latitude = 35.7000)
            // Drain all three fixes (near, far, near) so the cached revisit is
            // exercised before the request count is asserted.
            newRepo(flowOf(near, far, near)).addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                awaitItem()
                assertEquals("新宿区", awaitItem()?.locality)
                awaitComplete()
            }

            // One request for the near bucket, one for the far bucket; the
            // revisit of the near bucket is served from the cache.
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `falls back to the last cached value on a rate-limit response`() =
        runTest {
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))
            server.enqueue(MockResponse().setResponseCode(429))

            val flow = flowOf(fakeLocation(), fakeLocation(latitude = 35.7000))
            newRepo(flow).addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                // The throttled second bucket returns the cached value, not null.
                assertEquals("新宿区", awaitItem()?.locality)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits null when the first lookup fails with no cached value`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(429))

            newRepo(flowOf(fakeLocation())).addressFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits null when location flow yields null`() =
        runTest {
            newRepo(flowOf(null)).addressFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun TestScope.newRepo(locationFlow: Flow<Location?>): ReverseGeocoderRepository {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return ReverseGeocoderRepository(
            locationFlow = locationFlow,
            api =
                NominatimApi(
                    client = client,
                    baseUrl = server.url("/").toString(),
                    userAgent = USER_AGENT,
                    ioDispatcher = dispatcher,
                ),
            ioDispatcher = dispatcher,
            // Tie the throttle clock to the scheduler so delay() advances it.
            nowMs = { testScheduler.currentTime },
        )
    }

    private companion object {
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
