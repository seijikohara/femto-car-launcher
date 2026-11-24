package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * tier: the tier -> wave mapping, the scheme -> tier lookup the composables read it
 * through, and that every overload of both shapes composes.
 *
 * The wave's pixels are not asserted: a travelling wave never settles, so the
 * mapping is pinned as data and the composables are checked for what they publish
 * (their progress semantics). The clock is held so STANDARD's travelling wave
 * cannot keep a finder waiting for an idle composition.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33])
internal class WavyProgressTest(
    private val tier: MotionTier,
    private val expectedWave: WaveMotion,
) {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the tier maps to its wave`() {
        assertEquals(expectedWave, waveMotion(tier))
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

    @Test
    fun `a determinate ring publishes its progress`() {
        setContent { FemtoCircularProgressIndicator(progress = { 0.42f }, modifier = Modifier.testTag(RING)) }
        rule.onNodeWithTag(RING).assert(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.42f, 0f..1f)))
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
                arrayOf(MotionTier.STANDARD, WaveMotion(waving = true, moving = true)),
                arrayOf(MotionTier.REDUCED, WaveMotion(waving = true, moving = false)),
                arrayOf(MotionTier.OFF, WaveMotion(waving = false, moving = false)),
            )
    }
}
