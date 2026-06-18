package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.github.seijikohara.femto.data.display.DEFAULT_GLASS_BLUR_DP
import io.github.seijikohara.femto.data.display.DEFAULT_GLASS_TINT_SCALE

/**
 * Glass blur + tint for the map overlays, threaded from [DisplaySettings] down to
 * the clock / speed overlays like [MapConfig]. The defaults mirror the persisted
 * defaults ([DEFAULT_GLASS_BLUR_DP] / [DEFAULT_GLASS_TINT_SCALE]) so a
 * config-less preview matches a fresh install. [tintScale] is the absolute tint
 * opacity percent (0 = clear glass, 100 = fully opaque surface).
 */
internal data class GlassConfig(
    val blurRadius: Dp = DEFAULT_GLASS_BLUR_DP.dp,
    val tintScale: Int = DEFAULT_GLASS_TINT_SCALE,
)

/**
 * Shared frosted-glass backdrop for the map overlays (clock / speed): blur the
 * map captured via [hazeState] and lay the surface tint over it. [tintScale] is
 * the absolute tint opacity percent (0 = clear, 100 = opaque); the overlays stop
 * repeating the `hazeEffect` lambda.
 */
@Composable
internal fun Modifier.glassEffect(
    hazeState: HazeState,
    blurRadius: Dp,
    tintScale: Int,
): Modifier {
    val tintAlpha = glassTintAlpha(tintScale)
    // Capture the surface color outside the draw-time hazeEffect block, which
    // cannot read MaterialTheme.
    val surfaceColor = MaterialTheme.colorScheme.surface
    return hazeEffect(state = hazeState) {
        backgroundColor = surfaceColor
        tints = listOf(HazeTint(surfaceColor.copy(alpha = tintAlpha)))
        this.blurRadius = blurRadius
    }
}

/**
 * The complete glass-chrome recipe shared by every map overlay (clock, speed,
 * map controls) and the dashboard cards: clip to [shape] and frost the backdrop
 * via [glassEffect]. No outline — the panels read as frameless frosted glass over
 * the map, the tint + rounded clip carrying the panel shape.
 */
@Composable
internal fun Modifier.glassChrome(
    shape: Shape,
    hazeState: HazeState,
    glassConfig: GlassConfig,
): Modifier =
    clip(shape)
        .glassEffect(hazeState, glassConfig.blurRadius, glassConfig.tintScale)

/**
 * Resolve the tint alpha from the user's absolute opacity [tintPercent]
 * (0 = clear glass, 100 = opaque surface), clamped to a valid alpha. Pure, so the
 * mapping is unit-testable without Compose.
 */
internal fun glassTintAlpha(tintPercent: Int): Float = (tintPercent / 100f).coerceIn(0f, 1f)
