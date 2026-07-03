package io.github.seijikohara.femto.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import io.github.seijikohara.femto.data.display.MotionTier

/**
 * One home for dashboard motion so every surface animates identically and the
 * user's MotionTier applies everywhere. STANDARD = fade + subtle scale; REDUCED
 * = short fade, no scale; OFF = instant. (Previously the maximize panels each
 * hand-repeated the 220/180/0.92f literals — this is their SSOT.)
 */
internal object Motion {
    private const val STANDARD_IN_MS = 220
    private const val STANDARD_OUT_MS = 180
    private const val REDUCED_MS = 120
    private const val ENTER_SCALE = 0.92f

    fun panelEnter(tier: MotionTier): EnterTransition =
        when (tier) {
            MotionTier.STANDARD -> fadeIn(tween(STANDARD_IN_MS)) +
                scaleIn(tween(STANDARD_IN_MS), initialScale = ENTER_SCALE)

            MotionTier.REDUCED -> fadeIn(tween(REDUCED_MS))

            MotionTier.OFF -> fadeIn(snap())
        }

    fun panelExit(tier: MotionTier): ExitTransition =
        when (tier) {
            MotionTier.STANDARD -> fadeOut(tween(STANDARD_OUT_MS)) +
                scaleOut(tween(STANDARD_OUT_MS), targetScale = ENTER_SCALE)

            MotionTier.REDUCED -> fadeOut(tween(REDUCED_MS))

            MotionTier.OFF -> fadeOut(snap())
        }

    fun presetCrossfade(tier: MotionTier): FiniteAnimationSpec<Float> =
        when (tier) {
            MotionTier.STANDARD -> tween(STANDARD_IN_MS)
            MotionTier.REDUCED -> tween(REDUCED_MS)
            MotionTier.OFF -> snap()
        }
}
