package io.github.seijikohara.femto.data.location

import io.github.seijikohara.femto.testfixtures.fakeLocation
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationFreshnessTest {
    private val thresholdNanos = LOCATION_STALE_THRESHOLD_MS * 1_000_000L

    @Test
    fun `fix is fresh right at the threshold`() {
        val fix = fakeLocation(elapsedRealtimeNanos = 0L)
        assertTrue(fix.isFresh(nowElapsedRealtimeNanos = thresholdNanos))
    }

    @Test
    fun `fix is stale just past the threshold`() {
        val fix = fakeLocation(elapsedRealtimeNanos = 0L)
        assertFalse(fix.isFresh(nowElapsedRealtimeNanos = thresholdNanos + 1L))
    }

    @Test
    fun `a fix from the same instant is fresh`() {
        val fix = fakeLocation(elapsedRealtimeNanos = 5_000_000_000L)
        assertTrue(fix.isFresh(nowElapsedRealtimeNanos = 5_000_000_000L))
    }
}
