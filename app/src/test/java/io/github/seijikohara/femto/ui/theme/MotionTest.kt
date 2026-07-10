package io.github.seijikohara.femto.ui.theme

import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import io.github.seijikohara.femto.data.display.MotionTier
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Pins [Motion]'s MotionTier -> animation-spec mapping.
 *
 * [Motion.panelEnter] / [Motion.panelExit] return
 * [androidx.compose.animation.EnterTransition] /
 * [androidx.compose.animation.ExitTransition], whose equals is structural (the
 * sealed base delegates to the underlying `TransitionData`), so a spec built
 * here compares equal to the one the mapping returns — no reflection needed.
 * The per-tier content-fade spec behind [Motion.ContentCrossfade] routes
 * through a private helper, so it is covered indirectly by that composable
 * rather than asserted here.
 */
class MotionTest {
    // The accessibility invariant: a MotionTier.OFF user must never see a panel
    // animate in. snap() (a zero-duration spec, with no scale) is the instant,
    // no-motion transition; a regression that let OFF fall through to a tween
    // would break this equality.
    @Test
    fun `panelEnter is an instant snap fade for OFF`() {
        assertEquals(fadeIn(snap()), Motion.panelEnter(MotionTier.OFF))
    }

    @Test
    fun `panelExit is an instant snap fade for OFF`() {
        assertEquals(fadeOut(snap()), Motion.panelExit(MotionTier.OFF))
    }

    // STANDARD fades and scales, REDUCED fades over a shorter duration with no
    // scale, OFF snaps — three genuinely different specs. A copy-paste that
    // collapsed two tiers onto the same spec shrinks the set below three.
    @Test
    fun `panelEnter maps every tier to a distinct spec`() {
        val specs = MotionTier.entries.map { Motion.panelEnter(it) }
        assertEquals(MotionTier.entries.size, specs.toSet().size)
    }

    @Test
    fun `panelExit maps every tier to a distinct spec`() {
        val specs = MotionTier.entries.map { Motion.panelExit(it) }
        assertEquals(MotionTier.entries.size, specs.toSet().size)
    }
}
