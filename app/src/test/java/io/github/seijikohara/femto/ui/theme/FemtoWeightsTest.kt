package io.github.seijikohara.femto.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for the weight-tier resolver behind the font-weight setting.
 * Each step shifts the Bold Minimal tiers (light 200 / normal 400 / strong 600)
 * by 100, clamped to the valid [100, 900] FontWeight range so an extreme step
 * saturates rather than wraps. [FemtoWeights] carries the normal and strong tiers
 * directly; the light hero tier is not a field, so it is asserted through a
 * typography built with the same weights — exactly how the large-numeral
 * extensions resolve it at render time. Pure JVM — FontWeight is a plain value
 * holder that needs no Android runtime.
 */
class FemtoWeightsTest {
    @Test
    fun `step 0 resolves the design baseline tiers`() {
        assertTiers(step = 0, light = 200, normal = 400, strong = 600)
    }

    @Test
    fun `positive step one shifts every tier up by 100`() {
        assertTiers(step = 1, light = 300, normal = 500, strong = 700)
    }

    @Test
    fun `positive step two shifts every tier up by 200`() {
        assertTiers(step = 2, light = 400, normal = 600, strong = 800)
    }

    @Test
    fun `negative step one shifts every tier down by 100`() {
        assertTiers(step = -1, light = 100, normal = 300, strong = 500)
    }

    @Test
    fun `negative step two clamps the light tier at the 100 floor`() {
        assertTiers(step = -2, light = 100, normal = 200, strong = 400)
    }

    private fun assertTiers(
        step: Int,
        light: Int,
        normal: Int,
        strong: Int,
    ) {
        val weights = FemtoWeights.of(step)
        assertEquals(normal, weights.normal.weight)
        assertEquals(strong, weights.strong.weight)
        // The light hero tier is derived, not carried: read it back from a
        // typography built with these weights, matching how bigNumber / heroNumeral
        // resolve their default weight at render time.
        assertEquals(light, femtoTypography(FontFamily.Default, weights).lightWeight.weight)
    }
}
