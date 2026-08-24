package io.github.seijikohara.femto.data.display

/** Light / dark / follow-system theme choice. */
internal enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Speed/distance unit: follow the locale, or force metric / imperial. */
internal enum class SpeedUnitSetting { AUTO, KILOMETERS, MILES }

/** Temperature unit: follow the locale, or force Celsius / Fahrenheit. */
internal enum class TemperatureUnitSetting { AUTO, CELSIUS, FAHRENHEIT }

/** Clock: follow the system 12/24h setting, or force 12h / 24h. */
internal enum class ClockSetting { AUTO, TWELVE_HOUR, TWENTY_FOUR_HOUR }

/**
 * App accent: [DYNAMIC] keeps Material You wallpaper-derived color (the default);
 * every other entry forces a fixed preset seed from which the whole Material 3
 * scheme is generated. The seed color for each preset lives in the theme layer
 * (`accentSeedColor`), keeping this data enum free of any Compose dependency.
 *
 * Public (unlike the other display enums) because the public [FemtoTheme] takes
 * it as a parameter.
 */
enum class AccentColor { DYNAMIC, BLUE, TEAL, GREEN, AMBER, ORANGE, RED, VIOLET, PINK }

/**
 * Global UI scale, applied as a density multiplier over the whole UI (text, icons,
 * and layout). Five steps, symmetric around [MEDIUM] (factor 1.0): [COMPACT] and
 * [COMFORTABLE] are the midpoints between [MEDIUM] and each anchor. [MEDIUM] is the
 * safe default that honours the automotive floors (AGENTS.md#automotive-overrides);
 * [SMALL] and [LARGE] are explicit user opt-ins that may fall below / rise above them
 * — sanctioned because this ships as a general Play-Store app, mirroring the system
 * font-size / display-size controls. [COMPACT] and [COMFORTABLE] stay within
 * [SMALL]..[LARGE], so they never cross further below / above the floors than those
 * anchors already do. Public (like [AccentColor]) because the public [FemtoTheme]
 * takes it.
 */
enum class UiScale(
    val factor: Float,
) {
    SMALL(2f / 3f),
    COMPACT(5f / 6f),
    MEDIUM(1f),
    COMFORTABLE(7f / 6f),
    LARGE(4f / 3f),
}

/** Fullscreen: keep the system bars, or hide both status and navigation bars. */
internal enum class FullscreenSetting { OFF, ON }

/**
 * Assistant entry: [SYSTEM] hands the dock mic to the device's default
 * assistant, which draws its own overlay above the dashboard (the launcher
 * stays visible underneath); [IN_APP] opens the in-launcher voice sheet.
 * [SYSTEM] is the default — the host falls back to the sheet when no
 * assistant resolves (e.g. a head unit without one installed).
 */
internal enum class AssistantLaunchSetting { SYSTEM, IN_APP }

/**
 * Which screen edge hosts the dashboard dock. [BOTTOM] and [TOP] render the
 * horizontal bar; [LEFT] and [RIGHT] render it as a vertical rail.
 */
internal enum class DockPosition {
    BOTTOM,
    TOP,
    LEFT,
    RIGHT,
    ;

    /**
     * True on the two edges that draw the horizontal bar — the split
     * `DashboardDock` dispatches on. Lets a caller gate bar-only behaviour
     * (e.g. the Dock width setting, which a rail ignores) by asking the
     * question instead of re-listing the constants.
     */
    val hostsHorizontalBar: Boolean get() = this == BOTTOM || this == TOP
}

/**
 * How wide the horizontal dock draws itself. [COMPACT] is a centred pill that
 * wraps its buttons; [EXTENDED] is a full-width bar whose buttons share the
 * width. [COMPACT] is the default, so a fresh install keeps today's centred
 * pill — and it means "pill where it fits", not "pill always": the pill is a
 * fixed footprint, so a bar too narrow for it falls back to [EXTENDED] rather
 * than clipping its leading / trailing buttons below the automotive tap floor
 * (AGENTS.md#automotive-overrides). [EXTENDED] shrinks toward that floor instead
 * and never clips, so it is safe at any width. Only [DockPosition.BOTTOM] /
 * [DockPosition.TOP] render a horizontal bar; the rails ignore this.
 */
internal enum class DockWidth { COMPACT, EXTENDED }

