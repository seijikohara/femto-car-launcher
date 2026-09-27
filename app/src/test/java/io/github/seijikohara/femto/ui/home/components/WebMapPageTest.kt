package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.testfixtures.BoundedFailureDetails
import io.github.seijikohara.femto.testfixtures.NetworkFailureDetails
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test fun `live reload retry backoff doubles then caps at a minute`() {
        assertEquals(5_000L, liveReloadRetryDelayMs(0))
        assertEquals(10_000L, liveReloadRetryDelayMs(1))
        assertEquals(20_000L, liveReloadRetryDelayMs(2))
        assertEquals(40_000L, liveReloadRetryDelayMs(3))
        assertEquals(60_000L, liveReloadRetryDelayMs(4))
        // Past the cap the delay stays there, and the shift stays a
        // well-defined Long shift for any attempt value.
        assertEquals(60_000L, liveReloadRetryDelayMs(9))
        assertEquals(60_000L, liveReloadRetryDelayMs(Int.MAX_VALUE))
    }

    @Test fun `a network failure keeps retrying at the capped delay after the bounded budget`() {
        // What a page opened without data reports: the OSM page once no tile
        // has arrived, a hosted style that never loaded, the Google Maps
        // script that never arrived.
        NetworkFailureDetails.forEach { detail ->
            assertEquals(
                60_000L,
                liveReloadRetryDelayMsOrNull(detail, MAX_LIVE_RELOAD_RETRIES + 3, online = true),
                detail,
            )
        }
    }

    @Test fun `a network failure retries without a validated network`() {
        // Data can come back with no offline->online edge (a hotspot whose
        // network stayed VALIDATED while its upstream was gone), so the retry
        // must not wait for the validated signal.
        NetworkFailureDetails.forEach { detail ->
            assertEquals(5_000L, liveReloadRetryDelayMsOrNull(detail, 0, online = false), detail)
            assertEquals(60_000L, liveReloadRetryDelayMsOrNull(detail, 20, online = false), detail)
        }
    }

    @Test fun `credential, configuration and WebGL failures keep the bounded budget while online`() {
        // A rejected BYO key must not hammer the provider, a refused URL does
        // not heal by itself, and every Google reload after the map object
        // exists is a billed map load.
        BoundedFailureDetails.forEach { detail ->
            assertEquals(liveReloadRetryDelayMs(0), liveReloadRetryDelayMsOrNull(detail, 0, online = true), detail)
            val lastAttempt = MAX_LIVE_RELOAD_RETRIES - 1
            assertEquals(
                liveReloadRetryDelayMs(lastAttempt),
                liveReloadRetryDelayMsOrNull(detail, lastAttempt, online = true),
                detail,
            )
            assertNull(liveReloadRetryDelayMsOrNull(detail, MAX_LIVE_RELOAD_RETRIES, online = true), detail)
            assertNull(liveReloadRetryDelayMsOrNull(detail, 0, online = false), detail)
        }
    }

    @Test fun `a visible launcher retries a failed page on the backoff`() {
        assertEquals(
            LiveReloadStep.Retry(delayMs = 20_000L, onReturn = false),
            reloadStep(retryDelayMs = 20_000L),
        )
    }

    @Test fun `a hidden launcher holds every reload`() {
        assertEquals(LiveReloadStep.Held, reloadStep(started = false, retryDelayMs = 20_000L))
        assertEquals(LiveReloadStep.Held, reloadStep(started = false, reconnectPending = true))
        assertEquals(
            LiveReloadStep.Held,
            reloadStep(started = false, reconnectPending = true, pageFromReturnReload = true),
        )
    }

    @Test fun `the return reloads what came due while hidden at once`() {
        assertEquals(
            LiveReloadStep.Retry(delayMs = 0L, onReturn = true),
            reloadStep(retryDelayMs = 60_000L, heldWhileHidden = true),
        )
    }

    @Test fun `a reconnect reloads at once and wins over a retry`() {
        assertEquals(
            LiveReloadStep.Reconnect,
            reloadStep(reconnectPending = true, retryDelayMs = 60_000L, heldWhileHidden = true),
        )
    }

    @Test fun `a reconnect reaching the page the return reload built reloads nothing more`() {
        // The dashboard's state catches up only after the return, so an edge
        // from behind another app arrives after that reload.
        assertEquals(
            LiveReloadStep.CoveredReconnect,
            reloadStep(reconnectPending = true, pageFromReturnReload = true),
        )
    }

    @Test fun `nothing reloads when nothing is due`() {
        listOf(true, false).forEach { started ->
            assertEquals(
                LiveReloadStep.None,
                reloadStep(started = started, heldWhileHidden = true, pageFromReturnReload = true),
            )
        }
    }

    private fun reloadStep(
        started: Boolean = true,
        reconnectPending: Boolean = false,
        retryDelayMs: Long? = null,
        heldWhileHidden: Boolean = false,
        pageFromReturnReload: Boolean = false,
    ) = liveReloadStep(
        started = started,
        reconnectPending = reconnectPending,
        retryDelayMs = retryDelayMs,
        heldWhileHidden = heldWhileHidden,
        pageFromReturnReload = pageFromReturnReload,
    )

    @Test fun `an OSM tile host or hosted style that refused the map gets its own notice`() {
        // Not the provider-switch advice: the page is already on OpenStreetMap.
        listOf(
            "tile-host-rejected: AJAXError: Forbidden (403): https://tiles.example.test/planet",
            "style-load-rejected: AJAXError: Not Found (404): https://tiles.openfreemap.org/styles/positron",
        ).forEach { detail ->
            assertEquals(
                LiveMapNoticeText(R.string.map_live_data_refused, R.string.map_live_data_refused_hint),
                osmNotice(fatalDetail = detail),
                detail,
            )
        }
    }

    @Test fun `unreachable OSM data gets the notice that it reloads by itself`() {
        assertEquals(
            LiveMapNoticeText(R.string.map_live_data_unavailable, R.string.map_live_data_unavailable_hint),
            osmNotice(fatalDetail = NetworkFailureDetails.first()),
        )
    }

    @Test fun `a custom style keeps its own notice, refused or not`() {
        listOf(
            "style-load-rejected: AJAXError: Not Found (404): https://styles.example.test/basic/style.json",
            "style-load-failed: AJAXError: Failed to fetch (0): https://styles.example.test/basic/style.json",
        ).forEach { detail ->
            assertEquals(
                LiveMapNoticeText(R.string.map_custom_style_failed, R.string.map_custom_style_failed_hint),
                osmNotice(fatalDetail = detail, customStyleActive = true),
                detail,
            )
        }
    }

    @Test fun `other OSM failures keep the generic notice`() {
        assertEquals(
            LiveMapNoticeText(R.string.map_live_init_failed, R.string.map_live_init_failed_hint),
            osmNotice(fatalDetail = "no-webgl-context"),
        )
    }

    private fun osmNotice(
        fatalDetail: String,
        customStyleActive: Boolean = false,
    ) = liveMapNoticeText(
        rendererGaveUp = false,
        googleMapsKeyMissing = false,
        googleMapsBackend = false,
        customStyleActive = customStyleActive,
        fatalDetail = fatalDetail,
    )

    @Test fun `a failure is a network failure only by its leading kind`() {
        NetworkFailureDetails.forEach { assertTrue(isNetworkFailure(it), it) }
        BoundedFailureDetails.forEach { assertFalse(isNetworkFailure(it), it) }
        // The text after the kind is free (an exception message, a URL).
        assertFalse(isNetworkFailure("map-init-exception: style-load-failed: x"))
        assertFalse(isNetworkFailure(""))
    }
}
