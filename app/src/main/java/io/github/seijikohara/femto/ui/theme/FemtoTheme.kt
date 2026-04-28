package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Root theme for the launcher.
 *
 * Color: Material You dynamic color (always available because minSdk = 33).
 * Falls back to a Bold Minimal monochrome scheme inside Compose previews.
 *
 * Typography: Bold Minimal weights and automotive sizing on top of M3 roles.
 *
 * Shape: M3 default squircle tokens (no override).
 */
@Composable
fun FemtoTheme(
    fontTheme: FontTheme = FontTheme.GEIST,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val colorScheme = when {
        inPreview && darkTheme -> DarkFallback
        inPreview -> LightFallback
        darkTheme -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    val pair = fontPairOf(fontTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = femtoTypography(pair.latin),
        content = content,
    )
}
