package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapRenderMode
import io.github.seijikohara.femto.data.display.MapStyleSetting

// User-tunable map rendering config (derived from DisplaySettings): light/dark
// style, oblique tilt, zoom, the render resolution percent (lower renders a
// smaller bitmap, faster, upscaled to fill), the user-picked render backend, and
// the LIVE-only feature toggles (3D buildings / terrain relief).
internal data class MapConfig(
    val style: MapStyleSetting = MapStyleSetting.AUTO,
    val schemeLight: MapColorScheme = MapColorScheme.ACCENT,
    val schemeDark: MapColorScheme = MapColorScheme.ACCENT,
    val tiltDeg: Int = 55,
    val zoom: Int = 16,
    // North-up pins the LIVE camera to north (the chevron rotates to the heading
    // instead); false is heading-up, the driving default.
    val northUp: Boolean = false,
    val renderPercent: Int = 100,
    val renderMode: MapRenderMode = MapRenderMode.SNAPSHOT,
    val markerPos: Int = 70,
    // Fraction (0..0.5) of the map height the bottom speed overlay occupies,
    // measured at layout time (not a persisted setting). The marker drop is
    // clamped against it so the chevron always clears the overlay regardless of
    // markerPos or screen aspect; 0 means "unmeasured", leaving the MAX_MARKER_DROP
    // cap as the only bound.
    val bottomSafeFraction: Float = 0f,
    val buildings3d: Boolean = false,
    val terrain: Boolean = false,
)

// internal so MapSnapshotRenderTest renders the SAME zoom the panel uses, keeping
// it a single source of truth (POSITRON_STYLE_URL lives in MapScheme.kt).
internal const val MAP_ZOOM = 16.5
