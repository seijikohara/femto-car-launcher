package io.github.seijikohara.femto.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Automotive sizing constants that override M3 defaults for in-vehicle use.
 * The car-specific minimums are larger than M3's phone defaults because
 * drivers acquire targets at a glance, often with vibration.
 */
object FemtoDimens {
    /** Minimum tap target side length. M3 default is 48.dp. */
    val MinTouchTarget = 64.dp

    /** Minimum body text size for any driver-visible screen. */
    val MinBodyTextSize = 18.sp

    /** Outer padding for top-level screens. */
    val ScreenPadding = 24.dp

    /** Spacing between tiles in a launcher grid. */
    val GridGutter = 16.dp

    /** Default card elevation. Bold Minimal keeps surfaces flat. */
    val CardElevation = 0.dp

    /** Hero-block icon size for dashboard cards (e.g., the weather summary). */
    val HeroIconSize = 36.dp

    /** Inline icon size beside short labels (sunrise, wind, transport). */
    val InlineIconSize = 20.dp

    /** Album art thumbnail size in the music panel's playing state. */
    val AlbumArtSize = 72.dp

    /** Footer dock height (64.dp tap target + 16.dp top/bottom breathing room). */
    val FooterHeight = 80.dp

    /** Top row of the info pane that houses the calendar and weather cards. */
    val TopRowHeight = 224.dp

    /** Album art size inside the music card's vertical playing layout. */
    val MusicArtSize = 140.dp

    /** Skip-previous / skip-next transport button hit area. */
    val MusicTransportButton = 64.dp

    /** Centre play / pause button — slightly wider so it reads as primary. */
    val MusicPlayButton = 72.dp

    /** Corner radius for glass overlays on the map pane. */
    val OverlayCorner = 16.dp

    /** Weather glyph beside the city name in the weather card head row. */
    val WeatherGlyphLarge = 22.dp

    /** Weather glyph inside the 3-hour forecast chips. */
    val WeatherGlyphSmall = 18.dp

    /** Large numeric anchor (big-day, big-temp) display size. */
    val BigNumberFontSize = 56.sp
}
