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

// Muted monochrome roles derived from the Ink/Paper and Bone/Night anchors.
// The dashboard reads onSurfaceVariant, outlineVariant, surfaceContainer,
// surfaceContainerHigh, primaryContainer, onPrimaryContainer, outline, and
// tertiary; left unset they fall back to the M3 baseline purple/teal, which
// contradicts the Bold Minimal monochrome aesthetic in previews. Deriving
// them here as greys keeps the preview fallback in the same palette family as
// the production dynamic-color path.

// Light: greys stepping from Ink towards Paper.
private val InkMuted = Color(0xFF5A5A5A) // secondary ink for variant text
private val InkHairline = Color(0xFFD9D9D9) // hairline outlines on paper
private val PaperRaised = Color(0xFFF0F0F0) // first container tier above surface
private val PaperRaisedHigh = Color(0xFFE8E8E8) // second container tier

// Dark: greys stepping from Bone towards Night.
private val BoneMuted = Color(0xFFB0B0B0) // secondary bone text for variant roles
private val BoneHairline = Color(0xFF2E2E2E) // hairline outlines on night
private val NightRaised = Color(0xFF161616) // first container tier above surface
private val NightRaisedHigh = Color(0xFF1F1F1F) // second container tier

internal val LightFallback =
    lightColorScheme(
        primary = Ink,
        onPrimary = PaperPure,
        primaryContainer = PaperRaisedHigh,
        onPrimaryContainer = Ink,
        tertiary = Ink,
        background = Paper,
        onBackground = Ink,
        surface = PaperPure,
        onSurface = Ink,
        onSurfaceVariant = InkMuted,
        surfaceContainer = PaperRaised,
        surfaceContainerHigh = PaperRaisedHigh,
        outline = InkMuted,
        outlineVariant = InkHairline,
    )

// Rainbow stops for the Settings DYNAMIC accent swatch's sweep gradient; the
// first hue repeats at the end so the sweep closes seamlessly. Decorative only
// (signals "automatic / wallpaper-derived"), not theme color roles — they live
// here because hardcoded hex is confined to this file and AccentColors.kt
// (CLAUDE.md#design-system).
internal val DynamicAccentSweep =
    listOf(
        Color(0xFFEF5350),
        Color(0xFFFFCA28),
        Color(0xFF66BB6A),
        Color(0xFF26C6DA),
        Color(0xFF42A5F5),
        Color(0xFFAB47BC),
        Color(0xFFEF5350),
    )

internal val DarkFallback =
    darkColorScheme(
        primary = Bone,
        onPrimary = Night,
        primaryContainer = NightRaisedHigh,
        onPrimaryContainer = Bone,
        tertiary = Bone,
        background = Night,
        onBackground = Bone,
        surface = NightSurface,
        onSurface = Bone,
        onSurfaceVariant = BoneMuted,
        surfaceContainer = NightRaised,
        surfaceContainerHigh = NightRaisedHigh,
        outline = BoneMuted,
        outlineVariant = BoneHairline,
    )
