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

    /**
     * Glance-metadata text size for the sanctioned card relaxations of
     * [MinBodyTextSize] — secondary captions, metrics, and progress labels
     * inside dashboard cards (CLAUDE.md#automotive-overrides). One token so
     * every card relaxes to the same size.
     */
    val GlanceTextSize = 13.sp

    /** Outer padding for top-level screens. */
    val ScreenPadding = 24.dp

    /** Spacing between tiles in a launcher grid. */
    val GridGutter = 16.dp

    /** Hero-block icon size for dashboard cards (e.g., the weather summary). */
    val HeroIconSize = 36.dp

    /** Inline icon size beside short labels (sunrise, wind, transport). */
    val InlineIconSize = 20.dp

    /** Album art thumbnail size in the music panel's playing state. */
    val AlbumArtSize = 72.dp

    /**
     * Dock thickness: its height as a horizontal bar, its width as a vertical
     * rail. Set to exactly [MinTouchTarget] (64.dp): the dock holds full-size
     * nav buttons, so it cannot go lower without breaching the tap-target floor
     * (CLAUDE.md#automotive-overrides).
     */
    val DockThickness = 64.dp

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
     * larger 20 dp corner — a retired-dashboard-v2-mockup decision kept on
     * device — so the two tokens stay separate rather than reusing
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

    /**
     * Upper bound on the speed overlay's width so it reads as a centred glass
     * card on the map pane rather than stretching toward a full-width bar on a
     * wide head unit (e.g. an 853 dp-wide 5:3 projection). The overlay still
     * sizes to its content via `IntrinsicSize.Max`; this only caps the maximum,
     * and the address row ellipsizes within it instead of expanding the card.
     */
    val SpeedOverlayMaxWidth = 440.dp

    /** Weather glyph beside the city name in the weather card head row. */
    val WeatherGlyphLarge = 20.dp

    /** Hero weather glyph in the head row, sized to balance the big temperature. */
    val WeatherGlyphHero = 56.dp

    /** Weather glyph inside the 3-hour forecast chips. */
    val WeatherGlyphSmall = 18.dp

    /**
     * Minimum width per hourly-forecast chip. The chip count derives from the
     * card width at this floor (never fewer than three chips), so wider
     * panels gain forecast hours instead of stretching three chips.
     */
    val ForecastChipMinWidth = 52.dp

    /** Large numeric anchor (big-day, big-temp) display size. */
    val BigNumberFontSize = 56.sp

    /** Gap between the two top-level panes. Mockup legend: pane gap 16 dp. */
    val PaneGap = 16.dp

    /** Uniform inner padding for dashboard cards. Tightened from the mockup's 16 dp. */
    val CardPadding = 14.dp

    /** Vertical rhythm between the sections stacked inside a card. */
    val CardSectionGap = 10.dp

    /**
     * Tighter padding / section rhythm for the short top-row info cards
     * (calendar, weather) on the head-unit info pane, so the head + strip +
     * events (or head + metrics + forecast) pack in without clipping.
     */
    val CardPaddingCompact = 10.dp
    val CardSectionGapCompact = 6.dp

    /** Corner radius for the calendar day-strip cells. Mockup `.day-cell`: 10 px. */
    val DayCellCorner = 10.dp

    /** Corner radius for the music album-art block. Mockup `.music-card .art`: 14 px. */
    val ArtCorner = 14.dp

    /**
     * Glass overlay tint opacity in light theme: the surface tint laid over the
     * blurred map backdrop (the clock / speed panels use Haze). Kept below a fully
     * opaque scrim so the backdrop blur stays visible while text contrast holds; on
     * the Live map backend (no captured backdrop) the panel falls back to an opaque
     * surface base under this tint, so it stays legible there too.
     *
     * The glass alpha tokens keep the PascalCase token vocabulary of this object
     * rather than ktlint's SCREAMING_SNAKE_CASE for `const val`, so they read as
     * siblings of the dp tokens above.
     */
    @Suppress("ktlint:standard:property-naming")
    const val GlassBgAlphaLight = 0.6f

    /** Glass overlay tint opacity in dark theme — more translucent so the darker blurred map reads through. */
    @Suppress("ktlint:standard:property-naming")
    const val GlassBgAlphaDark = 0.42f

    /** Glass overlay hairline border opacity, shared across light and dark themes. */
    @Suppress("ktlint:standard:property-naming")
    const val GlassBorderAlpha = 0.6f

    /** Backdrop blur radius for the glass overlays (clock / speed) over the map. */
    val GlassBlurRadius = 24.dp

    /**
     * App-drawer bottom-sheet height as a fraction of the viewport, so the
     * dashboard stays visible behind the scrim. Keyed off screen height, never a
     * specific device.
     */
    @Suppress("ktlint:standard:property-naming")
    const val DrawerSheetHeightFraction = 0.72f

    /**
     * Font-picker bottom-sheet height as a fraction of the viewport. Taller than
     * the drawer / settings sheets because the picker is a long, scrollable list
     * of every Google Fonts family and benefits from the extra rows.
     */
    @Suppress("ktlint:standard:property-naming")
    const val FontPickerSheetHeightFraction = 0.92f
}
