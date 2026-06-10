package io.github.seijikohara.femto.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import com.materialkolor.rememberDynamicColorScheme
import io.github.seijikohara.femto.data.AccentColor

/**
 * Root theme for the launcher.
 *
 * Color: [AccentColor.DYNAMIC] (the default) uses Material You wallpaper-derived
 * color (always available because minSdk = 33); any other [accent] generates the
 * Material 3 scheme from a fixed preset seed. Falls back to a Bold Minimal
 * monochrome scheme inside Compose previews when on the dynamic path. Scheme
 * changes (Light <-> Dark, accent, wallpaper) cross-fade rather than snap.
 *
 * Typography: Bold Minimal weights and automotive sizing on top of M3 roles.
 * [fontFamily] is the resolved typeface — the system default, or the user's
 * downloaded Google Fonts pair (Latin + CJK fallback); see [buildFontFamily].
 *
 * Shape: M3 default squircle tokens (no override).
 *
 * Weather glyph colours live in [weatherGlyphs] — read it directly from
 * any Composable that renders weather icons.
 */
@Composable
fun FemtoTheme(
    fontFamily: FontFamily = FontFamily.Default,
    accent: AccentColor = AccentColor.DYNAMIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val seed = accent.accentSeedColor()
    val target =
        when {
            // A fixed preset seed generates a full M3 scheme and works without a
            // context (so it also previews), so it takes precedence everywhere.
            seed != null -> rememberDynamicColorScheme(seedColor = seed, isDark = darkTheme)

            inPreview && darkTheme -> DarkFallback

            inPreview -> LightFallback

            darkTheme -> dynamicDarkColorScheme(context)

            else -> dynamicLightColorScheme(context)
        }
    MaterialTheme(
        colorScheme = target.animated(),
        typography = femtoTypography(fontFamily),
        content = content,
    )
}

// How long a theme change (Light <-> Dark, accent, wallpaper) takes to cross-fade.
private const val THEME_FADE_MILLIS = 500

/**
 * Animate every colour role toward this target scheme so a theme switch
 * cross-fades instead of snapping. The first composition starts at the target
 * (animateColorAsState has no separate initial value), so cold start renders
 * instantly. The rarely-used fixed-* roles are left to snap — nothing in the
 * launcher reads them.
 */
@Composable
private fun ColorScheme.animated(): ColorScheme =
    copy(
        primary = primary.fade(),
        onPrimary = onPrimary.fade(),
        primaryContainer = primaryContainer.fade(),
        onPrimaryContainer = onPrimaryContainer.fade(),
        inversePrimary = inversePrimary.fade(),
        secondary = secondary.fade(),
        onSecondary = onSecondary.fade(),
        secondaryContainer = secondaryContainer.fade(),
        onSecondaryContainer = onSecondaryContainer.fade(),
        tertiary = tertiary.fade(),
        onTertiary = onTertiary.fade(),
        tertiaryContainer = tertiaryContainer.fade(),
        onTertiaryContainer = onTertiaryContainer.fade(),
        background = background.fade(),
        onBackground = onBackground.fade(),
        surface = surface.fade(),
        onSurface = onSurface.fade(),
        surfaceVariant = surfaceVariant.fade(),
        onSurfaceVariant = onSurfaceVariant.fade(),
        surfaceTint = surfaceTint.fade(),
        inverseSurface = inverseSurface.fade(),
        inverseOnSurface = inverseOnSurface.fade(),
        error = error.fade(),
        onError = onError.fade(),
        errorContainer = errorContainer.fade(),
        onErrorContainer = onErrorContainer.fade(),
        outline = outline.fade(),
        outlineVariant = outlineVariant.fade(),
        scrim = scrim.fade(),
        surfaceBright = surfaceBright.fade(),
        surfaceDim = surfaceDim.fade(),
        surfaceContainer = surfaceContainer.fade(),
        surfaceContainerHigh = surfaceContainerHigh.fade(),
        surfaceContainerHighest = surfaceContainerHighest.fade(),
        surfaceContainerLow = surfaceContainerLow.fade(),
        surfaceContainerLowest = surfaceContainerLowest.fade(),
    )

@Composable
private fun Color.fade(): Color = animateColorAsState(this, tween(THEME_FADE_MILLIS), label = "themeColor").value
