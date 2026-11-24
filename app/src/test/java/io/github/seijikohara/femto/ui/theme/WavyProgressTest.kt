package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.display.MotionTier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Pins how the shared wavy progress indicators follow the Motion setting, once per
 * tier: the tier -> wave parameters mapping every overload takes its amplitude and
 * speed from, the scheme -> tier lookup the composables read it through, and that
 * the overloads compose.
 *
 * The wave's pixels are not asserted: a travelling wave never settles, and the
 * Material indicators do not expose the parameters they were given. The mapping is
 * therefore pinned as data ([waveSpec], used by all three overloads) and the
 * composables are checked for what they publish (their progress semantics). The
 * clock is held so STANDARD's travelling wave cannot keep a finder waiting for an
 * idle composition.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33])
internal class WavyProgressTest(
    private val tier: MotionTier,
    private val expectedAmplitudeScale: Float,
    private val travels: Boolean,
) {
    @get:Rule
    val rule = createComposeRule()

    // The wavelengths the overloads pass, so a speed that ignored the wavelength (a
    // constant) would fail for at least one of them.
    private val wavelengths: List<Dp> =
        listOf(
            WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
            WavyProgressIndicatorDefaults.LinearIndeterminateWavelength,
            WavyProgressIndicatorDefaults.CircularWavelength,
            7.dp,
        )

    @Test
    fun `the tier scales the amplitude`() {
        wavelengths.forEach { assertEquals(expectedAmplitudeScale, waveSpec(tier, it).amplitudeScale) }
    }

    @Test
    fun `the tier travels the wave at one wavelength per second or holds it still`() {
        wavelengths.forEach { assertEquals(if (travels) it else 0.dp, waveSpec(tier, it).speed) }
    }

    @Test
    fun `the scheme FemtoTheme installs for the tier reads back as that tier`() {
        assertEquals(tier, Motion.tier(Motion.scheme(tier)))
    }

    // The composables derive the tier from the theme, never from a parameter, so the
    // theme's installed scheme must resolve back to the tier FemtoTheme was given.
    @Test
    fun `the theme the composables read resolves to the tier FemtoTheme was given`() {
        var seen: MotionTier? = null
        setContent { seen = Motion.tier(MaterialTheme.motionScheme) }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(tier, seen)
    }

    @Test
    fun `a determinate bar publishes its progress`() {
        setContent { FemtoLinearProgressIndicator(progress = { 0.42f }, modifier = Modifier.testTag(BAR)) }
        rule.onNodeWithTag(BAR).assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.42f, 0f..1f)))
    }

    // The indeterminate overloads loop their own progress forever, so the check is
    // that each one composes and lays out, the thin ring included.
    @Test
    fun `the indeterminate bar and ring compose`() {
        setContent {
            FemtoLinearProgressIndicator(modifier = Modifier.testTag(BAR).fillMaxWidth())
            FemtoCircularProgressIndicator(modifier = Modifier.testTag(RING), strokeWidth = 2.dp)
        }
        rule.onNodeWithTag(BAR).assertExists()
        rule.onNodeWithTag(RING).assertExists()
    }

    private fun setContent(content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { FemtoTheme(motionTier = tier) { content() } }
    }

    companion object {
        private const val BAR = "wavy-bar"
        private const val RING = "wavy-ring"

        // STANDARD waves and travels; REDUCED keeps the shape and holds it still; OFF
        // has no amplitude, so no wave is drawn and none can move.
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun tiers(): List<Array<Any>> =
            listOf(
                arrayOf(MotionTier.STANDARD, 1f, true),
                arrayOf(MotionTier.REDUCED, 1f, false),
                arrayOf(MotionTier.OFF, 0f, false),
            )
    }
}
