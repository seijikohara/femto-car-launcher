package io.github.seijikohara.femto.data.display

import org.junit.Test
import kotlin.test.assertEquals

class MapAccessTest {
    @Test fun mapbox_with_token_resolves_to_mapbox() =
        assertEquals(MapBackend.MAPBOX, effectiveBackend(MapBackend.MAPBOX, hasMapboxToken = true))

    @Test fun mapbox_without_token_falls_back_to_osm() =
        assertEquals(MapBackend.OSM, effectiveBackend(MapBackend.MAPBOX, hasMapboxToken = false))

    @Test fun osm_with_token_stays_osm() =
        assertEquals(MapBackend.OSM, effectiveBackend(MapBackend.OSM, hasMapboxToken = true))

    @Test fun osm_without_token_stays_osm() =
        assertEquals(MapBackend.OSM, effectiveBackend(MapBackend.OSM, hasMapboxToken = false))
}
