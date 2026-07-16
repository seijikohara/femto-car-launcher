package io.github.seijikohara.femto.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Cross-dissolve between discrete content states without the mid-transition
 * opacity dip of the stock `Crossfade`.
 *
 * `Crossfade` composites the outgoing and incoming layers as two independent
 * source-over passes, so at the midpoint (both at alpha 0.5) combined coverage
 * drops to `1 − 0.5 × 0.5 = 0.75` — the backdrop bleeds through even where the
 * content is opaque in BOTH frames. On this launcher every fading surface sits
 * on translucent glass over the live map, so that dip reads as a visible flick.
 *
 * This primitive instead dissolves additively in premultiplied alpha: while
 * states mix, the container rasterizes to an offscreen buffer
 * ([CompositingStrategy.Offscreen], which confines the blend to this content)
 * and every child draws with [BlendMode.Plus]. The first child adds into an
 * empty (transparent) buffer, where Plus equals source-over, so one uniform
 * rule covers every child. Because all children share one [animationSpec] and
 * retarget on the same frame, their alphas always sum to exactly 1 — total
 * coverage is conserved through any interruption chain, shared-opaque pixels
 * stay fully opaque for every t (color simply lerps old → new), and unchanged
 * antialiased edges are preserved exactly. When settled, the single remaining
 * child falls back to plain source-over with no offscreen pass, so the resting
 * frame is pixel-identical to composing [content] directly (screenshot goldens
 * are unaffected).
 *
 * State bookkeeping mirrors `androidx.compose.animation.Crossfade`: keep every
 * state whose layer is still fading, prune to the target once the transition
 * settles. The replace-in-place branch of the original is dropped because keys
 * here are the states themselves (`Motion.ContentCrossfade`'s discrete-key
 * contract), so "same key, different state" cannot occur.
 *
 * [Motion.ContentCrossfade] is the only intended entry point; call sites keep
 * routing through it for the tier-aware spec.
 */
@Composable
internal fun <T> CrossDissolve(
    targetState: T,
    animationSpec: FiniteAnimationSpec<Float>,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val transition = updateTransition(targetState, label)
    val currentlyVisible = remember { mutableStateListOf(targetState) }
    if (targetState !in currentlyVisible) {
        currentlyVisible.add(targetState)
    }
    if (transition.currentState == transition.targetState &&
        (currentlyVisible.size > 1 || currentlyVisible.firstOrNull() != transition.targetState)
    ) {
        currentlyVisible.removeAll { it != transition.targetState }
    }
    Box(
        modifier.graphicsLayer {
            // Offscreen only while mixing: it exists to keep Plus inside this
            // content; a settled single layer composites directly, avoiding the
            // buffer round-trip on every quiet frame.
            compositingStrategy =
                if (currentlyVisible.size > 1) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        },
    ) {
        for (state in currentlyVisible) {
            key(state) {
                val alpha by transition.animateFloat(
                    transitionSpec = { animationSpec },
                    label = "alpha",
                ) { if (it == state) 1f else 0f }
                Box(
                    Modifier.graphicsLayer {
                        this.alpha = alpha
                        // Plus only while mixing — a settled child must composite
                        // source-over, or it would add into the live backdrop.
                        blendMode = if (currentlyVisible.size > 1) BlendMode.Plus else BlendMode.SrcOver
                    },
                ) {
                    content(state)
                }
            }
        }
    }
}
