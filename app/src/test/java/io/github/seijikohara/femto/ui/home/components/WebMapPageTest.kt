package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapboxStyle
import org.junit.Test
import kotlin.test.assertEquals

class WebMapPageTest {
    @Test fun `osm backend loads map_html`() {
        assertEquals(
            "https://appassets.androidplatform.net/assets/web/map.html",
            mapPageUrl(MapBackend.OSM),
        )
    }

    @Test fun `mapbox backend loads mapbox_html`() {
        assertEquals(
            "https://appassets.androidplatform.net/assets/web/mapbox.html",
            mapPageUrl(MapBackend.MAPBOX),
        )
    }

    @Test fun `mapbox style ids match mapbox slugs`() {
        assertEquals("standard", mapboxStyleId(MapboxStyle.STANDARD))
        assertEquals("satellite-streets-v12", mapboxStyleId(MapboxStyle.SATELLITE))
        assertEquals("streets-v12", mapboxStyleId(MapboxStyle.STREETS))
    }

    @Test fun `lightPreset follows dark mode`() {
        assertEquals("night", lightPresetFor(dark = true))
        assertEquals("day", lightPresetFor(dark = false))
    }
}
