package io.github.seijikohara.femto.data.billing

import io.github.seijikohara.femto.data.display.MapBackend
import org.junit.Test
import kotlin.test.assertEquals

class MapAccessTest {
    @Test fun `mapbox stored and unlocked resolves to mapbox`() =
        assertEquals(MapBackend.MAPBOX, effectiveBackend(MapBackend.MAPBOX, mapboxUnlocked = true))

    @Test fun `mapbox stored but locked falls back to osm`() =
        assertEquals(MapBackend.OSM, effectiveBackend(MapBackend.MAPBOX, mapboxUnlocked = false))

    @Test fun `osm stored stays osm regardless of entitlement`() {
        assertEquals(MapBackend.OSM, effectiveBackend(MapBackend.OSM, mapboxUnlocked = true))
        assertEquals(MapBackend.OSM, effectiveBackend(MapBackend.OSM, mapboxUnlocked = false))
    }
}
