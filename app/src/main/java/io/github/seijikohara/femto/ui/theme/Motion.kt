package io.github.seijikohara.femto.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    /**
     * Tier-aware fade spec for a discrete content swap — the preset switch and
     * every card-level `Crossfade` (album art, track metadata, a weather/calendar
     * refresh, the driving location strip). STANDARD/REDUCED fade over their
     * duration; OFF snaps instantly, so a MotionTier.OFF user never sees any of
     * these dissolve. [targetState] at the call site must be a discrete identity
     * (a track key, a fetch timestamp, a snapshot) — never a per-frame value
     * (a progress fraction, a clock tick) — or the fade thrashes every frame
     * instead of firing once per real content change.
     */
    fun contentFadeSpec(tier: MotionTier): FiniteAnimationSpec<Float> =
        when (tier) {
            MotionTier.STANDARD -> tween(STANDARD_IN_MS)
            MotionTier.REDUCED -> tween(REDUCED_MS)
            MotionTier.OFF -> snap()
        }

    /**
     * Thin [Crossfade] wrapper that always routes through [contentFadeSpec], so
     * every content-swap fade in the dashboard honors [tier] the same way
     * instead of each call site re-deriving (or forgetting) the tier-aware spec.
     */
    @Composable
    fun <T> ContentCrossfade(
        targetState: T,
        tier: MotionTier,
        label: String,
        modifier: Modifier = Modifier,
        content: @Composable (T) -> Unit,
    ) = Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = contentFadeSpec(tier),
        label = label,
        content = content,
    )
}
