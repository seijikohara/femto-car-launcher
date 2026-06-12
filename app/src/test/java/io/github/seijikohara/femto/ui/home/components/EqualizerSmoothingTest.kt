package io.github.seijikohara.femto.ui.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FRAME_MS = 16L

class EqualizerSmoothingTest {
    @Test
    fun `rising levels approach the target within a few attack windows`() {
        // ~12 frames at 16 ms ≈ 190 ms ≈ 3 attack time constants (60 ms).
        val risen =
            (1..12).fold(floatArrayOf(0f)) { levels, _ ->
                smoothedLevels(levels, floatArrayOf(1f), FRAME_MS)
            }
        assertTrue("level ${risen[0]} should be near 1 after ~3 tau", risen[0] > 0.9f)
    }

    @Test
    fun `attack is faster than decay`() {
        val rise = smoothedLevels(floatArrayOf(0f), floatArrayOf(1f), FRAME_MS)[0]
        val fallStep = smoothedLevels(floatArrayOf(1f), floatArrayOf(0f), FRAME_MS)[0]
        val fall = 1f - fallStep
        assertTrue("rise=$rise must exceed fall=$fall per frame", rise > fall)
    }

    @Test
    fun `null target decays toward zero`() {
        val decayed =
            (1..60).fold(floatArrayOf(1f)) { levels, _ ->
                smoothedLevels(levels, null, FRAME_MS)
            }
        assertTrue("level ${decayed[0]} should decay below 0.1", decayed[0] < 0.1f)
    }

    @Test
    fun `size mismatch resets to a flat array of the target size`() {
        val reset = smoothedLevels(FloatArray(0), FloatArray(20) { 1f }, FRAME_MS)
        assertEquals(20, reset.size)
        reset.forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `null target with no displayed levels stays empty`() {
        assertEquals(0, smoothedLevels(FloatArray(0), null, FRAME_MS).size)
    }

    @Test
    fun `equal levels hold steady`() {
        val held = smoothedLevels(floatArrayOf(0.5f), floatArrayOf(0.5f), FRAME_MS)
        assertEquals(0.5f, held[0], 1e-6f)
    }
}
