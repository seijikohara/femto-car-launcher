package io.github.seijikohara.femto.data.location

import io.github.seijikohara.femto.testfixtures.fakeTrackPoint
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripGeometryTest {
    // Offsets into one interleaved vertex: x, y, z, r, g, b, distanceFraction.
    private fun TripGeometry.vertex(
        index: Int,
        offset: Int,
    ): Float = vertices[index * TripGeometry.FLOATS_PER_VERTEX + offset]

    // A steadily moving trip along a diagonal; altitude defaults to absent so
    // each test opts in to relief explicitly.
    private fun straightTrip(
        count: Int,
        speedMps: (Int) -> Float? = { 10f },
        altitudeM: (Int) -> Double? = { null },
    ): List<TrackPointEntity> =
        List(count) { i ->
            fakeTrackPoint(
                timeMs = BASE_MS + i * 1_000L,
                latitude = 35.0 + i * 1e-4,
                longitude = 139.0 + i * 1e-4,
                speedMps = speedMps(i),
                altitudeM = altitudeM(i),
            )
        }

    @Test
    fun `returns null for fewer than two points`() {
        assertNull(TripGeometry.from(emptyList()))
        assertNull(TripGeometry.from(straightTrip(1)))
    }

    @Test
    fun `builds geometry for two or more points`() {
        assertNotNull(TripGeometry.from(straightTrip(2)))
    }

    @Test
    fun `interleaves seven floats per input point`() {
        val geometry = assertNotNull(TripGeometry.from(straightTrip(10)))

        assertEquals(10, geometry.vertexCount)
        assertEquals(10 * TripGeometry.FLOATS_PER_VERTEX, geometry.vertices.size)
    }

    @Test
    fun `distance fraction starts at zero and ends at one`() {
        val geometry = assertNotNull(TripGeometry.from(straightTrip(10)))

        assertEquals(0f, geometry.vertex(0, 6), 0f)
        assertEquals(1f, geometry.vertex(geometry.vertexCount - 1, 6), 1e-4f)
    }

    @Test
    fun `distance fraction never decreases along the trip`() {
        val geometry = assertNotNull(TripGeometry.from(straightTrip(20)))

        (1 until geometry.vertexCount).forEach { i ->
            assertTrue(
                geometry.vertex(i, 6) >= geometry.vertex(i - 1, 6),
                "fraction dipped at vertex $i",
            )
        }
    }

    @Test
    fun `rising altitude produces rising y`() {
        val geometry =
            assertNotNull(TripGeometry.from(straightTrip(10, altitudeM = { 100.0 + it * 5.0 })))

        (1 until geometry.vertexCount).forEach { i ->
            assertTrue(
                geometry.vertex(i, 1) > geometry.vertex(i - 1, 1),
                "y did not rise at vertex $i",
            )
        }
    }

    @Test
    fun `altitude-less trip lies flat at zero y`() {
        val geometry = assertNotNull(TripGeometry.from(straightTrip(10, altitudeM = { null })))

        (0 until geometry.vertexCount).forEach { i ->
            assertEquals(0f, geometry.vertex(i, 1), 0f)
        }
    }

    @Test
    fun `null altitudes are filled from neighbours instead of dropping to zero`() {
        // Real altitudes at the ends, null in the middle: the filled profile must
        // never dip to 0 m (which would spike the wireframe to the ground plane).
        val geometry =
            assertNotNull(
                TripGeometry.from(
                    straightTrip(5, altitudeM = { i -> if (i == 0 || i == 4) 100.0 + i * 10.0 else null }),
                ),
            )
        // y is normalized within the trip's own altitude span; every vertex must
        // stay strictly above the ground plane (no false 0 spike from the nulls).
        (0 until geometry.vertexCount).forEach { i ->
            assertTrue(geometry.vertex(i, 1) >= 0f, "y went below the ground at $i")
        }
        assertTrue(geometry.stats.hasAltitude)
    }

    @Test
    fun `altitude stats carry the trip's lowest and highest points`() {
        val profile = listOf(120.0, 140.0, 95.0, 180.0)
        val geometry = assertNotNull(TripGeometry.from(straightTrip(4, altitudeM = { profile[it] })))
        assertEquals(95.0, geometry.stats.minAltitudeM, 1e-6)
        assertEquals(180.0, geometry.stats.maxAltitudeM, 1e-6)
    }

    @Test
    fun `an antimeridian-crossing trip stays continuous`() {
        // Four close points straddling +/-180 deg. Without longitude unwrap the
        // projection would explode the extent and the distance stat; with it the
        // trip is a short continuous line.
        val lons = listOf(179.9, 179.95, -179.95, -179.9)
        val trip =
            List(4) { i ->
                fakeTrackPoint(timeMs = BASE_MS + i * 1_000L, latitude = 66.0, longitude = lons[i], speedMps = 10f)
            }
        val geometry = assertNotNull(TripGeometry.from(trip))
        (0 until geometry.vertexCount).forEach { i ->
            assertTrue(abs(geometry.vertex(i, 0)) <= 1f + EPSILON, "x escaped at $i")
        }
        // ~0.2 deg of longitude at 66 N is only a few km, not the ~40000 km a
        // wrapped delta would fabricate.
        assertTrue(geometry.stats.distanceMeters < 20_000.0, "distance was ${geometry.stats.distanceMeters}")
    }

    @Test
    fun `horizontal coordinates stay inside the unit box`() {
        val geometry = assertNotNull(TripGeometry.from(straightTrip(50)))

        (0 until geometry.vertexCount).forEach { i ->
            assertTrue(abs(geometry.vertex(i, 0)) <= 1f + EPSILON, "x escaped at vertex $i")
            assertTrue(abs(geometry.vertex(i, 2)) <= 1f + EPSILON, "z escaped at vertex $i")
        }
    }

    @Test
    fun `a continuous trip yields a single segment covering every vertex`() {
        val geometry = assertNotNull(TripGeometry.from(straightTrip(6)))

        assertEquals(listOf(0..5), geometry.segments)
    }

    @Test
    fun `a recording gap longer than the gap window splits the segments`() {
        // 70 s hole between the third and fourth fixes (> MAX_GAP_SECONDS).
        val offsets = longArrayOf(0, 1_000, 2_000, 72_000, 73_000, 74_000)
        val points =
            offsets.mapIndexed { i, offset ->
                fakeTrackPoint(
                    timeMs = BASE_MS + offset,
                    latitude = 35.0 + i * 1e-4,
                    longitude = 139.0 + i * 1e-4,
                )
            }

        val geometry = assertNotNull(TripGeometry.from(points))

        assertEquals(listOf(0..2, 3..5), geometry.segments)
    }

    @Test
    fun `speed variation colors the trip's ends differently`() {
        val geometry =
            assertNotNull(TripGeometry.from(straightTrip(2, speedMps = { if (it == 0) 1f else 30f })))

        val slowRgb = List(3) { geometry.vertex(0, 3 + it) }
        val fastRgb = List(3) { geometry.vertex(1, 3 + it) }
        assertFalse(slowRgb == fastRgb, "slow and fast points share the color $slowRgb")
    }

    @Test
    fun `stats span the first and last fix times`() {
        val stats = assertNotNull(TripGeometry.from(straightTrip(5))).stats

        assertEquals(BASE_MS, stats.startMs)
        assertEquals(BASE_MS + 4_000L, stats.endMs)
    }

    @Test
    fun `stats measure a positive distance for a moving trip`() {
        val stats = assertNotNull(TripGeometry.from(straightTrip(5))).stats

        assertTrue(stats.distanceMeters > 0.0)
    }

    @Test
    fun `stats keep average speed at or below max speed`() {
        val speeds = floatArrayOf(5f, 10f, 20f, 15f)
        val stats =
            assertNotNull(TripGeometry.from(straightTrip(4, speedMps = { speeds[it] }))).stats

        assertTrue(stats.maxSpeedMps >= stats.avgSpeedMps)
    }

    @Test
    fun `downsampling caps the vertex count and keeps the trip's end`() {
        val geometry =
            assertNotNull(TripGeometry.from(straightTrip(TripGeometry.MAX_RENDER_POINTS + 1_000)))

        assertTrue(geometry.vertexCount <= TripGeometry.MAX_RENDER_POINTS)
        assertEquals(1f, geometry.vertex(geometry.vertexCount - 1, 6), 1e-4f)
    }

    @Test
    fun `turbo returns unit-range rgb across its domain`() {
        listOf(0f, 0.5f, 1f).forEach { t ->
            val rgb = turbo(t)
            assertEquals(3, rgb.size)
            rgb.forEach { channel ->
                assertTrue(channel.isFinite() && channel in 0f..1f, "turbo($t) channel $channel")
            }
        }
    }

    @Test
    fun `turbo clamps inputs below zero to the start color`() {
        assertTrue(turbo(-1f).contentEquals(turbo(0f)))
    }

    @Test
    fun `turbo clamps inputs above one to the end color`() {
        assertTrue(turbo(2f).contentEquals(turbo(1f)))
    }

    @Test
    fun `turbo varies between nearby inputs`() {
        assertFalse(turbo(0.4f).contentEquals(turbo(0.5f)))
    }

    @Test
    fun `turbo start color is dark-safe rather than near black`() {
        assertTrue(turbo(0f).max() > 0.1f)
    }

    private companion object {
        const val BASE_MS = 1_752_710_400_000L // 2025-07-17T00:00:00Z
        const val EPSILON = 1e-4f
    }
}
