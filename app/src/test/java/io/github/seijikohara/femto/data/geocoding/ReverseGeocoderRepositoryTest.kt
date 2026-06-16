@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data.geocoding

import android.location.Location
import app.cash.turbine.test
import io.github.seijikohara.femto.data.geocoding.ReverseGeocoderRepository.Companion.MAX_ENTRIES
import io.github.seijikohara.femto.data.geocoding.ReverseGeocoderRepository.Companion.NETWORK_PACING_MS
import io.github.seijikohara.femto.data.geocoding.ReverseGeocoderRepository.Companion.TTL_MS
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReverseGeocoderRepositoryTest {
    @Test
    fun `emits the resolved short address for a fix`() =
        runTest {
            val geocoder = FakeReverseGeocoder()

            newRepo(flowOf(fakeLocation()), geocoder).addressFlow().test {
                val address = awaitItem()
                assertEquals("新宿区", address?.locality)
                assertEquals("東京都新宿区新宿三丁目", address?.displayString())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dedupes consecutive near locations to a single lookup`() =
        runTest {
            val geocoder = FakeReverseGeocoder()

            // Two fixes within the 100 m bucket collapse to one emission and one
            // lookup.
            val flow = flowOf(fakeLocation(), fakeLocation(latitude = 35.65805))
            newRepo(flow, geocoder).addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, geocoder.callCount)
        }

    @Test
    fun `does not issue a second lookup when revisiting a cached bucket`() =
        runTest {
            val geocoder = FakeReverseGeocoder()
            val near = fakeLocation()
            val far = fakeLocation(latitude = 35.7000)
            // Drain all three fixes (near, far, near) so the cached revisit is
            // exercised. The clock advances one pacing window per lookup so the
            // far bucket clears the pacing gate.
            newRepo(flowOf(near, far, near), geocoder, nowMs = pacedClock(geocoder)).addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                awaitItem()
                assertEquals("新宿区", awaitItem()?.locality)
                awaitComplete()
            }

            // One lookup for the near bucket, one for the far; the revisit of the
            // near bucket is served from the cache.
            assertEquals(2, geocoder.callCount)
        }

    @Test
    fun `falls back to the last cached value on a failed lookup`() =
        runTest {
            // The far fix sits ~4.7 km from the resolved address — inside the
            // fallback distance bound, so the failed lookup still serves the last
            // value.
            val geocoder = FakeReverseGeocoder(listOf(SHINJUKU, null))
            val flow = flowOf(fakeLocation(), fakeLocation(latitude = 35.7000))
            newRepo(flow, geocoder, nowMs = pacedClock(geocoder)).addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                // The failed second bucket returns the cached value, not null.
                assertEquals("新宿区", awaitItem()?.locality)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `skips the lookup for a new bucket inside the pacing window`() =
        runTest {
            val geocoder = FakeReverseGeocoder()
            // The clock never advances, so the far bucket arrives inside the
            // pacing window and must reuse the last address with no lookup.
            val flow = flowOf(fakeLocation(), fakeLocation(latitude = 35.7000))
            newRepo(flow, geocoder, nowMs = { 0L }).addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                assertEquals("新宿区", awaitItem()?.locality)
                awaitComplete()
            }

            assertEquals(1, geocoder.callCount)
        }

    @Test
    fun `drops the stale fallback once the fix moves beyond the distance bound`() =
        runTest {
            // The far fix is ~115 km from the last resolved address, far past the
            // fallback distance bound.
            val geocoder = FakeReverseGeocoder(listOf(SHINJUKU, null))
            val flow = flowOf(fakeLocation(), fakeLocation(latitude = 36.7000))
            newRepo(flow, geocoder, nowMs = pacedClock(geocoder)).addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                // A stale address from 115 km away is worse than no address.
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `emits null when the first lookup fails with no cached value`() =
        runTest {
            val geocoder = FakeReverseGeocoder(listOf(null))

            newRepo(flowOf(fakeLocation()), geocoder).addressFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits null when location flow yields null`() =
        runTest {
            newRepo(flowOf(null), FakeReverseGeocoder()).addressFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `evicts the eldest bucket once the cache exceeds its bound`() =
        runTest {
            val geocoder = FakeReverseGeocoder()
            // The eldest fill, MAX_ENTRIES newer fills that push the eldest out,
            // and the eldest re-query after its eviction each hit the source.
            val totalLookups = MAX_ENTRIES + 2

            val eldest = fakeLocation(latitude = BASE_LATITUDE)
            val newer =
                (1..MAX_ENTRIES).map { index ->
                    fakeLocation(latitude = BASE_LATITUDE + index * BUCKET_STEP_DEG)
                }
            val fixes = listOf(eldest) + newer + eldest

            newRepo(fixes.asFlow(), geocoder, nowMs = pacedClock(geocoder)).addressFlow().test {
                repeat(fixes.size) { assertEquals("新宿区", awaitItem()?.locality) }
                awaitComplete()
            }

            // If the eldest were still cached the revisit would be a cache hit and
            // the count would be MAX_ENTRIES + 1.
            assertEquals(totalLookups, geocoder.callCount)
        }

    @Test
    fun `re-queries a bucket once its cached value passes the TTL`() =
        runTest {
            val geocoder = FakeReverseGeocoder()
            // The clock crosses the near entry's TTL once the near and far fills
            // (the first two lookups) have landed. Tying the clock to the call
            // count makes the TTL crossing deterministic regardless of when the
            // eager flow drains. Before the crossing the clock advances one pacing
            // window per lookup so the far bucket clears the pacing gate; the
            // crossing adds that pacing offset because the near entry was stamped
            // at one window past zero.
            val nowMs = {
                if (geocoder.callCount >= 2) {
                    TTL_MS + NETWORK_PACING_MS + 1
                } else {
                    geocoder.callCount * NETWORK_PACING_MS
                }
            }
            val near = fakeLocation()
            val far = fakeLocation(latitude = 35.7000)

            newRepo(flowOf(near, far, near), geocoder, nowMs = nowMs).addressFlow().test {
                assertEquals("新宿区", awaitItem()?.locality)
                assertEquals("新宿区", awaitItem()?.locality)
                // Past the TTL: the near bucket re-queries instead of hitting the
                // now-stale cache entry.
                assertEquals("新宿区", awaitItem()?.locality)
                awaitComplete()
            }

            assertEquals(3, geocoder.callCount)
        }

    // Advance the clock one pacing window per issued lookup so multi-bucket
    // scenarios clear the pacing gate deterministically.
    private fun pacedClock(geocoder: FakeReverseGeocoder): () -> Long = { geocoder.callCount * NETWORK_PACING_MS }

    private fun TestScope.newRepo(
        locationFlow: Flow<Location?>,
        geocoder: ReverseGeocoder,
        nowMs: () -> Long = { testScheduler.currentTime },
    ): ReverseGeocoderRepository =
        ReverseGeocoderRepository(
            locationFlow = locationFlow,
            geocoder = geocoder,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            // The injected clock keeps the pacing gate and the cache TTL
            // deterministic under the test scheduler.
            nowMs = nowMs,
        )

    // Returns the canned results in order, clamping to the last entry once
    // exhausted, and counts every call so the pacing / cache assertions can read
    // it the way they previously read MockWebServer.requestCount.
    private class FakeReverseGeocoder(
        private val results: List<ShortAddress?> = listOf(SHINJUKU),
    ) : ReverseGeocoder {
        var callCount = 0
            private set

        override suspend fun reverse(
            latitude: Double,
            longitude: Double,
        ): ShortAddress? = results[callCount.coerceAtMost(results.size - 1)].also { callCount++ }
    }

    private companion object {
        val SHINJUKU = ShortAddress(locality = "新宿区", region = "東京都", line = "東京都新宿区新宿三丁目")

        // A starting latitude and a step large enough that each fix lands in a
        // distinct 100 m bucket (~0.001 deg ~= 111 m).
        const val BASE_LATITUDE = 35.0
        const val BUCKET_STEP_DEG = 0.01
    }
}