/**
 * Which side the driver sits on. The dashboard anchors its info-dense
 * layout (floating cards, clock, speed reserve, map controls, self-marker) to
 * this side so it stays within the driver's reach/glance instead of behind the
 * wheel. [RIGHT] is the default — today's layout, unchanged for a fresh install.
 */
internal enum class DriverSide { RIGHT, LEFT }

/**
 * Motion intensity for panel transitions and card content cross-fades.
 * STANDARD = fade + subtle scale; REDUCED = short fade, no scale; OFF =
 * instant (for the weakest head-unit GPUs).
 */
internal enum class MotionTier { STANDARD, REDUCED, OFF }

/**
 * Screen orientation: follow the head unit ([AUTO], the default — portrait and
 * landscape units must both work, per AGENTS.md#launcher-behavior), or force
 * landscape / portrait for units that misreport their natural orientation.
 */
internal enum class OrientationSetting { AUTO, LANDSCAPE, PORTRAIT }

/** Map light/dark style: follow the system theme, or force light / dark. */
internal enum class MapStyleSetting { AUTO, LIGHT, DARK }

/**
 * A map colour scheme. [ACCENT] is the adaptive default: the base style for the
 * active light/dark context (bundled positron / dark-matter) recoloured with the
 * app's Material accent. The rest are fixed OpenFreeMap styles — [POSITRON],
 * [BRIGHT], [LIBERTY] read as light; [DARK_MATTER] (bundled), [DARK], [FIORD] read
 * as dark. The light and dark schemes are chosen independently (see
 * [DisplaySettings.mapSchemeLight] / [DisplaySettings.mapSchemeDark]).
 */
internal enum class MapColorScheme { ACCENT, POSITRON, BRIGHT, LIBERTY, DARK_MATTER, DARK, FIORD }

/** Default oblique-camera tilt (degrees) and zoom level for the map. */
internal const val DEFAULT_MAP_TILT_DEG = 55
internal const val DEFAULT_MAP_ZOOM = 16

/**
 * Zoom bounds shared by the settings slider and the on-map zoom buttons.
 * 12 keeps a usable overview; 19 is the densest the bundled styles render well.
 */
internal const val MIN_MAP_ZOOM = 12
internal const val MAX_MAP_ZOOM = 19

/**
 * Default location-marker vertical position (0..100): 0 centres the marker in the
 * map, 100 drops it just above the speed overlay. The camera is shifted so the
 * marker lands at the chosen height.
 */
internal const val DEFAULT_MAP_MARKER_POS = 70

/** Default glass-overlay blur radius (dp) and tint opacity (absolute percent: 0 = clear, 100 = opaque). */
internal const val DEFAULT_GLASS_BLUR_DP = 16
internal const val DEFAULT_GLASS_TINT_SCALE = 50
internal const val DEFAULT_GLASS_SHADOW_ENABLED = true
internal const val DEFAULT_GLASS_SHADOW_INTENSITY = 40
internal const val DEFAULT_GLASS_SHADOW_SIZE_DP = 8

/**
 * Default font-adjustment values, applied on top of the resolved font family by
 * `FemtoTheme`. [DEFAULT_FONT_BASE_SIZE_SP] (16 sp) matches the theme's rem-scale
 * root, so the default re-bases nothing; [DEFAULT_FONT_WEIGHT_STEP] and
 * [DEFAULT_FONT_LETTER_SPACING_CENTI_EM] (both 0) leave the Bold Minimal weight
 * tiers and tracking untouched. Held here in the data layer (which never imports
 * `ui/`) so the read fallback and [DisplaySettings.Default] share one source.
 */
internal const val DEFAULT_FONT_BASE_SIZE_SP = 16
internal const val DEFAULT_FONT_WEIGHT_STEP = 0
internal const val DEFAULT_FONT_LETTER_SPACING_CENTI_EM = 0

/**
 * User display settings that override the locale / system defaults. Every value
 * defaults to the auto / system choice so a fresh install behaves exactly as
 * before the settings screen existed.
 */
