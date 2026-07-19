package io.github.seijikohara.femto.data.location

import io.github.seijikohara.femto.testfixtures.fakeTrackPoint
import org.junit.Test
import kotlin.math.abs
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
    fun `dark palette passes the track speed colours through verbatim`() {
        val geometry = geometryOf(altitude = false, points = 6)!!
        val wire = TripWireframe.build(geometry, TripScenePalette.Dark)
        // The dark scene is the tuned-for reference: the first track vertex's
        // colour must be the geometry's turbo colour untouched.
        assertEquals(geometry.vertices[3], wire[3], 1e-6f)
        assertEquals(geometry.vertices[4], wire[4], 1e-6f)
        assertEquals(geometry.vertices[5], wire[5], 1e-6f)
    }

    @Test
    fun `light palette tones every track colour into the jewel band`() {
        val geometry = geometryOf(altitude = false, points = 6)!!
        val dark = TripWireframe.build(geometry, TripScenePalette.Dark)
        val light = TripWireframe.build(geometry, lightPalette())

        // Same geometry, so the buffers are the same length; only colours differ.
        assertEquals(dark.size, light.size)
        // Every track vertex's HSV value (max channel) sits inside the light
        // band — never near-black on the light backdrop, never blown out. The
        // flat trip has no curtain, so everything before the grid is track.
        val f = TripWireframe.FLOATS_PER_VERTEX
        val trackVertices = (0 until light.size / f).takeWhile { light[it * f + 6] != TripWireframe.ALWAYS_ON }
        assertTrue(trackVertices.isNotEmpty())
        trackVertices.forEach { i ->
            val base = i * f
            val value = maxOf(light[base + 3], light[base + 4], light[base + 5])
            assertTrue(value in 0.49f..0.83f, "track vertex $i value $value outside the light band")
        }
        // The always-on grid tail carries the palette grid colour verbatim.
        val gridBase = light.size - f
        assertEquals(TripWireframe.ALWAYS_ON, light[gridBase + 6])
        assertEquals(0.2f, light[gridBase + 3], 1e-6f)
        assertEquals(0.1f, light[gridBase + 4], 1e-6f)
        assertEquals(0.05f, light[gridBase + 5], 1e-6f)
    }

    @Test
    fun `light curtain recedes toward the light backdrop`() {
        val geometry = geometryOf(altitude = true, points = 8)!!
        val light = TripWireframe.build(geometry, lightPalette())
        val f = TripWireframe.FLOATS_PER_VERTEX
        // The curtain sits between the track pairs and the grid tail as
        // (top, bottom) vertex pairs — a top can also sit at y == 0 at the
        // trip's lowest point, so bottoms are identified by pair parity, not by
        // y. Bottoms must be near the light backdrop — the ribs fade into the
        // scene instead of hanging as dark bars.
        val trackVertexCount = geometry.segments.sumOf { (it.last - it.first) * 2 }
        val bottomBases =
            (trackVertexCount until light.size / f)
                .filter { light[it * f + 6] != TripWireframe.ALWAYS_ON && (it - trackVertexCount) % 2 == 1 }
                .map { it * f }
        assertTrue(bottomBases.isNotEmpty())
        bottomBases.forEach { base ->
            val distanceToBackground =
                maxOf(
                    abs(light[base + 3] - 0.9f),
                    abs(light[base + 4] - 0.9f),
                    abs(light[base + 5] - 0.9f),
                )
            assertTrue(distanceToBackground < 0.15f, "curtain bottom not receded (delta $distanceToBackground)")
        }
    }

    private fun lightPalette() =
        TripScenePalette(
            isDark = false,
            background = floatArrayOf(0.9f, 0.9f, 0.9f),
            grid = floatArrayOf(0.2f, 0.1f, 0.05f),
            head = floatArrayOf(0f, 0f, 0f),
        )

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
