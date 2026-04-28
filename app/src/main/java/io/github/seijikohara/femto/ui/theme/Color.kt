package io.github.seijikohara.femto.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Bold Minimal fallback palettes used when Dynamic Color is unavailable
 * (e.g. Compose previews running outside an Activity context). Production
 * runtime always pulls from [androidx.compose.material3.dynamicLightColorScheme].
 */

private val Ink = Color(0xFF111111)
private val Paper = Color(0xFFFAFAFA)
private val PaperPure = Color.White
private val Night = Color(0xFF050505)
private val NightSurface = Color(0xFF0A0A0A)
private val Bone = Color.White

internal val LightFallback = lightColorScheme(
    primary = Ink,
    onPrimary = PaperPure,
    background = Paper,
    onBackground = Ink,
    surface = PaperPure,
    onSurface = Ink,
)

internal val DarkFallback = darkColorScheme(
    primary = Bone,
    onPrimary = Night,
    background = Night,
    onBackground = Bone,
    surface = NightSurface,
    onSurface = Bone,
)
