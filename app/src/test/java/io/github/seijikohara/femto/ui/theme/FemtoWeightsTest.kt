package io.github.seijikohara.femto.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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
 * extensions resolve it at render time. The emphasized type roles are covered
 * here too: they are the base roles raised to the strong tier, so they must
 * shift with the same step. Pure JVM — FontWeight and TextStyle are plain value
 * holders that need no Android runtime.
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

    // Left unset, an emphasized role keeps the M3 token style (default font,
    // M3 size), so an Expressive component would drop the user's typeface and
    // the rem scale. Each one must carry its base role's family, size, line
    // height and tracking.
    @Test
    fun `every emphasized role keeps its base role's family size line height and tracking`() {
        val typography = femtoTypography(FontFamily.Serif, FemtoWeights.Default, letterSpacingEm = 0f)
        EmphasizedPairs.forEach { (name, pair) ->
            val (base, emphasized) = pair(typography)
            assertEquals(FontFamily.Serif, emphasized.fontFamily, name)
            assertEquals(base.fontSize, emphasized.fontSize, name)
            assertEquals(base.lineHeight, emphasized.lineHeight, name)
            assertEquals(base.letterSpacing, emphasized.letterSpacing, name)
        }
    }

    // Emphasis is the strong tier, which also tracks the user's weight step;
    // the three-weight system adds no heavier tier for the roles already strong.
    @Test
    fun `every emphasized role runs at the strong tier of the weight step`() {
        val weights = FemtoWeights.of(1)
        val typography = femtoTypography(FontFamily.Default, weights)
        EmphasizedPairs.forEach { (name, pair) ->
            assertEquals(weights.strong, pair(typography).second.fontWeight, name)
        }
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

    private companion object {
        // Every base role paired with its emphasized counterpart, by name so a
        // failure names the role.
        val EmphasizedPairs: List<Pair<String, (Typography) -> Pair<TextStyle, TextStyle>>> =
            listOf(
                "displayLarge" to { it.displayLarge to it.displayLargeEmphasized },
                "displayMedium" to { it.displayMedium to it.displayMediumEmphasized },
                "displaySmall" to { it.displaySmall to it.displaySmallEmphasized },
                "headlineLarge" to { it.headlineLarge to it.headlineLargeEmphasized },
                "headlineMedium" to { it.headlineMedium to it.headlineMediumEmphasized },
                "headlineSmall" to { it.headlineSmall to it.headlineSmallEmphasized },
                "titleLarge" to { it.titleLarge to it.titleLargeEmphasized },
                "titleMedium" to { it.titleMedium to it.titleMediumEmphasized },
                "titleSmall" to { it.titleSmall to it.titleSmallEmphasized },
                "bodyLarge" to { it.bodyLarge to it.bodyLargeEmphasized },
                "bodyMedium" to { it.bodyMedium to it.bodyMediumEmphasized },
                "bodySmall" to { it.bodySmall to it.bodySmallEmphasized },
                "labelLarge" to { it.labelLarge to it.labelLargeEmphasized },
                "labelMedium" to { it.labelMedium to it.labelMediumEmphasized },
                "labelSmall" to { it.labelSmall to it.labelSmallEmphasized },
            )
    }
}
