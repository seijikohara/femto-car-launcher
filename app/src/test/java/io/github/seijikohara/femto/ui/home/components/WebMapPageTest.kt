package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MapBackend
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

    @Test fun `google maps backend loads the entry page with the googlemaps parameter`() {
        assertEquals(
            "https://appassets.androidplatform.net/assets/web/index.html?backend=googlemaps",
            mapPageUrl(MapBackend.GOOGLEMAPS),
        )
    }

    @Test fun `native attribution overlay shows only for osm backend`() {
        // OSM hides its web-side attribution and relies on the native overlay; the
        // Google Maps backend carries its own ToS-mandated in-WebView attribution,
        // so the host must not overlay the OSM/OpenMapTiles/OpenFreeMap credit on it.
        assertTrue(showsNativeAttribution(MapBackend.OSM))
        assertFalse(showsNativeAttribution(MapBackend.GOOGLEMAPS))
    }

    @Test fun `tile hosts put the override first and the build default behind it`() {
        assertEquals(
            listOf("https://tiles.example.test", "https://tiles.openfreemap.org"),
            mapTileHosts(override = " https://tiles.example.test/ ", default = "https://tiles.openfreemap.org"),
        )
    }

    @Test fun `a blank override leaves only the default host`() {
        assertEquals(
            listOf("https://tiles.openfreemap.org"),
            mapTileHosts(override = "", default = "https://tiles.openfreemap.org"),
        )
        // An override that merely repeats the default must not double the list,
        // or half the retry budget would reload the same dead host.
        assertEquals(
            listOf("https://tiles.openfreemap.org"),
            mapTileHosts(override = "https://tiles.openfreemap.org/", default = "https://tiles.openfreemap.org"),
        )
    }

    @Test fun `retries walk the host list round-robin`() {
        val hosts = listOf("a", "b")
        assertEquals("a", tileHostForAttempt(hosts, 0))
        assertEquals("b", tileHostForAttempt(hosts, 1))
        assertEquals("a", tileHostForAttempt(hosts, 2))
        assertEquals("only", tileHostForAttempt(listOf("only"), 5))
        // A build that blanked MAP_TILE_HOST with no override leaves no host to
        // walk; the page falls back to the upstream origin rather than crashing
        // the modulo.
        assertEquals("", tileHostForAttempt(emptyList(), 0))
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
