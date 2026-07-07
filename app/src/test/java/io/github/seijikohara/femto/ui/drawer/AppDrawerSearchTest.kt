package io.github.seijikohara.femto.ui.drawer

import io.github.seijikohara.femto.data.apps.DrawerLayout
import org.junit.Test
import kotlin.test.assertEquals

class AppDrawerSearchTest {
    @Test
    fun `ranks prefix matches before substring matches`() =
        assertEquals(
            listOf("Maps", "Music", "Gmail"),
            filterAndRank(listOf("Gmail", "Maps", "Music", "Phone"), "m") { it },
        )

    @Test
    fun `matches case-insensitively`() =
        assertEquals(
            listOf("maps", "MUSIC"),
            filterAndRank(listOf("maps", "MUSIC", "Phone"), "M") { it },
        )

    @Test
    fun `keeps input order within each partition`() =
        assertEquals(
            listOf("Mb", "Ma", "xMb", "xMa"),
            filterAndRank(listOf("xMb", "Mb", "xMa", "Ma"), "m") { it },
        )

    @Test
    fun `returns items unchanged for a blank query`() {
        val items = listOf("Gmail", "Maps")
        assertEquals(items, filterAndRank(items, "  ") { it })
    }

    @Test
    fun `returns empty when nothing matches`() = assertEquals(emptyList(), filterAndRank(listOf("Phone"), "z") { it })

    @Test
    fun `effectiveLayout forces LIST while a query is active`() {
        assertEquals(DrawerLayout.LIST, effectiveLayout(DrawerLayout.GRID, "m"))
        assertEquals(DrawerLayout.GRID, effectiveLayout(DrawerLayout.GRID, ""))
        assertEquals(DrawerLayout.GRID, effectiveLayout(DrawerLayout.GRID, "   "))
        assertEquals(DrawerLayout.LIST, effectiveLayout(DrawerLayout.LIST, ""))
    }

    @Test
    fun `resolveByOrder preserves the order argument, not the items argument`() =
        assertEquals(
            listOf("music", "maps"),
            resolveByOrder(items = listOf("maps", "music"), order = listOf("music", "maps")) { it },
        )

    @Test
    fun `resolveByOrder drops a key with no matching item`() =
        assertEquals(
            listOf("maps"),
            resolveByOrder(items = listOf("maps"), order = listOf("uninstalled", "maps")) { it },
        )

    @Test
    fun `resolveByOrder returns empty for an empty order`() =
        assertEquals(emptyList(), resolveByOrder(items = listOf("maps"), order = emptyList()) { it })
}
