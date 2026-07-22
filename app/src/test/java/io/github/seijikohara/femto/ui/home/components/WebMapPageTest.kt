package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapboxStyle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebMapPageTest {
    @Test fun `osm backend loads the entry page with the osm parameter`() {
        assertEquals(
            "https://appassets.androidplatform.net/assets/web/index.html?backend=osm",
            mapPageUrl(MapBackend.OSM),
        )
    }

    @Test fun `mapbox backend loads the entry page with the mapbox parameter`() {
        assertEquals(
            "https://appassets.androidplatform.net/assets/web/index.html?backend=mapbox",
            mapPageUrl(MapBackend.MAPBOX),
        )
    }

    @Test fun `google maps backend loads the entry page with the googlemaps parameter`() {
        assertEquals(
            "https://appassets.androidplatform.net/assets/web/index.html?backend=googlemaps",
            mapPageUrl(MapBackend.GOOGLEMAPS),
        )
    }

    @Test fun `native attribution overlay shows only for osm backend`() {
        // OSM hides its web-side attribution and relies on the native overlay; the
        // paid backends carry their own ToS-mandated in-WebView attribution, so the
        // host must not overlay the OSM/OpenMapTiles/OpenFreeMap credit on them.
        assertTrue(showsNativeAttribution(MapBackend.OSM))
        assertFalse(showsNativeAttribution(MapBackend.MAPBOX))
        assertFalse(showsNativeAttribution(MapBackend.GOOGLEMAPS))
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

    @Test fun `live reload retry backoff doubles then caps`() {
        assertEquals(5_000L, liveReloadRetryDelayMs(0))
        assertEquals(10_000L, liveReloadRetryDelayMs(1))
        assertEquals(20_000L, liveReloadRetryDelayMs(2))
        assertEquals(160_000L, liveReloadRetryDelayMs(5))
        // Past the budget-sized shift the delay stays at the cap (and stays a
        // well-defined Long shift for any attempt value).
        assertEquals(160_000L, liveReloadRetryDelayMs(9))
        assertEquals(160_000L, liveReloadRetryDelayMs(MAX_LIVE_RELOAD_RETRIES))
    }
}
