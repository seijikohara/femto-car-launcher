package io.github.seijikohara.femto.data.location

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TripScenePaletteTest {
    // The VM dedups palette pushes with `==`, so equality must be value-based
    // (a data class over FloatArray would fall back to array identity).
    @Test
    fun `equality and hash are value-based over the colour arrays`() {
        val a = paletteOf(isDark = true, grid = floatArrayOf(0.4f, 0.5f, 0.6f))
        val b = paletteOf(isDark = true, grid = floatArrayOf(0.4f, 0.5f, 0.6f))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `differing dark flag, grid, or line scale are not equal`() {
        val base = paletteOf(isDark = true, grid = floatArrayOf(0.4f, 0.5f, 0.6f))
        assertNotEquals(base, paletteOf(isDark = false, grid = floatArrayOf(0.4f, 0.5f, 0.6f)))
        assertNotEquals(base, paletteOf(isDark = true, grid = floatArrayOf(0.1f, 0.2f, 0.3f)))
        assertNotEquals(base, paletteOf(isDark = true, grid = floatArrayOf(0.4f, 0.5f, 0.6f), lineScale = 0.5f))
    }

    private fun paletteOf(
        isDark: Boolean,
        grid: FloatArray,
        lineScale: Float = 1f,
    ) = TripScenePalette(
        isDark = isDark,
        background = floatArrayOf(0.02f, 0.03f, 0.06f),
        grid = grid,
        head = floatArrayOf(1f, 1f, 1f),
        lineScale = lineScale,
    )
}
