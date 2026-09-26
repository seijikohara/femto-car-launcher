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
    val schemeLight: MapColorScheme = MapColorScheme.ACCENT,
    val schemeDark: MapColorScheme = MapColorScheme.ACCENT,
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
    // as ACCENT). May carry a provider key — see MapScheme.mapStyleRefFor.
    val customStyleUrl: String = "",
    val markerPos: Int = 70,
    // Fraction (0..0.5) of the map height the bottom speed overlay occupies,
    // measured at layout time (not a persisted setting). The marker drop is
    // clamped against it so the chevron always clears the overlay regardless of
    // markerPos or screen aspect; 0 means "unmeasured", leaving the MAX_MARKER_DROP
    // cap as the only bound.
    val bottomSafeFraction: Float = 0f,
    // Fraction (0..0.45) of the map width the right-hand floating cards occupy,
    // measured at layout time — the horizontal analogue of [bottomSafeFraction]:
    // the page places the marker clear of those cards (webmap/src/style.ts:
    // markerSpot, googleMarkerSpot). 0 keeps the marker centred (portrait, or no
    // cards).
    val rightSafeFraction: Float = 0f,
    // Fraction (0..0.45) of the map width the left-hand floating cards occupy —
    // the horizontal mirror of [rightSafeFraction], set when the dashboard anchors
    // to the driver's LEFT; the page places the marker clear of those cards the
    // same way. Only one of [rightSafeFraction] / [leftSafeFraction] is ever
    // non-zero; 0 keeps the marker centred.
    val leftSafeFraction: Float = 0f,
    val buildings3d: Boolean = false,
    val terrain: Boolean = false,
)
