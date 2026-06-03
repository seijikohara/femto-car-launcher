@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data

import android.location.Location
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
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

    @Test
    fun `evicts the eldest bucket once the cache exceeds its bound`() =
        runTest {
            // The eldest fill, MAX_ENTRIES newer fills that push the eldest out,
            // and the eldest re-query after its eviction each hit the network.
            val totalRequests = MAX_ENTRIES + 2
            repeat(totalRequests) { server.enqueue(MockResponse().setBody(SHINJUKU_BODY)) }

            // Distinct buckets spaced well beyond the 100 m bucket so each fix is
            // its own cache key. The eldest bucket is filled first, never
            // re-read while the newer buckets arrive, then revisited last to
            // prove it was evicted rather than served from the cache. Inserting
            // the eldest plus MAX_ENTRIES newer buckets overflows the cap by one,
            // so the eldest is the entry that the LRU drops.
            val eldest = fakeLocation(latitude = BASE_LATITUDE)
            val newer =
                (1..MAX_ENTRIES).map { index ->
                    fakeLocation(latitude = BASE_LATITUDE + index * BUCKET_STEP_DEG)
                }
            val fixes = listOf(eldest) + newer + eldest

            newRepo(fixes.asFlow()).addressFlow().test {
                repeat(fixes.size) { assertEquals("新宿区", awaitItem()?.locality) }
                awaitComplete()
            }

            // If the eldest were still cached the revisit would be a cache hit
            // and the count would be MAX_ENTRIES + 1.
            assertEquals(totalRequests, server.requestCount)
        }

    @Test
    fun `re-queries a bucket once its cached value passes the TTL`() =
        runTest {
            // One body per network call: near bucket, far bucket, then the near
            // bucket again after the TTL expires.
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))
            server.enqueue(MockResponse().setBody(SHINJUKU_BODY))

            // The clock crosses the near entry's TTL once the near and far fills
            // (the first two network calls) have landed. Tying the clock to the
            // request count makes the TTL crossing deterministic regardless of
            // when the eager flow drains, where a variable mutated between
            // awaitItem() calls would race the buffered emissions.
            val nowMs = { if (server.requestCount >= 2) TTL_MS + 1 else 0L }
            // near -> far -> near: the far fix between the two near visits keeps
            // distinctUntilChangedByBucket from collapsing them, so the second
            // near visit actually reaches resolve().
            val near = fakeLocation()
            val far = fakeLocation(latitude = 35.7000)
            val repo = newRepo(flowOf(near, far, near), nowMs = nowMs)

            repo.addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                assertEquals("新宿区", awaitItem()?.locality)
                // Past the TTL: the near bucket re-queries instead of hitting the
                // now-stale cache entry.
                assertEquals("新宿区", awaitItem()?.locality)
                awaitComplete()
            }

            // Near (fill), far (fill), near (re-query after the TTL): the
            // sibling cache test asserts this same sequence is two requests when
            // the clock stays inside the TTL.
            assertEquals(3, server.requestCount)
        }

    private fun TestScope.newRepo(
        locationFlow: Flow<Location?>,
        nowMs: () -> Long = { testScheduler.currentTime },
    ): ReverseGeocoderRepository {
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
            nowMs = nowMs,
        )
    }

    private companion object {
        const val USER_AGENT = "femto-car-launcher/1.0 (test)"

        // Mirrors ReverseGeocoderRepository.MAX_ENTRIES / TTL_MS, which are
        // private; keep these in sync if the production bounds change.
        const val MAX_ENTRIES = 256
        const val TTL_MS = 24L * 60L * 60L * 1_000L

        // A starting latitude and a step large enough that each fix lands in a
        // distinct 100 m bucket (~0.001 deg ~= 111 m).
        const val BASE_LATITUDE = 35.0
        const val BUCKET_STEP_DEG = 0.01

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
