package io.github.seijikohara.femto.data.display

import org.junit.Test
import kotlin.test.assertEquals

class MapAccessTest {
    @Test fun mapbox_with_token_resolves_to_mapbox() =
        assertEquals(MapBackend.MAPBOX, effectiveBackend(MapBackend.MAPBOX, hasMapboxToken = true, hasGoogleMapsKey = false))

    @Test fun mapbox_without_token_falls_back_to_osm() =
        assertEquals(MapBackend.OSM, effectiveBackend(MapBackend.MAPBOX, hasMapboxToken = false, hasGoogleMapsKey = false))

    @Test fun osm_with_token_stays_osm() =
        assertEquals(MapBackend.OSM, effectiveBackend(MapBackend.OSM, hasMapboxToken = true, hasGoogleMapsKey = false))

    @Test fun osm_without_token_stays_osm() =
        assertEquals(MapBackend.OSM, effectiveBackend(MapBackend.OSM, hasMapboxToken = false, hasGoogleMapsKey = false))

    @Test fun googleMapsKeptWhenKeyPresent() {
        assertEquals(
            MapBackend.GOOGLEMAPS,
            effectiveBackend(MapBackend.GOOGLEMAPS, hasMapboxToken = false, hasGoogleMapsKey = true),
        )
    }

    @Test fun googleMapsFallsBackToOsmWithoutKey() {
        assertEquals(
            MapBackend.OSM,
            effectiveBackend(MapBackend.GOOGLEMAPS, hasMapboxToken = false, hasGoogleMapsKey = false),
        )
    }
}
