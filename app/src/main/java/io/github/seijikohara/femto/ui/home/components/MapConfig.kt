package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.display.MapboxStyle

// User-tunable map rendering config (derived from DisplaySettings): light/dark
// style, oblique tilt, zoom, the user-picked backend, and the OSM-only feature
// toggles (3D buildings / terrain relief).
internal data class MapConfig(
    val style: MapStyleSetting = MapStyleSetting.AUTO,
    val schemeLight: MapColorScheme = MapColorScheme.ACCENT,
    val schemeDark: MapColorScheme = MapColorScheme.ACCENT,
    val tiltDeg: Int = 55,
    val zoom: Int = 16,
    // North-up pins the camera to north (the chevron rotates to the heading
    // instead); false is heading-up, the driving default.
    val northUp: Boolean = false,
    // Which map page to load: OSM (MapLibre + OpenFreeMap, free) or MAPBOX
    // (Mapbox GL JS, paid). Kept as a MapConfig field so the WebView host can
    // branch page URL and bridge calls without reaching into DisplaySettings.
    val backend: MapBackend = MapBackend.OSM,
    // Mapbox-specific fields; ignored when backend == OSM.
    val mapboxStyle: MapboxStyle = MapboxStyle.STANDARD,
    val mapboxTraffic: Boolean = false,
    val markerPos: Int = 70,
    // Fraction (0..0.5) of the map height the bottom speed overlay occupies,
    // measured at layout time (not a persisted setting). The marker drop is
    // clamped against it so the chevron always clears the overlay regardless of
    // markerPos or screen aspect; 0 means "unmeasured", leaving the MAX_MARKER_DROP
    // cap as the only bound.
    val bottomSafeFraction: Float = 0f,
    // Fraction (0..0.45) of the map width the right-hand floating cards occupy,
    // measured at layout time. The marker is shifted left of centre so it stays
    // clear of those cards — the horizontal analogue of [bottomSafeFraction]. 0
    // keeps the marker centred (portrait, or no cards).
    val rightSafeFraction: Float = 0f,
    val buildings3d: Boolean = false,
    val terrain: Boolean = false,
)
