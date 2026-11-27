package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.GoogleMapType
import io.github.seijikohara.femto.data.display.GoogleMapsRendering
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapStyleSetting

// User-tunable map rendering config (derived from DisplaySettings): light/dark
// style, oblique tilt, zoom, the user-picked backend, and the OSM-only feature
// toggles (3D buildings / terrain relief).
internal data class MapConfig(
    val style: MapStyleSetting = MapStyleSetting.AUTO,
    val schemeLight: MapColorScheme = MapColorScheme.STANDARD,
    val schemeDark: MapColorScheme = MapColorScheme.STANDARD,
    val tiltDeg: Int = 55,
    val zoom: Int = 16,
    // North-up pins the camera to north (the chevron rotates to the heading
    // instead); false is heading-up, the driving default.
    val northUp: Boolean = false,
    // Which map page to load — see MapBackend for what each value means. Kept as
    // a MapConfig field so the WebView host can branch page URL and bridge calls
    // without reaching into DisplaySettings.
    val backend: MapBackend = MapBackend.OSM,
    // Google Maps-specific fields; ignored when backend != GOOGLEMAPS.
    val googleMapsApiKey: String = "",
    val googleMapsMapId: String = "",
    // Construction-time for the Maps JS API, so a change rebuilds the WebView
    // (see WebMapView's effectiveGoogleRendering).
    val googleMapsRendering: GoogleMapsRendering = GoogleMapsRendering.AUTO,
    val googleMapsMapType: GoogleMapType = GoogleMapType.ROADMAP,
    val googleMapsTraffic: Boolean = false,
    // OSM-only: the user's tile-host override (blank = the build default). The
    // host resolves it into the ordered host list the page rotates through on
    // retries — see WebMapView.mapTileHosts.
    val tileHostOverride: String = "",
    // OSM-only: the hosted style the CUSTOM scheme loads (blank = CUSTOM renders
    // as STANDARD). May carry a provider key — see MapScheme.mapStyleRefFor.
    val customStyleUrl: String = "",
    val markerPos: Int = 70,
    // Fraction (0..0.5) of the map height the bottom speed overlay occupies,
    // measured at layout time (not a persisted setting). The marker drop is
    // clamped against it so the chevron always clears the overlay regardless of
    // markerPos or screen aspect; 0 means "unmeasured", leaving the MAX_MARKER_DROP
    // cap as the only bound.
    val bottomSafeFraction: Float = 0f,
    // Fraction (0..1) of the map width covered from the right edge — the floating
    // cards of a RIGHT driver side, a dock on the right edge, or both — measured at
    // layout time; the horizontal analogue of [bottomSafeFraction]. The page puts
    // the marker at the middle of what is left between this and [leftSafeFraction]
    // (webmap/src/style.ts: markerSpot, on both map backends). 0 on both sides
    // keeps the marker centred.
    val rightSafeFraction: Float = 0f,
    // Fraction (0..1) of the map width covered from the left edge — the cards of
    // a LEFT driver side, a dock on the left edge, or both; the mirror of
    // [rightSafeFraction]. Both can be set at once (cards on one side, the dock on
    // the other).
    val leftSafeFraction: Float = 0f,
    val buildings3d: Boolean = false,
    val terrain: Boolean = false,
)
