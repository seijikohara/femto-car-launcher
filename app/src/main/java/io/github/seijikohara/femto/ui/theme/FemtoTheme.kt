package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.materialkolor.rememberDynamicColorScheme
import io.github.seijikohara.femto.data.AccentColor

/**
 * Root theme for the launcher.
 *
 * Color: [AccentColor.DYNAMIC] (the default) uses Material You wallpaper-derived
 * color (always available because minSdk = 33); any other [accent] generates the
 * Material 3 scheme from a fixed preset seed. Falls back to a Bold Minimal
 * monochrome scheme inside Compose previews when on the dynamic path.
 *
 * Typography: Bold Minimal weights and automotive sizing on top of M3 roles.
 *
 * Shape: M3 default squircle tokens (no override).
 *
 * Weather glyph colours live in [weatherGlyphs] — read it directly from
 * any Composable that renders weather icons.
 */
@Composable
fun FemtoTheme(
    fontTheme: FontTheme = FontTheme.INTER,
    accent: AccentColor = AccentColor.DYNAMIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val seed = accent.accentSeedColor()
    MaterialTheme(
        colorScheme =
            when {
                // A fixed preset seed generates a full M3 scheme and works without a
                // context (so it also previews), so it takes precedence everywhere.
                seed != null -> rememberDynamicColorScheme(seedColor = seed, isDark = darkTheme)

                inPreview && darkTheme -> DarkFallback

                inPreview -> LightFallback

                darkTheme -> dynamicDarkColorScheme(context)

                else -> dynamicLightColorScheme(context)
            },
        typography = femtoTypography(fontPairOf(fontTheme).latin),
        content = content,
    )
}
