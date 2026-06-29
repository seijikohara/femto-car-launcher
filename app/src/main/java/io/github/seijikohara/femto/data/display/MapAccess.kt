package io.github.seijikohara.femto.data.display

/**
 * Resolve the effective map backend. A token-gated backend renders only when the
 * user has stored the required credential; otherwise fall back to OSM. The stored
 * preference is never mutated here, so clearing a credential falls back to OSM
 * and re-entering it auto-restores the backend without re-selecting the provider.
 */
internal fun effectiveBackend(
    stored: MapBackend,
    hasMapboxToken: Boolean,
    hasGoogleMapsKey: Boolean,
): MapBackend =
    when (stored) {
        MapBackend.MAPBOX -> if (hasMapboxToken) MapBackend.MAPBOX else MapBackend.OSM
        MapBackend.GOOGLEMAPS -> if (hasGoogleMapsKey) MapBackend.GOOGLEMAPS else MapBackend.OSM
        MapBackend.OSM -> MapBackend.OSM
    }
