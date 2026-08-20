package io.github.seijikohara.femto.data.location

import io.github.seijikohara.femto.testfixtures.fakeLocation
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Location is an Android type Robolectric supplies; the policy itself is pure.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationSanityTest {
    @Test
    fun `accepts the first fix of a session`() {
        assertTrue(isUsableFix(fakeLocation(elapsedRealtimeNanos = 5_000L), lastAcceptedElapsedNanos = null))
    }

    @Test
    fun `accepts a fix that advances the boot clock`() {
        assertTrue(isUsableFix(fakeLocation(elapsedRealtimeNanos = 6_000L), lastAcceptedElapsedNanos = 5_000L))
    }

    @Test
    fun `rejects a replayed fix from behind the boot clock`() {
        // The re-subscribe seed replays getLastKnownLocation for both
        // providers; the NETWORK cache is routinely older than the GPS one.
        assertFalse(isUsableFix(fakeLocation(elapsedRealtimeNanos = 4_000L), lastAcceptedElapsedNanos = 5_000L))
    }

    @Test
    fun `accepts a repeated timestamp so a zero-timestamp HAL keeps delivering`() {
        // Some HALs never populate elapsedRealtimeNanos. Requiring a strict
        // advance would deliver exactly one fix and then go silent.
        assertTrue(isUsableFix(fakeLocation(elapsedRealtimeNanos = 0L), lastAcceptedElapsedNanos = 0L))
    }

    @Test
    fun `rejects a non-finite coordinate`() {
        assertFalse(isUsableFix(fakeLocation(latitude = Double.NaN), lastAcceptedElapsedNanos = null))
        assertFalse(
            isUsableFix(fakeLocation(longitude = Double.POSITIVE_INFINITY), lastAcceptedElapsedNanos = null),
        )
    }

    @Test
    fun `rejects a coordinate outside the WGS84 range`() {
        // A camera target off the globe throws the map marker out of the
        // viewport until the next fix lands (issue #351).
        assertFalse(isUsableFix(fakeLocation(latitude = 91.0), lastAcceptedElapsedNanos = null))
        assertFalse(isUsableFix(fakeLocation(longitude = -181.0), lastAcceptedElapsedNanos = null))
    }

    @Test
    fun `a stamp the boot clock can vouch for may anchor the baseline`() {
        assertTrue(canAnchorRecency(fakeLocation(elapsedRealtimeNanos = 5_000L), nowElapsedRealtimeNanos = 9_000L))
    }

    @Test
    fun `a stamp from the future may not anchor the baseline`() {
        // Epoch nanos where boot nanos belong — the classic mock-provider bug.
        // Anchoring on it would pin the baseline above every genuine fix and
        // blank the whole location stack until the process restarts.
        assertFalse(
            canAnchorRecency(
                fakeLocation(elapsedRealtimeNanos = 1_700_000_000_000_000_000L),
                nowElapsedRealtimeNanos = 9_000L,
            ),
        )
    }

    @Test
    fun `a negative stamp may not anchor the baseline`() {
        assertFalse(canAnchorRecency(fakeLocation(elapsedRealtimeNanos = -1L), nowElapsedRealtimeNanos = 9_000L))
    }
}
