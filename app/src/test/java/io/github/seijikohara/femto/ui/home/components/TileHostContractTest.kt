package io.github.seijikohara.femto.ui.home.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drift guard for the one tile-host fact that spans Kotlin, the bundled style
 * assets and the TypeScript page: the origin the styles are written against.
 *
 * The page re-points requests whose URL starts with `UPSTREAM_TILE_HOST`
 * (`webmap/src/style.ts`) at the configured host. Nothing enforces that the
 * styles actually use that origin, and a mismatch has no symptom — the rewrite
 * silently matches nothing and every request keeps going to the hard-coded
 * origin, so a configured mirror is quietly ignored. These assertions are the
 * only place that failure becomes visible.
 */
class TileHostContractTest {
    @Test fun `the bundled styles and the hosted style base sit under the page's upstream origin`() {
        val upstream = upstreamTileHost()
        val styleUrls =
            listOf(LIGHT_STYLE_ASSET, DARK_STYLE_ASSET)
                .map { asset -> repoFile("app/src/main/assets/$asset").readText() }
                .flatMap { style -> REQUEST_URL.findAll(style).map { it.groupValues[1] } }
                .distinct()
        assertTrue("the bundled styles request no absolute URL", styleUrls.isNotEmpty())
        styleUrls.forEach { url ->
            assertTrue("$url is not under $upstream", url.startsWith("$upstream/"))
        }
        assertTrue(
            "$OFM_STYLE_BASE is not under $upstream",
            OFM_STYLE_BASE.startsWith("$upstream/"),
        )
    }

    // The literal the page rewrites from, read out of its own source: a Kotlin
    // copy of it would be the very drift this test exists to catch.
    private fun upstreamTileHost(): String =
        UPSTREAM_TILE_HOST_DECLARATION
            .find(repoFile("webmap/src/style.ts").readText())
            ?.groupValues
            ?.get(1)
            ?: error("UPSTREAM_TILE_HOST not found in webmap/src/style.ts")

    // Gradle runs unit tests with the module directory as the working directory.
    private fun repoFile(path: String): File = File("../$path")

    private companion object {
        val UPSTREAM_TILE_HOST_DECLARATION = Regex("""UPSTREAM_TILE_HOST\s*=\s*"([^"]+)"""")

        // Only the fields the page actually fetches: a style also carries
        // attribution HTML whose links are not requests and are not ours to
        // re-point.
        val REQUEST_URL = Regex("""["](?:url|tiles|sprite|glyphs)["]\s*:\s*\[?\s*["](https://[^"]+)["]""")
    }
}
