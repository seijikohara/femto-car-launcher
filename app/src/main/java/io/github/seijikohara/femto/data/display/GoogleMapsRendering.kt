package io.github.seijikohara.femto.data.display

/**
 * How the Google Maps backend asks the Maps JavaScript API to render.
 *
 * The API decides this at construction and exposes it as `MapOptions.renderingType`;
 * an explicit value **overrides** whatever the Cloud Map ID is configured for, so
 * [AUTO] deliberately sends nothing and leaves that configuration in charge.
 *
 * Only a [VECTOR] map supports the launcher's heading-up rotation and tilt — on a
 * raster map the API reinterprets both (heading applies to aerial imagery only and
 * snaps to available angles; tilt accepts just 0 or 45 as an imagery-switching
 * policy), which is why the backend omits them entirely when it renders raster.
 * Since Maps JS 3.56.10 vector no longer needs a Map ID, so the two are independent
 * choices. Vector is a *request*: the API silently falls back to raster on a device
 * that cannot support it, which the backend detects after the first tile load.
 */
internal enum class GoogleMapsRendering { AUTO, RASTER, VECTOR }
