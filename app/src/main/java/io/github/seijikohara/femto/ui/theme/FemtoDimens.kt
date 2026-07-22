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

    /**
     * Type-scale root — the rem-like base every text size derives from. Change
     * this single value and the whole modular scale below (and every role and
     * named style in `Type.kt` wired to it) moves together. 16sp is the M3 body
     * baseline and the automotive body floor ([MinBodyTextSize]).
     */
    val BaseTextSize = 16.sp

    // Modular type scale off [BaseTextSize] in 0.25x steps, widening to larger
    // multipliers toward the display/hero end. Roles and named styles in
    // `Type.kt` reference these tokens, never a raw sp literal, so the base stays
    // the single source of truth for the whole scale.
    val TextXs = BaseTextSize * 0.5f // 8
    val TextSm = BaseTextSize * 0.75f // 12
    val TextMd = BaseTextSize // 16 (alias of the base for readability)
    val TextLg = BaseTextSize * 1.25f // 20
    val TextXl = BaseTextSize * 1.5f // 24
    val Text2Xl = BaseTextSize * 1.75f // 28
    val Text3Xl = BaseTextSize * 2.0f // 32
    val Text4Xl = BaseTextSize * 2.5f // 40
    val Text5Xl = BaseTextSize * 3.0f // 48
    val Text6Xl = BaseTextSize * 3.5f // 56
    val Text7Xl = BaseTextSize * 4.5f // 72
    val Text8Xl = BaseTextSize * 6.0f // 96

    /**
     * Minimum body text size for any driver-visible screen — the automotive body
     * floor (AGENTS.md#automotive-overrides). The [TextMd] scale step (16sp),
     * lowered from 18sp by explicit design decision so the floor sits on the
     * rem-scale base.
     */
    val MinBodyTextSize = TextMd

    /**
     * Glance-metadata text size for the sanctioned card relaxations of
     * [MinBodyTextSize] — secondary captions, metrics, and progress labels
     * inside dashboard cards (AGENTS.md#automotive-overrides). The [TextSm] scale
     * step (12sp); one token so every card relaxes to the same size.
     */
    val GlanceTextSize = TextSm

    /**
     * Glance metric-value size — one notch above [GlanceTextSize] for the numeric
     * anchors inside glance surfaces (trip metrics, the calendar day-gutter
     * numeral) that read heavier than their captions. The [TextMd] scale step
     * (16sp), sitting on the [MinBodyTextSize] body floor. A named token so the
     * size lives here rather than as a literal inside the `glanceMetric` extension.
     */
    val GlanceMetricSize = TextMd

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
     * Width of the calendar event color bar — the thin rounded vertical line
     * that marks which calendar an event belongs to on the calendar card and
     * panel, shown only when the visible events span more than one calendar.
     * A decorative indicator, not a tap target. Width only: the bar stretches
     * to the event title's rendered lines at its call sites (see
     * `CalendarColorBar`).
     */
    val CalendarBarWidth = 3.dp

    /** Gap between the calendar event color bar and the event's time / title text. */
    val CalendarBarGap = 4.dp

    /**
     * Start indent that clears the leading color-bar gutter: the bar's width
     * plus its trailing gap. The bar leads the title row, spanning its rendered
     * lines, so an event's other lines (time, location) pad their start by this
     * to share the title's left edge.
     */
    val CalendarBarGutter = CalendarBarWidth + CalendarBarGap

    /**
     * Dock thickness: its height as a horizontal bar, its width as a vertical
     * rail. Set to exactly [MinTouchTarget] (64.dp): the dock holds full-size
     * nav buttons, so it cannot go lower without breaching the tap-target floor
     * (AGENTS.md#automotive-overrides).
     */
    val DockThickness = 64.dp

    /** Album art size inside the music card's vertical playing layout. */
    val MusicArtSize = 140.dp

    /**
     * Minimum width guaranteed to the music card's title / artist / album
     * column before the album art is allowed to claim its full [MusicArtSize].
     * The art's width is height-driven (a square that grows with the meta
     * column's height via `aspectRatio`), so on a tall meta block it could
     * otherwise grow wide enough to squeeze the text column below what a
     * track's title or album name needs, truncating it despite the card having
     * plenty of width overall — the art shrinks first instead.
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
     * Upper bound on the speed overlay's width so it reads as a centred glass
     * card on the map pane rather than stretching toward a full-width bar on a
     * wide head unit (e.g. an 853 dp-wide 5:3 projection). The overlay still
     * sizes to its content via `IntrinsicSize.Max`; this only caps the maximum,
     * and the address row ellipsizes within it instead of expanding the card.
     */
    val SpeedOverlayMaxWidth = 440.dp

    /**
     * Upper bound on the dashboard dock's weight-shared nav-button cluster (the
     * reference-binding rationale mirrors [SpeedOverlayMaxWidth]). Two dock layouts
     * share the buttons across an axis via `Modifier.weight` and cap the shared run
     * here so the buttons stay a tight, tappable group instead of spreading into a
     * wide, sparse gap on an ultrawide / premium panel: the vertical rail's nav
     * column (a height cap via `heightIn`) and the horizontal bar's weight-shared
     * fallback — the layout the bar drops to when the fixed-margin pill would
     * overflow the width (a width cap via `widthIn`). The fixed pill itself needs no
     * cap because it wraps its content; either way the leftover space centres around
     * the capped cluster.
     */
    val DockNavClusterMaxWidth = 760.dp

    /**
     * Fixed horizontal margin each horizontal-dock nav button reserves on both
     * sides. Adjacent buttons' margins add up (gap = 2x this) and the first / last
     * button keeps a single margin against the bar edge (edge = 1x), so the bar —
     * which wraps its content and centres — reads with uniform, screen-width-
     * independent spacing rather than dynamically distributed gaps.
     */
    val DockButtonMargin = 16.dp

    /** Weather glyph beside the city name in the weather card head row. */
    val WeatherGlyphLarge = 20.dp

    /** Hero weather glyph in the head row, sized to balance the big temperature. */
    val WeatherGlyphHero = 44.dp

    /** Weather glyph inside the forecast chips. */
    val WeatherGlyphSmall = 18.dp

    /**
     * Canvas height of the weather panel's 24 h temperature curve (curve area +
     * precipitation band + hour-label strip; the glyph row sits below it).
     */
    val WeatherCurveHeight = 140.dp

    /** Gap between the weather card's forecast-grid chips, on both axes. */
    val ForecastChipGap = 4.dp

    /** Large numeric anchor (big-day, big-temp) display size — the [Text6Xl] scale step (56sp). */
    val BigNumberFontSize = Text6Xl

    /** Uniform inner padding for dashboard cards. Tightened from the mockup's 16 dp. */
    val CardPadding = 14.dp

    /** Vertical rhythm between the sections stacked inside a card. */
    val CardSectionGap = 10.dp

    /**
     * Padding / section rhythm shared by the dashboard info cards (calendar,
     * weather, music) and the clock overlay's vertical inset. Kept a touch tighter
     * than the full [CardPadding] for the head-unit info pane, but roomy enough
     * that the content breathes from the card edge rather than crowding it — one
     * value so every card reads as a consistent set.
     */
    val CardPaddingCompact = 14.dp
    val CardSectionGapCompact = 8.dp

    /**
     * Reserved height for the music card's idle states (nothing playing / connect),
     * so the card keeps a playing-card-sized presence instead of shrinking to its
     * sparse content and letting the calendar / weather row jump taller. Sized to
     * the tallest playing layout — album art at its [MusicArtSize] cap over the
     * transport row ([MusicPlayButton]), with the section gap and both paddings.
     * The live playing card can run a little shorter: its art is a square tracking
     * the meta column's natural height, which typically sits under the cap.
     */
    val MusicCardMinHeight = CardPaddingCompact * 2 + MusicArtSize + CardSectionGapCompact + MusicPlayButton

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
