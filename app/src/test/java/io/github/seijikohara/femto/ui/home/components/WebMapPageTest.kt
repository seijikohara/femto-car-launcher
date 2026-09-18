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

    @Test fun `native attribution overlay shows only for osm backend on the default provider's styles`() {
        // OSM hides its web-side attribution and relies on the native overlay; the
        // Google Maps backend carries its own ToS-mandated in-WebView attribution,
        // so the host must not overlay the OSM/OpenMapTiles/OpenFreeMap credit on it.
        val accent = MapStyleRef.Accent(LIGHT_STYLE_ASSET)
        assertTrue(showsNativeAttribution(MapBackend.OSM, accent))
        assertTrue(showsNativeAttribution(MapBackend.OSM, MapStyleRef.Hosted(POSITRON_STYLE_URL)))
        assertFalse(showsNativeAttribution(MapBackend.GOOGLEMAPS, accent))
    }

    @Test fun `a custom style hands attribution to the page`() {
        // The host cannot know what a user-supplied style draws on, so its fixed
        // credit would be wrong; the page's MapLibre control reads the style's own.
        val custom = MapStyleRef.Hosted("https://example.test/style.json", custom = true)
        assertFalse(showsNativeAttribution(MapBackend.OSM, custom))
        // The bundled dark base is the default provider's data like the rest.
        assertTrue(showsNativeAttribution(MapBackend.OSM, MapStyleRef.Bundled(DARK_STYLE_ASSET)))
    }

    @Test fun `the style push quotes the url as a JS string and carries the attribution flag`() {
        // A custom URL is user input landing inside a script; a quote or a
        // backslash in it must become an escaped character, never code.
        val script =
            setStyleUrlScript(
                url = "https://example.test/s.json?key=a'b\\c\"d",
                accent = null,
                pageAttribution = true,
            )
        assertEquals(
            "window.setStyleUrl && setStyleUrl(\"https://example.test/s.json?key=a'b\\\\c\\\"d\", " +
                "'', '', '', '', '', '', '', '', true)",
            script,
        )
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
