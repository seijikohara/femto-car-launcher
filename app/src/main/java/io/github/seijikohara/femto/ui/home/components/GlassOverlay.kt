package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.border
import androidx.compose.foundation.preferKeepClear
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.seijikohara.femto.data.display.DEFAULT_GLASS_BLUR_DP
import io.github.seijikohara.femto.data.display.DEFAULT_GLASS_SHADOW_ENABLED
import io.github.seijikohara.femto.data.display.DEFAULT_GLASS_SHADOW_INTENSITY
import io.github.seijikohara.femto.data.display.DEFAULT_GLASS_SHADOW_SIZE_DP
import io.github.seijikohara.femto.data.display.DEFAULT_GLASS_TINT_SCALE
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme

/**
 * Glass blur + tint, plus an optional outline and drop shadow, for the map
 * overlays and dashboard cards; threaded from [DisplaySettings] down to the clock
 * / speed overlays and the dock like [MapConfig]. The defaults mirror the
 * persisted defaults so a config-less preview matches a fresh install. [tintScale]
 * is the absolute tint opacity percent (0 = clear glass, 100 = fully opaque
 * surface). [showBorder] defaults off (the frameless frosted-glass look);
 * [shadowEnabled] defaults on — a subtle theme-aware drop shadow. [shadowIntensity]
 * is a 0-100 percent and [shadowSize] the shadow's blur / elevation.
 */
internal data class GlassConfig(
    val blurRadius: Dp = DEFAULT_GLASS_BLUR_DP.dp,
    val tintScale: Int = DEFAULT_GLASS_TINT_SCALE,
    val showBorder: Boolean = false,
    val shadowEnabled: Boolean = DEFAULT_GLASS_SHADOW_ENABLED,
    val shadowIntensity: Int = DEFAULT_GLASS_SHADOW_INTENSITY,
    val shadowSize: Dp = DEFAULT_GLASS_SHADOW_SIZE_DP.dp,
)

/**
 * Shared frosted-glass backdrop for the map overlays (clock / speed): blur the
 * map captured via [hazeState] and lay the surface tint over it. [tintScale] is
 * the absolute tint opacity percent (0 = clear, 100 = opaque); the overlays stop
 * repeating the Haze blur style.
 *
 * Two arguments keep the Haze 1 rendering: [HazeInput.Sources] samples only the
 * registered map source (the [HazeInput.Backdrop] input can switch to native
 * window pixels behind an experimental Haze flag), and
 * [HazePerformanceMode.Quality] keeps the full-resolution blur input that the
 * Haze 2 default (Balanced) downsamples.
 */
@Composable
internal fun Modifier.glassEffect(
    hazeState: HazeState,
    blurRadius: Dp,
    tintScale: Int,
): Modifier {
    val tintAlpha = glassTintAlpha(tintScale)
    val surfaceColor = MaterialTheme.colorScheme.surface
    return hazeBlur(
        input = HazeInput.Sources(hazeState),
        style =
            HazeBlurStyle {
                backgroundColor(surfaceColor)
                colorEffects(listOf(HazeColorEffect.tint(surfaceColor.copy(alpha = tintAlpha))))
                blurRadius(blurRadius)
            },
        performanceMode = HazePerformanceMode.Quality,
    )
}

// Hairline width for the optional glass outline.
private val GlassBorderWidth = 1.dp

/**
 * The complete glass-chrome recipe shared by every map overlay (clock, speed, map
 * controls), the dashboard cards, and the dock: an optional drop shadow, clip to
 * [shape], frost the backdrop via [glassEffect], and an optional outline. The
 * shadow is a default-on user setting; the border a default-off opt-in
 * ([GlassConfig]).
 *
 * The shadow colour is theme-aware: light theme drops a black shadow, while dark
 * theme lifts the panel off the dark map with a faint white glow (a black shadow
 * would sink into the dark backdrop). The border is the same hairline as the dock
 * / speed-panel dividers — `outlineVariant` at [FemtoDimens.DividerAlpha].
 *
 * Every glass surface is also a keep-clear area: the system then moves another
 * app's picture-in-picture window off the cards, the overlays and the dock into
 * the open map, where it honours app keep-clear areas (Android 13 QPR3 and later).
 * An ordinary app cannot place or size that window any other way (#390).
 */
@Composable
internal fun Modifier.glassChrome(
    shape: Shape,
    hazeState: HazeState,
    glassConfig: GlassConfig,
): Modifier =
    this
        .preferKeepClear()
        .then(
            if (glassConfig.shadowEnabled && glassConfig.shadowSize > 0.dp) {
                // Theme is read only on the enabled path, so a default (shadow-off)
                // surface keeps its narrow MaterialTheme-only dependency. clip = false:
                // the explicit clip below frosts the backdrop; the shadow only needs to
                // draw in the margin outside the shape.
                val shadowColor =
                    (if (LocalFemtoDarkTheme.current) Color.White else Color.Black)
                        .copy(alpha = glassShadowAlpha(glassConfig.shadowIntensity))
                Modifier.shadow(
                    elevation = glassConfig.shadowSize,
                    shape = shape,
                    clip = false,
                    ambientColor = shadowColor,
                    spotColor = shadowColor,
                )
            } else {
                Modifier
            },
        ).clip(shape)
        .glassEffect(hazeState, glassConfig.blurRadius, glassConfig.tintScale)
        .then(
            if (glassConfig.showBorder) {
                // Same hairline as the dock / speed-panel dividers: outlineVariant at the
                // shared DividerAlpha, so the opt-in panel outline reads as one family.
                Modifier.border(
                    GlassBorderWidth,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha),
                    shape,
                )
            } else {
                Modifier
            },
        )

/**
 * Resolve the tint alpha from the user's absolute opacity [tintPercent]
 * (0 = clear glass, 100 = opaque surface), clamped to a valid alpha. Pure, so the
 * mapping is unit-testable without Compose.
 */
internal fun glassTintAlpha(tintPercent: Int): Float = (tintPercent / 100f).coerceIn(0f, 1f)

/**
 * Resolve the shadow-colour alpha from the user's intensity [intensityPercent]
 * (0 = invisible, 100 = full strength), clamped to a valid alpha. Pure, so the
 * mapping is unit-testable without Compose.
 */
internal fun glassShadowAlpha(intensityPercent: Int): Float = (intensityPercent / 100f).coerceIn(0f, 1f)
