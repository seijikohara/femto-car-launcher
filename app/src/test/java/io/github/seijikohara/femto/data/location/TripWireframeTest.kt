package io.github.seijikohara.femto.data.location

import io.github.seijikohara.femto.testfixtures.fakeTrackPoint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TripWireframeTest {
    @Test
    fun `builds a well-formed line list with grid chrome`() {
        val geometry = geometryOf(altitude = false, points = 6)!!
        val wire = TripWireframe.build(geometry)

        // Whole array is line-list pairs: a multiple of 2 * floats-per-vertex.
        assertEquals(0, wire.size % (2 * TripWireframe.FLOATS_PER_VERTEX))
        // The grid is always present, so the list is never empty.
        assertTrue(wire.isNotEmpty())
    }

    @Test
    fun `grid vertices carry the always-on reveal marker`() {
        val wire = TripWireframe.build(geometryOf(altitude = false, points = 4)!!)
        // The last grid line pair's distance fraction is the always-on sentinel.
        val distFrac = wire[wire.size - 1]
        assertEquals(TripWireframe.ALWAYS_ON, distFrac)
    }

    @Test
    fun `flat trip omits the elevation curtain but a climbing trip includes it`() {
        val flat = TripWireframe.build(geometryOf(altitude = false, points = 8)!!).size
        val climbing = TripWireframe.build(geometryOf(altitude = true, points = 8)!!).size
        // The curtain adds vertical rib pairs, so the climbing wireframe is larger.
        assertTrue(climbing > flat)
    }

    @Test
    fun `every vertex is finite for a downsampled trip`() {
        val geometry = geometryOf(altitude = true, points = TripGeometry.MAX_RENDER_POINTS + 500)!!
        val wire = TripWireframe.build(geometry)
        assertTrue(wire.all { it.isFinite() })
        assertTrue(wire.isNotEmpty())
    }

    @Test
    fun `light palette scales the track speed colour and recolours the grid`() {
        val geometry = geometryOf(altitude = false, points = 6)!!
        val dark = TripWireframe.build(geometry, TripScenePalette.Dark)
        val lightPalette =
            TripScenePalette(
                isDark = false,
                background = floatArrayOf(0.9f, 0.9f, 0.9f),
                grid = floatArrayOf(0.2f, 0.1f, 0.05f),
                head = floatArrayOf(0f, 0f, 0f),
                lineScale = 0.5f,
            )
        val light = TripWireframe.build(geometry, lightPalette)

        // Same geometry, so the buffers are the same length; only colours differ.
        assertEquals(dark.size, light.size)
        // Track vertex 0 speed colour is scaled by lineScale (0.5) on the light scene.
        assertEquals(dark[3] * 0.5f, light[3], 1e-6f)
        assertEquals(dark[4] * 0.5f, light[4], 1e-6f)
        assertEquals(dark[5] * 0.5f, light[5], 1e-6f)
        // The always-on grid tail carries the palette grid colour verbatim.
        val gridBase = light.size - TripWireframe.FLOATS_PER_VERTEX
        assertEquals(TripWireframe.ALWAYS_ON, light[gridBase + 6])
        assertEquals(0.2f, light[gridBase + 3], 1e-6f)
        assertEquals(0.1f, light[gridBase + 4], 1e-6f)
        assertEquals(0.05f, light[gridBase + 5], 1e-6f)
    }

    private fun geometryOf(
        altitude: Boolean,
        points: Int,
    ): TripGeometry? =
        TripGeometry.from(
            (0 until points).map { i ->
                fakeTrackPoint(
                    tripId = 0L,
                    timeMs = i * 1_000L,
                    latitude = 35.6580 + i * 0.0005,
                    longitude = 139.7016 + i * 0.0003,
                    speedMps = 8f + (i % 5) * 2f,
                    altitudeM = if (altitude) 20.0 + i * 3.0 else null,
                )
            },
        )
}
