package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * Glass blur strength for the map overlays, threaded from [DisplaySettings] down
 * to the clock / speed overlays like [MapConfig]. The defaults reproduce the
 * pre-settings look (24 dp blur, 100% tint).
 */
internal data class GlassConfig(
    val blurRadius: Dp = FemtoDimens.GlassBlurRadius,
    val tintScale: Int = 100,
)

/**
 * Shared frosted-glass backdrop for the map overlays (clock / speed): blur the
 * map captured via [hazeState] and lay the surface tint over it. The light/dark
 * base-alpha pick and the user's percent [tintScale] collapse into one alpha
 * here, so the overlays stop repeating the `hazeEffect` lambda.
 */
@Composable
internal fun Modifier.glassEffect(
    hazeState: HazeState,
    blurRadius: Dp,
    tintScale: Int,
): Modifier {
    val baseAlpha = if (isSystemInDarkTheme()) FemtoDimens.GlassBgAlphaDark else FemtoDimens.GlassBgAlphaLight
    val tintAlpha = glassTintAlpha(baseAlpha, tintScale)
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
 * Resolve the effective tint alpha from a [baseAlpha] and the user's percent
 * [tintScale] (100 = unchanged), clamped to a valid alpha. Pure, so the scaling
 * is unit-testable without Compose.
 */
internal fun glassTintAlpha(
    baseAlpha: Float,
    tintScale: Int,
): Float = (baseAlpha * tintScale / 100f).coerceIn(0f, 1f)
