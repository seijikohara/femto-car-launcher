package io.github.seijikohara.femto.ui.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedOverlayEmaTest {
    @Test
    fun `null previous seeds the accumulator with the sample`() {
        assertEquals(12.5f, emaStep(previous = null, sample = 12.5f, alpha = 0.33f), 0f)
    }

    @Test
    fun `single step moves toward the sample by alpha`() {
        // previous + alpha * (sample - previous) = 10 + 0.5 * (20 - 10) = 15.
        assertEquals(15f, emaStep(previous = 10f, sample = 20f, alpha = 0.5f), 1e-4f)
    }

    @Test
    fun `repeated steps converge toward a steady sample`() {
        val target = 30f
        val converged =
            (1..10).fold(0f) { estimate, _ ->
                emaStep(previous = estimate, sample = target, alpha = 0.33f)
            }
        assertTrue(
            "estimate $converged should be within 1 of the steady sample $target",
            kotlin.math.abs(converged - target) < 1f,
        )
    }

    @Test
    fun `larger alpha takes a bigger step toward the same sample`() {
        val slow = emaStep(previous = 0f, sample = 100f, alpha = 0.2f)
        val fast = emaStep(previous = 0f, sample = 100f, alpha = 0.8f)
        assertTrue("higher alpha must respond faster: slow=$slow fast=$fast", fast > slow)
    }

    @Test
    fun `zero alpha holds the previous estimate`() {
        assertEquals(42f, emaStep(previous = 42f, sample = 100f, alpha = 0f), 0f)
    }
}
