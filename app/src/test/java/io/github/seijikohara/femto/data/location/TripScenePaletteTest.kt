package io.github.seijikohara.femto.data.location

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
    fun `differing dark flag, grid, or head are not equal`() {
        val base = paletteOf(isDark = true, grid = floatArrayOf(0.4f, 0.5f, 0.6f))
        assertNotEquals(base, paletteOf(isDark = false, grid = floatArrayOf(0.4f, 0.5f, 0.6f)))
        assertNotEquals(base, paletteOf(isDark = true, grid = floatArrayOf(0.1f, 0.2f, 0.3f)))
        assertNotEquals(
            base,
            paletteOf(isDark = true, grid = floatArrayOf(0.4f, 0.5f, 0.6f), head = floatArrayOf(0f, 0f, 0f)),
        )
    }

    // The turbo gradient's dark navy low end is the "wires all black" culprit on
    // the light scene: the tone must lift it into a clearly-coloured band while
    // keeping its blue-dominant hue.
    @Test
    fun `light tone lifts a near-black navy into a saturated blue`() {
        val toned = lightSceneLineTone(0.19f, 0.072f, 0.232f)
        val value = maxOf(toned[0], toned[1], toned[2])
        val minChannel = minOf(toned[0], toned[1], toned[2])
        assertTrue(value >= 0.49f, "value $value still near-black")
        // Saturated (visibly coloured, not grey) and still blue-dominant.
        assertTrue((value - minChannel) / value >= 0.6f)
        assertTrue(toned[2] > toned[0] && toned[2] > toned[1])
    }

    @Test
    fun `light tone caps a bright colour into the band and greys stay grey`() {
        val toned = lightSceneLineTone(0.976f, 0.983f, 0.32f)
        assertTrue(maxOf(toned[0], toned[1], toned[2]) <= 0.83f)
        // A near-grey keeps its greyness (no invented red cast from the
        // saturation floor); only the value band applies.
        val grey = lightSceneLineTone(0.7f, 0.7f, 0.7f)
        assertEquals(grey[0], grey[1], 1e-6f)
        assertEquals(grey[1], grey[2], 1e-6f)
    }

    private fun paletteOf(
        isDark: Boolean,
        grid: FloatArray,
        head: FloatArray = floatArrayOf(1f, 1f, 1f),
    ) = TripScenePalette(
        isDark = isDark,
        background = floatArrayOf(0.02f, 0.03f, 0.06f),
        grid = grid,
        head = head,
    )
}
