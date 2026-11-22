package io.github.seijikohara.femto.data.routing

import org.junit.Test
import kotlin.test.assertEquals

class RoutingTileAreaTest {
    @Test
    fun `a short trip inside one tile needs only that tile`() {
        assertEquals(listOf("E130_N30"), routingTilesAround(MATSUYAMA, IMABARI))
    }

    @Test
    fun `the minimum margin reaches into the next tile from a point near its edge`() {
        // 0.2° from the 135° line: inside the 0.25° floor, so the tile east of the line is asked for too.
        val nearEdge = RoutePoint(latitude = 34.5, longitude = 134.8)

        assertEquals(listOf("E130_N30", "E135_N30"), routingTilesAround(nearEdge, nearEdge))
    }

    @Test
    fun `a point just past the minimum margin stays in its own tile`() {
        val pastMargin = RoutePoint(latitude = 32.5, longitude = 134.7)

        assertEquals(listOf("E130_N30"), routingTilesAround(pastMargin, pastMargin))
    }

    @Test
    fun `a long trip widens the margin by a share of its span`() {
        // A span of 3.4° in latitude gives a margin of 0.68°, which reaches south of 30°; the 0.25° floor would not.
        val south = RoutePoint(latitude = 30.6, longitude = 131.0)
        val north = RoutePoint(latitude = 34.0, longitude = 131.0)

        assertEquals(listOf("E130_N25", "E130_N30"), routingTilesAround(south, north))
    }

    @Test
    fun `tiles are listed west to east within south to north`() {
        val tokyo = RoutePoint(latitude = 35.7, longitude = 139.7)
        val nagoya = RoutePoint(latitude = 35.18, longitude = 136.9)

        assertEquals(
            listOf("E135_N30", "E140_N30", "E135_N35", "E140_N35"),
            routingTilesAround(nagoya, tokyo),
        )
    }

    @Test
    fun `the order of origin and destination does not matter`() {
        val west = RoutePoint(latitude = -2.0, longitude = -6.0)
        val east = RoutePoint(latitude = 1.0, longitude = 2.0)

        assertEquals(routingTilesAround(west, east), routingTilesAround(east, west))
    }

    @Test
    fun `the margin stops at the edges of the world`() {
        val farNorthEast = RoutePoint(latitude = 89.9, longitude = 179.9)

        assertEquals(listOf("E175_N85"), routingTilesAround(farNorthEast, farNorthEast))
    }

    private companion object {
        val MATSUYAMA = RoutePoint(latitude = 33.84, longitude = 132.77)
        val IMABARI = RoutePoint(latitude = 34.07, longitude = 133.0)
    }
}
