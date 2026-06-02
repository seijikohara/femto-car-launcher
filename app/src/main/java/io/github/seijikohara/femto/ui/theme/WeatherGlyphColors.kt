package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour set for weather glyphs.
 *
 * Material You dynamic colour does not produce reliable warm-cool pairings
 * for "sunny" vs "cloudy" vs "moon" — they all collapse to the user's
 * accent. The dashboard relies on those distinctions being immediately
 * readable at a glance, so the glyph palette is curated separately.
 */
@Immutable
data class WeatherGlyphColors(
    val sun: Color,
    val moon: Color,
    val cloudSun: Color,
    val cloud: Color,
)

internal val DarkWeatherGlyphs =
    WeatherGlyphColors(
        sun = Color(0xFFFFD166),
        moon = Color(0xFF9EB8DA),
        cloudSun = Color(0xFFD6A06A),
        cloud = Color(0xFF93A3B8),
    )

internal val LightWeatherGlyphs =
    WeatherGlyphColors(
        sun = Color(0xFFE08A00),
        moon = Color(0xFF5D7A9E),
        cloudSun = Color(0xFFB07A30),
        cloud = Color(0xFF6F7E90),
    )

/**
 * Resolve the weather glyph palette for the current theme.
 *
 * Exposed as a `@Composable` function (rather than a `CompositionLocal`)
 * because ktlint's Compose rules discourage adding new CompositionLocals
 * unless they are unavoidable, and the dark / light selection is a single
 * `isSystemInDarkTheme()` check.
 */
@Composable
@ReadOnlyComposable
internal fun weatherGlyphs(): WeatherGlyphColors = if (isSystemInDarkTheme()) DarkWeatherGlyphs else LightWeatherGlyphs
