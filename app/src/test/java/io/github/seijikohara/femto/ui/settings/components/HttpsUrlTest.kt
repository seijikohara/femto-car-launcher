package io.github.seijikohara.femto.ui.settings.components

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpsUrlTest {
    @Test
    fun `accepts an https origin, with or without a path and query, ignoring surrounding whitespace`() {
        listOf(
            "https://tiles.example.test",
            "https://tiles.example.test/",
            "https://example.test/styles/basic/style.json?key=abc&v=2",
            "  https://example.test/style.json  ",
        ).forEach { assertTrue(isHttpsUrl(it), it) }
    }

    @Test
    fun `rejects what the map page could not load from`() {
        listOf(
            // A bare host resolves against the page's own appassets origin.
            "tiles.example.test",
            // Mixed content: the WebView blocks it.
            "http://tiles.example.test",
            "https://",
            "https:///path",
            "https://example.test/sty le.json",
            "",
            "   ",
        ).forEach { assertFalse(isHttpsUrl(it), it) }
    }
}
