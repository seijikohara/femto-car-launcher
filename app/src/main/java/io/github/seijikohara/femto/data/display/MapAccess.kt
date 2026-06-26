package io.github.seijikohara.femto.data.display

/**
 * Resolve the effective map backend. Mapbox renders only when the user has
 * stored an access token; otherwise fall back to OSM. The stored preference is
 * never mutated here, so clearing the token falls back to OSM and re-entering it
 * auto-restores Mapbox without re-selecting the provider.
 */
internal fun effectiveBackend(
    stored: MapBackend,
    hasMapboxToken: Boolean,
): MapBackend = if (stored == MapBackend.MAPBOX && hasMapboxToken) MapBackend.MAPBOX else MapBackend.OSM
