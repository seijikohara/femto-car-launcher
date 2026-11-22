package io.github.seijikohara.femto.ui.drawer

import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import org.junit.Test
import kotlin.test.assertTrue

class DrawerDimensionsTest {
    @Test
    fun `every icon size preset keeps its grid cell at or above the touch-target floor`() =
        DrawerIconSize.entries.forEach { size ->
            assertTrue(size.dimensions().tileWidth >= FemtoDimens.MinTouchTarget, "$size")
        }

    @Test
    fun `each icon size preset is larger than the one before it in every metric`() =
        DrawerIconSize.entries.map { it.dimensions() }.zipWithNext().forEach { (smaller, larger) ->
            assertTrue(larger.tileWidth > smaller.tileWidth, "tile $smaller -> $larger")
            assertTrue(larger.gridIconSize > smaller.gridIconSize, "grid icon $smaller -> $larger")
            assertTrue(larger.listIconSize > smaller.listIconSize, "list icon $smaller -> $larger")
        }
}
