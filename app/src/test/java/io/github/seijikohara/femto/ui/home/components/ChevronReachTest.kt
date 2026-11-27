package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MapBackend
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

/**
 * How far above its centre [chevronReach] says the vehicle's chevron may draw: the 34 dp box's tip heading-up, its
 * corner once north-up turns it, squashed by the tilt on the OpenStreetMap map, and the corner unsquashed on Google,
 * whose raster map lays no perspective on it and whose rendering the launcher cannot tell.
 */
class ChevronReachTest {
    @Test
    fun osm_heading_up_reaches_the_tip_squashed_by_the_tilt() =
        assertEquals(8.5f, chevronReach(MapConfig(backend = MapBackend.OSM, tiltDeg = 60)).value, TOLERANCE)

    @Test
    fun osm_untilted_reaches_the_whole_tip() =
        assertEquals(17f, chevronReach(MapConfig(backend = MapBackend.OSM, tiltDeg = 0)).value, TOLERANCE)

    @Test
    fun osm_north_up_reaches_the_corner_squashed_by_the_tilt() =
        assertEquals(
            CORNER / 2f,
            chevronReach(MapConfig(backend = MapBackend.OSM, tiltDeg = 60, northUp = true)).value,
            TOLERANCE,
        )

    @Test
    fun google_reaches_the_corner_whatever_the_tilt_and_orientation() =
        listOf(false, true).forEach { northUp ->
            assertEquals(
                CORNER,
                chevronReach(MapConfig(backend = MapBackend.GOOGLEMAPS, tiltDeg = 60, northUp = northUp)).value,
                TOLERANCE,
            )
        }

    private companion object {
        const val TOLERANCE = 1e-3f
        val CORNER = 17f * sqrt(2f)
    }
}
