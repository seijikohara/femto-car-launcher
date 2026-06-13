package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MapColorScheme
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapSchemeTest {
    @Test
    fun `accent resolves to the light base in the light context`() {
        val ref = mapStyleRefFor(MapColorScheme.ACCENT, isDark = false)
        assertTrue(ref is MapStyleRef.Accent)
        assertEquals(LIGHT_STYLE_ASSET, ref.baseAsset)
    }

    @Test
    fun `accent resolves to the dark base in the dark context`() {
        val ref = mapStyleRefFor(MapColorScheme.ACCENT, isDark = true)
        assertTrue(ref is MapStyleRef.Accent)
        assertEquals(DARK_STYLE_ASSET, ref.baseAsset)
    }

    @Test
    fun `dark matter resolves to the bundled dark asset`() {
        val ref = mapStyleRefFor(MapColorScheme.DARK_MATTER, isDark = true)
        assertEquals(MapStyleRef.Bundled(DARK_STYLE_ASSET), ref)
    }

    @Test
    fun `fixed schemes resolve to their hosted OpenFreeMap urls`() {
        val expected =
            mapOf(
                MapColorScheme.POSITRON to POSITRON_STYLE_URL,
                MapColorScheme.BRIGHT to OFM_STYLE_BASE + "bright",
                MapColorScheme.LIBERTY to OFM_STYLE_BASE + "liberty",
                MapColorScheme.DARK to OFM_STYLE_BASE + "dark",
                MapColorScheme.FIORD to OFM_STYLE_BASE + "fiord",
            )
        expected.forEach { (scheme, url) ->
            assertEquals(MapStyleRef.Hosted(url), mapStyleRefFor(scheme, isDark = false))
        }
    }
}
