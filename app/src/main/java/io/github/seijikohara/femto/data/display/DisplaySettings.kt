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
internal enum class DockPosition { BOTTOM, TOP, LEFT, RIGHT }

/**
 * Screen orientation: follow the head unit ([AUTO], the default — portrait and
 * landscape units must both work, per CLAUDE.md#launcher-behavior), or force
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

/**
 * Map render backend, picked explicitly by the user (no auto-fallback).
 *
 * - [LIVE]: MapLibre GL JS (WebGL) in a hardware-accelerated WebView — smooth,
 *   animated, renders on both the emulator and the head unit.
 * - [SNAPSHOT]: off-screen `MapSnapshotter` bitmaps; presents reliably on any
 *   display (the robust floor for hardware that cannot keep a WebGL context).
 *   The default.
 *
 * A software-WebGL (SwiftShader) backend was removed: there is no per-app API to
 * route WebView WebGL through SwiftShader (`setLayerType(LAYER_TYPE_SOFTWARE)`
 * yields a blank map, not software WebGL), and hardware WebGL works on the target
 * device, so SNAPSHOT already covers the no-WebGL case.
 */
internal enum class MapRenderMode { LIVE, SNAPSHOT }

/** Default oblique-camera tilt (degrees) and zoom level for the map. */
internal const val DEFAULT_MAP_TILT_DEG = 55
internal const val DEFAULT_MAP_ZOOM = 16

/**
 * Default snapshot render resolution, as a percentage of the panel's pixel size.
 * 100 renders at full resolution; lower values render a smaller bitmap (upscaled
 * to fill), trading sharpness for a faster render and a smoother frame rate.
 */
internal const val DEFAULT_MAP_RENDER_PERCENT = 100

/**
 * Default location-marker vertical position (0..100): 0 centres the marker in the
 * map, 100 drops it just above the speed overlay. The camera is shifted so the
 * marker lands at the chosen height.
 */
internal const val DEFAULT_MAP_MARKER_POS = 70

/** Default glass-overlay blur radius (dp) and tint scale (percent of the per-theme base alpha). */
internal const val DEFAULT_GLASS_BLUR_DP = 24
internal const val DEFAULT_GLASS_TINT_SCALE = 100

/**
 * User display settings that override the locale / system defaults. Every value
 * defaults to the auto / system choice so a fresh install behaves exactly as
 * before the settings screen existed.
 */
internal data class DisplaySettings(
    val themeMode: ThemeMode,
    val accentColor: AccentColor,
    val speedUnit: SpeedUnitSetting,
    val temperatureUnit: TemperatureUnitSetting,
    val clock: ClockSetting,
    // Whether the clock overlay shows seconds. Defaults to true (the original
    // HH:mm:ss readout); when false the overlay drops to HH:mm and self-times
    // per-minute instead of per-second.
    val showClockSeconds: Boolean,
    val fullscreen: FullscreenSetting,
    // Which screen edge hosts the dashboard dock; BOTTOM is the classic dock.
    val dockPosition: DockPosition,
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
    // Snapshot render resolution as a percent of the panel pixel size; lower is
    // blurrier but renders faster (a smaller bitmap to upscale).
    val mapRenderPercent: Int,
    val mapRenderMode: MapRenderMode,
    // Location-marker vertical position (0..100): 0 = map centre, 100 = just above
    // the speed overlay. Applied to both backends.
    val mapMarkerPos: Int,
    // Live-map (WebGL) feature toggles. Both default off; they apply to the LIVE
    // backends only (SNAPSHOT's native GL cannot extrude/relief safely). 3D buildings
    // extrude the OpenMapTiles building layer; terrain adds raster-DEM relief.
    val map3dBuildings: Boolean,
    val mapTerrain: Boolean,
    // Map-overlay glass blur strength: the backdrop blur radius (dp) and the tint
    // opacity as a percent of the per-theme base alpha (100 = the current look).
    val glassBlurRadius: Int,
    val glassTintScale: Int,
    // Info-pane card visibility. Each card defaults to shown so a fresh install
    // renders the full dashboard; hiding one lets the remaining cards (or the map)
    // reflow into the freed space.
    val showCalendar: Boolean,
    val showWeather: Boolean,
    val showMusic: Boolean,
) {
    companion object {
        val Default =
            DisplaySettings(
                themeMode = ThemeMode.SYSTEM,
                accentColor = AccentColor.DYNAMIC,
                speedUnit = SpeedUnitSetting.AUTO,
                temperatureUnit = TemperatureUnitSetting.AUTO,
                clock = ClockSetting.AUTO,
                showClockSeconds = false,
                fullscreen = FullscreenSetting.ON,
                dockPosition = DockPosition.BOTTOM,
                orientation = OrientationSetting.AUTO,
                keepScreenOn = true,
                assistantLaunch = AssistantLaunchSetting.SYSTEM,
                mapStyle = MapStyleSetting.AUTO,
                mapSchemeLight = MapColorScheme.ACCENT,
                mapSchemeDark = MapColorScheme.ACCENT,
                mapTiltDeg = DEFAULT_MAP_TILT_DEG,
                mapZoom = DEFAULT_MAP_ZOOM,
                mapRenderPercent = DEFAULT_MAP_RENDER_PERCENT,
                mapRenderMode = MapRenderMode.LIVE,
                mapMarkerPos = DEFAULT_MAP_MARKER_POS,
                map3dBuildings = true,
                mapTerrain = true,
                glassBlurRadius = DEFAULT_GLASS_BLUR_DP,
                glassTintScale = DEFAULT_GLASS_TINT_SCALE,
                showCalendar = true,
                showWeather = true,
                showMusic = true,
            )
    }
}
