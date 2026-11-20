package io.github.seijikohara.femto.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MotionScheme
import io.github.seijikohara.femto.data.display.MotionTier
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * Pins [Motion]'s MotionTier -> animation-spec mapping.
 *
 * [Motion.panelEnter] / [Motion.panelExit] return
 * [androidx.compose.animation.EnterTransition] /
 * [androidx.compose.animation.ExitTransition], whose equals is structural (the
 * sealed base delegates to the underlying `TransitionData`), so a spec built
 * here compares equal to the one the mapping returns — no reflection needed.
 * The panel transitions are asserted with the scheme [Motion.scheme] gives the
 * same tier, which is the pairing FemtoTheme installs in production. The
 * per-tier content-fade spec behind [Motion.ContentCrossfade] routes through a
 * private helper, so it is covered indirectly by that composable rather than
 * asserted here.
 */
class MotionTest {
    // Decided in #427: STANDARD takes the expressive scheme, REDUCED the
    // standard one (no overshoot, faster settle on a 30 Hz head-unit panel).
    @Test
    fun `scheme maps STANDARD to the expressive Material scheme`() {
        assertSame(MotionScheme.expressive(), Motion.scheme(MotionTier.STANDARD))
    }

    @Test
    fun `scheme maps REDUCED to the standard Material scheme`() {
        assertSame(MotionScheme.standard(), Motion.scheme(MotionTier.REDUCED))
    }

    // The accessibility invariant for Material components: a MotionTier.OFF
    // user must never see one animate, so every spec the OFF scheme hands out
    // is the zero-duration snap(). A regression that fell back to a built-in
    // scheme would return a spring here.
    @Test
    fun `scheme for OFF returns a snap spec from every member`() {
        val scheme = Motion.scheme(MotionTier.OFF)
        val specs: List<FiniteAnimationSpec<Float>> =
            listOf(
                scheme.defaultSpatialSpec(),
                scheme.fastSpatialSpec(),
                scheme.slowSpatialSpec(),
                scheme.defaultEffectsSpec(),
                scheme.fastEffectsSpec(),
                scheme.slowEffectsSpec(),
            )
        specs.forEach { assertEquals(snap(), it) }
    }

    // The same invariant for the panels: snap() (with no scale) is the instant,
    // no-motion transition; a regression that let OFF fall through to a
    // scheme-timed spec would break this equality. Passed the expressive scheme
    // on purpose: OFF must snap whatever scheme the caller hands in.
    @Test
    fun `panelEnter is an instant snap fade for OFF`() {
        assertEquals(fadeIn(snap()), Motion.panelEnter(MotionTier.OFF, MotionScheme.expressive()))
    }

    @Test
    fun `panelExit is an instant snap fade for OFF`() {
        assertEquals(fadeOut(snap()), Motion.panelExit(MotionTier.OFF, MotionScheme.expressive()))
    }

    // STANDARD fades and scales, REDUCED fades with no scale, OFF snaps — three
    // genuinely different specs. A copy-paste that collapsed two tiers onto the
    // same spec shrinks the set below three.
    @Test
    fun `panelEnter maps every tier to a distinct spec`() {
        val specs = MotionTier.entries.map { Motion.panelEnter(it, Motion.scheme(it)) }
        assertEquals(MotionTier.entries.size, specs.toSet().size)
    }

    @Test
    fun `panelExit maps every tier to a distinct spec`() {
        val specs = MotionTier.entries.map { Motion.panelExit(it, Motion.scheme(it)) }
        assertEquals(MotionTier.entries.size, specs.toSet().size)
    }

    // REDUCED exists to drop the scale, so its panel transitions are a bare
    // fade timed by the theme's effects spec.
    @Test
    fun `panelEnter is a fade only for REDUCED`() {
        val scheme = Motion.scheme(MotionTier.REDUCED)
        assertEquals(fadeIn(scheme.defaultEffectsSpec()), Motion.panelEnter(MotionTier.REDUCED, scheme))
    }

    // A closing panel must not bounce: the exit rides the effects spec, never
    // the expressive spatial spring that overshoots. Swapping only the spatial
    // specs out of the scheme must therefore leave the exit unchanged.
    @Test
    fun `panelExit for STANDARD never reads the spatial specs`() {
        val scheme = Motion.scheme(MotionTier.STANDARD)
        assertEquals(
            Motion.panelExit(MotionTier.STANDARD, scheme),
            Motion.panelExit(MotionTier.STANDARD, SnappingSpatial(scheme)),
        )
    }

    // The control for the test above: the enter does ride the spatial spec (its
    // scale), so the same swap changes it — proving the swap is observable.
    @Test
    fun `panelEnter for STANDARD scales on the spatial spec`() {
        val scheme = Motion.scheme(MotionTier.STANDARD)
        assertNotEquals(
            Motion.panelEnter(MotionTier.STANDARD, scheme),
            Motion.panelEnter(MotionTier.STANDARD, SnappingSpatial(scheme)),
        )
    }

    /** [base] with its three spatial specs replaced by snap(); effects unchanged. */
    private class SnappingSpatial(
        private val base: MotionScheme,
    ) : MotionScheme by base {
        override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

        override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

        override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    }
}
