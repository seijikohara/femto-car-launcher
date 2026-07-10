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

    /**
     * Diameter of the app drawer's floating letter-indicator bubble, shown
     * over the app list while the A-Z rail is being dragged. Decorative only
     * (not itself a tap target), so it is sized independently of
     * [MinTouchTarget].
     */
    val IndexBubbleSize = 72.dp

    /** Hero-block icon size for dashboard cards (e.g., the weather summary). */
    val HeroIconSize = 36.dp

    /** Inline icon size beside short labels (sunrise, wind, transport). */
    val InlineIconSize = 20.dp

    /**
     * Dock thickness: its height as a horizontal bar, its width as a vertical
     * rail. Set to exactly [MinTouchTarget] (64.dp): the dock holds full-size
     * nav buttons, so it cannot go lower without breaching the tap-target floor
     * (CLAUDE.md#automotive-overrides).
     */
    val DockThickness = 64.dp

    /** Album art size inside the music card's vertical playing layout. */
    val MusicArtSize = 140.dp

    /**
     * Minimum width guaranteed to the music card's title / artist / album
     * column before the album art is allowed to claim its full [MusicArtSize].
     * The art's width is height-driven (a square that grows with the row's
     * available height via `aspectRatio`), so on a tall card it can otherwise
     * grow wide enough to squeeze the text column below what a track's title
     * or album name needs, truncating it despite the card having plenty of
     * width overall — the art shrinks first instead.
     */
    val MusicMetaMinWidth = 190.dp

    /** Skip-previous / skip-next transport button hit area. */
    val MusicTransportButton = 64.dp

    /** Centre play / pause button — slightly wider so it reads as primary. */
    val MusicPlayButton = 72.dp

    /**
     * Gap between the transport buttons (skip-previous / play-pause /
     * skip-next), and independently between the shuffle / repeat toggles.
     * Shared by the music card and the Now Playing panel's landscape
     * (inline-toggle) row — both of which size the row to wrap its content,
     * so the buttons sit as a tight cluster regardless of gap.
     */
    val MusicTransportGap = 24.dp

    /**
     * Tighter transport / toggle button gap for the Now Playing panel's
     * PORTRAIT layout only. The panel's oversized prev/play/next buttons read
     * as too far apart at the shared [MusicTransportGap]; a smaller gap draws
     * them into one compact cluster. Applied panel-only via `TransportRow`'s
     * and `TransportToggles`' own `gap` parameter, leaving the card (which
     * wraps its content) on the shared value.
     */
    val NowPlayingPanelTransportGap = 12.dp

    /** Cap on the Now Playing panel's album art so it never dominates a tall panel. */
    val NowPlayingArtMax = 320.dp

    /**
     * Horizontal inset for the Now Playing panel's spectrum background, so the
     * bars stop where the seek bar's track and the metadata text start rather
     * than spanning the full content column — the seek bar and metadata rows
     * are themselves inset by a leading icon or a time label, so an edge-to-edge
     * spectrum reads as wider than everything else it sits behind.
     */
    val SpectrumHorizontalInset = 40.dp

    /** Inner padding for the small glass map overlays (clock / speed pill). */
    val OverlayPaddingHorizontal = 16.dp
    val OverlayPaddingVertical = 6.dp

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

    /**
     * Upper bound on the dashboard dock's nav-button cluster (the reference-binding
     * rationale mirrors [SpeedOverlayMaxWidth]). The seven buttons share their
     * slot's width equally via `Modifier.weight`, so on an ultrawide / premium
     * head unit that slot grows far past the buttons' comfortable size and each
     * one ends up centred inside a wide, sparse-looking gap. Capping the cluster
     * (not the dock bar itself, which stays full-width) keeps the buttons a
     * tight, tappable group and centres the leftover space around them, while
     * the reference 853 dp-wide 5:3 head unit's narrower cluster stays under
     * the cap and is unaffected.
     */
    val DockNavClusterMaxWidth = 760.dp

    /** Weather glyph beside the city name in the weather card head row. */
    val WeatherGlyphLarge = 20.dp

    /** Hero weather glyph in the head row, sized to balance the big temperature. */
    val WeatherGlyphHero = 44.dp

    /** Weather glyph inside the forecast chips. */
    val WeatherGlyphSmall = 18.dp

    /** Gap between the weather card's forecast-grid chips, on both axes. */
    val ForecastChipGap = 4.dp

    /** Large numeric anchor (big-day, big-temp) display size. */
    val BigNumberFontSize = 56.sp

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

    /**
     * Shared opacity for the dashboard's content dividers — the dock's nav /
     * status separator, the speed overlay's metric separators and metric /
     * address rule, and the map-control pill's segment dividers — so every
     * hairline reads at the same weight over the glass. The dock consumes this
     * through the shared `FemtoVerticalDivider` / `FemtoHorizontalDivider`
     * (`ui/home/components/Dividers.kt`); the speed overlay and the map
     * control pill still build their own divider primitive but reference the
     * same alpha. A plain `val`, not `const val`: ktlint reserves
     * SCREAMING_SNAKE_CASE for compile-time constants.
     */
    val DividerAlpha = 0.5f

    /**
     * App-drawer bottom-sheet height as a fraction of the viewport, so the
     * dashboard stays visible behind the scrim. Keyed off screen height, never a
     * specific device.
     */
    val DrawerSheetHeightFraction = 0.72f

    /**
     * Font-picker bottom-sheet height as a fraction of the viewport. Taller than
     * the drawer sheet because the picker is a long, scrollable list of every
     * Google Fonts family and benefits from the extra rows.
     */
    val FontPickerSheetHeightFraction = 0.92f

    /**
     * Settings bottom-sheet height as a fraction of the viewport. Taller than
     * the drawer sheet (and equal to the font picker's) because the
     * master-detail layout needs the extra room on both sides: the rail /
     * list wants to show as many of the 7 categories as possible without
     * scrolling, and the detail pane wants to show more than a couple of rows
     * before its own scroll kicks in.
     */
    val SettingsSheetHeightFraction = 0.92f

    /**
     * Fraction of the Now-Playing panel's height reserved for the upcoming-queue
     * list, so it scales with the panel geometry instead of eating whatever
     * leftover space the enclosing scroll column has (which could run to the
     * panel's very bottom edge). See `PlayingNextList` / `FitWholeRows`.
     */
    val NowPlayingQueueHeightFraction = 0.3f
}