internal data class DisplaySettings(
    val themeMode: ThemeMode,
    val accentColor: AccentColor,
    // Global UI scale (density multiplier): MEDIUM is the safe default; SMALL may
    // fall below the automotive floors and LARGE rises above them (user opt-in).
    val uiScale: UiScale,
    val speedUnit: SpeedUnitSetting,
    val temperatureUnit: TemperatureUnitSetting,
    val clock: ClockSetting,
    // Whether the clock overlay shows seconds. Defaults to false (HH:mm, self-timed
    // per-minute); when true the overlay shows the full HH:mm:ss readout and ticks
    // per-second.
    val showClockSeconds: Boolean,
    val fullscreen: FullscreenSetting,
    // Which screen edge hosts the dashboard dock; BOTTOM is the classic dock.
    val dockPosition: DockPosition,
    // Whether the horizontal dock draws as a centred pill or a full-width bar.
    // COMPACT is the default; see DockWidth for why it is not a hard "pill always".
    val dockWidth: DockWidth,
    // Which side the driver sits on; the dashboard anchors to it. RIGHT is
    // today's layout, unchanged for a fresh install.
    val driverSide: DriverSide,
    // Motion intensity for panel transitions and card content cross-fades.
    val motionTier: MotionTier,
    // Screen orientation; AUTO follows the head unit's natural orientation.
    val orientation: OrientationSetting,
    // Whether to keep the screen awake while the launcher is foreground. Defaults
    // to true: the head unit runs on vehicle power, so the dashboard should stay lit.
    val keepScreenOn: Boolean,
    // Whether the dock mic launches the system assistant overlay or the
    // in-launcher voice sheet.
    val assistantLaunch: AssistantLaunchSetting,
    val mapStyle: MapStyleSetting,
    // Independent colour schemes for the light and dark map contexts (which one
    // applies follows [mapStyle] / the system theme). Both default to ACCENT.
    val mapSchemeLight: MapColorScheme,
    val mapSchemeDark: MapColorScheme,
    val mapTiltDeg: Int,
    val mapZoom: Int,
    // Live-map camera orientation: true pins the camera to north, false rotates
    // it with the travel heading (the driving default everywhere; the compass
    // overlay toggles this). Locale-neutral by design — no market prefers one.
    val mapNorthUp: Boolean,
    // Location-marker vertical position (0..100): 0 = map centre, 100 = just above
    // the speed overlay. Applied to both backends.
    val mapMarkerPos: Int,
    // OSM-map (WebGL) feature toggles. 3D buildings extrude the OpenMapTiles building
    // layer (default on); terrain adds raster-DEM relief and defaults OFF — it fetches
    // a separate elevation-tile source and is the heavier of the two on the weakest
    // head-unit GPUs, so a fresh install stays light and the user opts in. Ignored when
    // backend == MAPBOX (Mapbox GL JS manages its own layer stack).
    val map3dBuildings: Boolean,
    val mapTerrain: Boolean,
    // Map-overlay glass: the backdrop blur radius (dp) and the tint opacity as an
    // absolute percent (0 = clear glass, 100 = fully opaque surface).
    val glassBlurRadius: Int,
    val glassTintScale: Int,
    // Optional glass chrome: an outline border (off by default, keeping the
    // frameless frosted-glass look) and a drop shadow (on by default — a subtle
    // theme-aware lift off the map). glassShadowIntensity is a 0-100 percent;
    // glassShadowSizeDp is the shadow's blur/elevation in dp.
    val glassShowBorder: Boolean,
    val glassShadowEnabled: Boolean,
    val glassShadowIntensity: Int,
    val glassShadowSizeDp: Int,
    // User font adjustments applied on top of the resolved font family through
    // FemtoTheme: the rem-scale base size (sp), the weight-tier step (each step
    // shifts the three Bold Minimal weight tiers by 100), and letter spacing in
    // hundredths of an em. The defaults leave the Bold Minimal baseline unchanged.
    val fontBaseSizeSp: Int,
    val fontWeightStep: Int,
    val fontLetterSpacingCentiEm: Int,
    // Info-pane card visibility. Each card defaults to shown so a fresh install
    // renders the full dashboard; hiding one lets the remaining cards (or the map)
    // reflow into the freed space.
    val showCalendar: Boolean,
    val showWeather: Boolean,
    val showMusic: Boolean,
    // Whether the music card renders the audio-reactive spectrum behind its
    // transport controls. Defaults to false: the visualization is decorative
    // and its Visualizer capture sits behind the RECORD_AUDIO runtime grant,
    // so a fresh install must never prompt for it.
    val musicSpectrum: Boolean,
    // Whether the music card and the full-screen player show the album name.
    // Defaults true (the album line is shown); users who find it redundant with
    // the title can hide it. See musicShowArt for the cover-art counterpart.
    val musicShowAlbum: Boolean,
    // Whether the music card and the full-screen player show the album artwork.
    // Defaults true; hiding it yields a metadata-only, minimal player.
    val musicShowArt: Boolean,
    // Map backend: OSM (MapLibre + OpenFreeMap, free) or MAPBOX (requires a user-supplied token).
    val mapBackend: MapBackend = MapBackend.OSM,
    // Mapbox base style, only meaningful when mapBackend == MAPBOX.
    val mapboxStyle: MapboxStyle = MapboxStyle.STANDARD,
    // Whether to overlay live traffic on the Mapbox map.
    val mapboxTraffic: Boolean = false,
    /** User-supplied Mapbox public access token (pk.…); blank disables the Mapbox backend. */
    val mapboxAccessToken: String = "",
    /** User-supplied Google Maps API key; blank disables the Google Maps backend. */
    val googleMapsApiKey: String = "",
    /**
     * User-supplied Google Maps Map ID. Independent of the rendering mode below:
     * it carries cloud styling and advanced markers, and only under
     * [GoogleMapsRendering.AUTO] does its cloud configuration also decide raster
     * vs vector. Blank is supported in every mode.
     */
    val googleMapsMapId: String = "",
    // Raster / vector is independent of the Map ID (Maps JS 3.56.10+); AUTO leaves
    // the Map ID's Cloud configuration in charge. See GoogleMapsRendering.
    val googleMapsRendering: GoogleMapsRendering = GoogleMapsRendering.AUTO,
    // Google Maps map type, only meaningful when mapBackend == GOOGLEMAPS.
    val googleMapsMapType: GoogleMapType = GoogleMapType.ROADMAP,
    // Whether to overlay live traffic on the Google Maps map.
    val googleMapsTraffic: Boolean = false,
) {
    companion object {
        val Default =
            DisplaySettings(
                themeMode = ThemeMode.SYSTEM,
                accentColor = AccentColor.DYNAMIC,
                uiScale = UiScale.MEDIUM,
                speedUnit = SpeedUnitSetting.AUTO,
                temperatureUnit = TemperatureUnitSetting.AUTO,
                clock = ClockSetting.AUTO,
                showClockSeconds = false,
                fullscreen = FullscreenSetting.ON,
                dockPosition = DockPosition.BOTTOM,
                dockWidth = DockWidth.COMPACT,
                driverSide = DriverSide.RIGHT,
                motionTier = MotionTier.STANDARD,
                orientation = OrientationSetting.AUTO,
                keepScreenOn = true,
                assistantLaunch = AssistantLaunchSetting.SYSTEM,
                mapStyle = MapStyleSetting.AUTO,
                mapSchemeLight = MapColorScheme.ACCENT,
                mapSchemeDark = MapColorScheme.ACCENT,
                mapTiltDeg = DEFAULT_MAP_TILT_DEG,
                mapZoom = DEFAULT_MAP_ZOOM,
                mapNorthUp = false,
                mapMarkerPos = DEFAULT_MAP_MARKER_POS,
                map3dBuildings = true,
                mapTerrain = false,
                glassBlurRadius = DEFAULT_GLASS_BLUR_DP,
                glassTintScale = DEFAULT_GLASS_TINT_SCALE,
                glassShowBorder = false,
                glassShadowEnabled = DEFAULT_GLASS_SHADOW_ENABLED,
                glassShadowIntensity = DEFAULT_GLASS_SHADOW_INTENSITY,
                glassShadowSizeDp = DEFAULT_GLASS_SHADOW_SIZE_DP,
                fontBaseSizeSp = DEFAULT_FONT_BASE_SIZE_SP,
                fontWeightStep = DEFAULT_FONT_WEIGHT_STEP,
                fontLetterSpacingCentiEm = DEFAULT_FONT_LETTER_SPACING_CENTI_EM,
                showCalendar = true,
                showWeather = true,
                showMusic = true,
                musicSpectrum = false,
                musicShowAlbum = true,
                musicShowArt = true,
                mapBackend = MapBackend.OSM,
                mapboxStyle = MapboxStyle.STANDARD,
                mapboxTraffic = false,
                mapboxAccessToken = "",
                googleMapsApiKey = "",
                googleMapsMapId = "",
                googleMapsRendering = GoogleMapsRendering.AUTO,
                googleMapsMapType = GoogleMapType.ROADMAP,
                googleMapsTraffic = false,
            )
    }
}
