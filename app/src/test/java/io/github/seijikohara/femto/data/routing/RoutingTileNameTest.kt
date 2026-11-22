package io.github.seijikohara.femto.data.routing

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * The tile a point falls in, as BRouter names the file it opens: the south-west corner floored to 5°, west and south
 * as `W` / `S` with the absolute value, and a point on a 5° line in the tile east or north of it.
 */
@RunWith(Parameterized::class)
internal class RoutingTileNameTest(
    private val latitude: Double,
    private val longitude: Double,
    private val expected: String,
) {
    @Test
    fun `names the tile that holds the point`() {
        assertEquals(expected, routingTileName(RoutePoint(latitude, longitude)))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "lat={0}, lon={1} -> {2}")
        fun cases(): List<Array<Any>> =
            listOf(
                arrayOf(35.7, 139.7, "E135_N35"),
                arrayOf(33.84, 132.77, "E130_N30"),
                arrayOf(-2.0, -3.0, "W5_S5"),
                arrayOf(51.5, -0.1, "W5_N50"),
                arrayOf(-33.9, 151.2, "E150_S35"),
                arrayOf(-22.9, -43.2, "W45_S25"),
                // On a 5° line: the tile east or north of it.
                arrayOf(35.0, 135.0, "E135_N35"),
                arrayOf(0.0, 0.0, "E0_N0"),
                arrayOf(-5.0, -5.0, "W5_S5"),
                // Just inside the tile west or south of the line.
                arrayOf(34.99999, 134.99999, "E130_N30"),
                // BRouter rounds to whole micro-degrees, so a point a fraction of one west of the line is on it.
                arrayOf(0.0, -0.0000004, "E0_N0"),
                // The edges of the world, where no tile lies east of 180° or north of 90°.
                arrayOf(-90.0, -180.0, "W180_S90"),
                arrayOf(90.0, 180.0, "E175_N85"),
            )
    }
}
