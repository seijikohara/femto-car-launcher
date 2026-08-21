package io.github.seijikohara.femto.data.location

import org.junit.Test
import kotlin.test.assertEquals

class TripStateOdometerGuardTest {
    @Test
    fun `keeps a usable total`() {
        assertEquals(1234.5, 1234.5.orZeroWhenUnusable(), 0.0)
        assertEquals(0.0, 0.0.orZeroWhenUnusable(), 0.0)
    }

    @Test
    fun `restores an absent total as zero`() {
        assertEquals(0.0, null.orZeroWhenUnusable(), 0.0)
    }

    @Test
    fun `restores a non-finite total as zero`() {
        // A pre-#351 build could accrue distance across a NaN coordinate and
        // write it through; restoring it throws out of the hero row's
        // roundToInt() on every launch.
        assertEquals(0.0, Double.NaN.orZeroWhenUnusable(), 0.0)
        assertEquals(0.0, Double.POSITIVE_INFINITY.orZeroWhenUnusable(), 0.0)
    }

    @Test
    fun `restores a negative total as zero`() {
        assertEquals(0.0, (-5.0).orZeroWhenUnusable(), 0.0)
    }
}
