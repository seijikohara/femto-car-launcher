package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.routing.RoutePoint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The host's reading of the OSM page's `longpress` detail, which webmap/src/long-press.ts writes. */
class MapLongPressTest {
    @Test
    fun `a latitude and longitude pair is the pressed point`() {
        assertEquals(RoutePoint(35.681236, 139.767125), longPressPointOrNull("35.681236,139.767125"))
    }

    @Test
    fun `a southern and western point keeps its signs`() {
        assertEquals(RoutePoint(-33.8688, -151.2093), longPressPointOrNull("-33.868800,-151.209300"))
    }

    @Test
    fun `the edges of the earth are points`() {
        assertEquals(RoutePoint(-90.0, 180.0), longPressPointOrNull("-90.000000,180.000000"))
    }

    @Test
    fun `a latitude beyond the pole is no point`() {
        assertNull(longPressPointOrNull("90.5,139.7"))
    }

    @Test
    fun `a longitude beyond the antimeridian is no point`() {
        assertNull(longPressPointOrNull("35.6,180.5"))
    }

    @Test
    fun `a detail without two numbers is no point`() {
        listOf("", "35.6", "35.6,", ",139.7", "a,b", "35.6,139.7,0", "NaN,139.7", "35.6,Infinity").forEach { detail ->
            assertNull(longPressPointOrNull(detail), "detail \"$detail\"")
        }
    }
}
