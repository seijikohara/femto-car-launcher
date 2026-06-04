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

    /** Album art size inside the music card's vertical playing layout. */
    val MusicArtSize = 140.dp

    /** Skip-previous / skip-next transport button hit area. */
    val MusicTransportButton = 64.dp

    /** Centre play / pause button — slightly wider so it reads as primary. */
    val MusicPlayButton = 72.dp

    /** Corner radius for glass overlays on the map pane. */
    val OverlayCorner = 16.dp

    /**
     * Corner radius for the speed overlay on the map pane. Deliberately
     * distinct from [OverlayCorner] (16 dp): the speed overlay carries a
     * larger 20 dp corner per `docs/design/dashboard-v2-mockup.html`
     * `.speed-overlay`, so the two tokens stay separate rather than reusing
     * [OverlayCorner].
     */
    val SpeedOverlayCorner = 20.dp

    /**
     * Minimum width reserved for the speed overlay's hero numeral so the card
     * does not reflow as the speed's digit count changes (e.g. 9 -> 120 km/h).
     * Sized for the 3-digit range left after [TripRepository]'s plausibility
     * clamp; the value is right-aligned within it.
     */
    val SpeedHeroValueMinWidth = 72.dp

    /**
     * Minimum width reserved for each secondary speed-metric cell (distance,
     * average) so digit-count changes do not reflow the overlay across the
     * common driving range.
     */
    val SpeedMetricMinWidth = 76.dp

    /** Weather glyph beside the city name in the weather card head row. */
    val WeatherGlyphLarge = 20.dp

    /** Weather glyph inside the 3-hour forecast chips. */
    val WeatherGlyphSmall = 18.dp

    /** Large numeric anchor (big-day, big-temp) display size. */
    val BigNumberFontSize = 56.sp

    /** Gap between the two top-level panes. Mockup legend: pane gap 16 dp. */
    val PaneGap = 16.dp

    /** Uniform inner padding for dashboard cards. Tightened from the mockup's 16 dp. */
    val CardPadding = 14.dp

    /** Vertical rhythm between the sections stacked inside a card. */
    val CardSectionGap = 10.dp

    /** Corner radius for dashboard cards. Mockup legend: card radius 16 dp (Shapes.large). */
    val CardCorner = 16.dp

    /** Corner radius for the calendar day-strip cells. Mockup `.day-cell`: 10 px. */
    val DayCellCorner = 10.dp

    /** Corner radius for the weather forecast chips. Mockup `.chip`: 8 px. */
    val ChipCorner = 8.dp

    /** Corner radius for the music album-art block. Mockup `.music-card .art`: 14 px. */
    val ArtCorner = 14.dp

    /**
     * Glass overlay background opacity in light theme.
     *
     * The glass alpha tokens keep the PascalCase token vocabulary of this object
     * rather than ktlint's SCREAMING_SNAKE_CASE for `const val`, so they read as
     * siblings of the dp tokens above.
     */
    @Suppress("ktlint:standard:property-naming")
    const val GlassBgAlphaLight = 0.78f

    /** Glass overlay background opacity in dark theme — more translucent so the darker map reads through. */
    @Suppress("ktlint:standard:property-naming")
    const val GlassBgAlphaDark = 0.55f

    /** Glass overlay hairline border opacity, shared across light and dark themes. */
    @Suppress("ktlint:standard:property-naming")
    const val GlassBorderAlpha = 0.6f
}
