package io.github.seijikohara.femto.ui.theme

import androidx.compose.runtime.Composable
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
data class WeatherGlyphColors(
    val sun: Color,
    val moon: Color,
    val cloudSun: Color,
    val cloud: Color,
    // Precipitation families. Every wet or stormy condition used to share
    // [cloud], which made rain, snow and thunder indistinguishable at a glance;
    // the icon shape now differs too (see glyphIconFor), and the tint reinforces
    // it in the one axis a driver reads fastest.
    val rain: Color,
    val snow: Color,
    val thunder: Color,
)

internal val DarkWeatherGlyphs =
    WeatherGlyphColors(
        sun = Color(0xFFFFD166),
        moon = Color(0xFF9EB8DA),
        cloudSun = Color(0xFFD6A06A),
        cloud = Color(0xFF93A3B8),
        rain = Color(0xFF7FB3E8),
        snow = Color(0xFFCFE3F5),
        thunder = Color(0xFFF6B73C),
    )

internal val LightWeatherGlyphs =
    WeatherGlyphColors(
        sun = Color(0xFFE08A00),
        moon = Color(0xFF5D7A9E),
        cloudSun = Color(0xFFB07A30),
        cloud = Color(0xFF6F7E90),
        rain = Color(0xFF2F6DA3),
        snow = Color(0xFF5F87A8),
        thunder = Color(0xFFB87A00),
    )

/**
 * Resolve the weather glyph palette for the current theme.
 *
 * Reads [LocalFemtoDarkTheme] (not `isSystemInDarkTheme()`) so the palette
 * follows the rendered theme when the user forces a ThemeMode in Settings —
 * the system flag would put light-palette glyphs on dark surfaces.
 */
@Composable
@ReadOnlyComposable
internal fun weatherGlyphs(): WeatherGlyphColors =
    if (LocalFemtoDarkTheme.current) DarkWeatherGlyphs else LightWeatherGlyphs

/**
 * Data-graphics colours for the weather panel's charts: the temperature ramp
 * behind the 24 h curve and the daily range bars, the precipitation blue, and
 * the standard five-step UV scale. Curated for the same reason as the glyphs —
 * dynamic colour cannot express a cold→hot ramp or the UV convention — with a
 * luminous set for the dark glass and a deeper jewel set for light.
 */
data class WeatherDataColors(
    /** Temperature stops (°C → colour), sorted ascending; lerped between. */
    val tempStops: List<Pair<Float, Color>>,
    val precipitation: Color,
    /** WHO UV bands: low / moderate / high / very high / extreme. */
    val uvScale: List<Color>,
)

internal val DarkWeatherData =
    WeatherDataColors(
        tempStops =
            listOf(
                -10f to Color(0xFF8A7BFF),
                0f to Color(0xFF5BA8FF),
                10f to Color(0xFF4DD0C4),
                16f to Color(0xFF8BD47E),
                22f to Color(0xFFFFC94D),
                28f to Color(0xFFFF9A4D),
                36f to Color(0xFFFF5D5D),
            ),
        precipitation = Color(0xFF6EC1FF),
        uvScale =
            listOf(
                Color(0xFF8BD47E),
                Color(0xFFFFD166),
                Color(0xFFFF9A4D),
                Color(0xFFFF5D5D),
                Color(0xFFB08CFF),
            ),
    )

internal val LightWeatherData =
    WeatherDataColors(
        tempStops =
            listOf(
                -10f to Color(0xFF5B4FD1),
                0f to Color(0xFF2F6FD0),
                10f to Color(0xFF14907F),
                16f to Color(0xFF3F9A3F),
                22f to Color(0xFFC98A00),
                28f to Color(0xFFC96A1E),
                36f to Color(0xFFC94040),
            ),
        precipitation = Color(0xFF1E6FBF),
        uvScale =
            listOf(
                Color(0xFF3F9A3F),
                Color(0xFFC98A00),
                Color(0xFFC96A1E),
                Color(0xFFC94040),
                Color(0xFF7E57C2),
            ),
    )

/** Resolve the weather data-graphics palette for the rendered theme. */
@Composable
@ReadOnlyComposable
internal fun weatherDataColors(): WeatherDataColors =
    if (LocalFemtoDarkTheme.current) DarkWeatherData else LightWeatherData
