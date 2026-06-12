package io.github.seijikohara.femto.ui.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedOverlaySmoothingTest {
    @Test
    fun `null previous seeds the estimate with the sample`() {
        assertEquals(12.5f, speedSmoothingStep(previous = null, sampleMs = 12.5f, dtMillis = 250L), 0f)
    }

    @Test
    fun `a stationary sample snaps the estimate straight to zero`() {
        // Below the moving floor the dial must read 0 immediately — no decay tail.
        assertEquals(0f, speedSmoothingStep(previous = 11f, sampleMs = 0.2f, dtMillis = 250L), 0f)
    }

    @Test
    fun `a stationary first sample also reads zero`() {
        assertEquals(0f, speedSmoothingStep(previous = null, sampleMs = 0.1f, dtMillis = 0L), 0f)
    }

    @Test
    fun `a larger dt steps further toward the sample`() {
        val slow = speedSmoothingStep(previous = 0f, sampleMs = 30f, dtMillis = 100L)
        val fast = speedSmoothingStep(previous = 0f, sampleMs = 30f, dtMillis = 1_000L)
        assertTrue("longer elapsed time must respond more: slow=$slow fast=$fast", fast > slow)
    }

    @Test
    fun `zero dt holds the previous estimate`() {
        assertEquals(20f, speedSmoothingStep(previous = 20f, sampleMs = 30f, dtMillis = 0L), 1e-4f)
    }

    @Test
    fun `settles near a steady sample within one second of accumulated time`() {
        // 4 fixes x 250 ms = 1 s ≈ 3 time constants: ≥ 90% of the step covered.
        val settled =
            (1..4).fold(0f) { estimate, _ ->
                speedSmoothingStep(previous = estimate, sampleMs = 30f, dtMillis = 250L)
            }
        assertTrue("estimate $settled should be within 10% of 30 after ~3 tau", settled > 27f)
    }

    @Test
    fun `settle time is cadence independent`() {
        // The same 1 s of real time smooths equally whether it arrives as
        // four 250 ms fixes or one 1000 ms fix (within fp tolerance).
        val chunked =
            (1..4).fold(0f) { estimate, _ ->
                speedSmoothingStep(previous = estimate, sampleMs = 30f, dtMillis = 250L)
            }
        val single = speedSmoothingStep(previous = 0f, sampleMs = 30f, dtMillis = 1_000L)
        assertEquals(single, chunked, 0.01f)
    }
}
