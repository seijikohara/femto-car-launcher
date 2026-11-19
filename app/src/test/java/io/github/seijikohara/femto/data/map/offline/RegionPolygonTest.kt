package io.github.seijikohara.femto.data.map.offline

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionPolygonTest {
    private val square = doubleArrayOf(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0, 0.0, 0.0)
    private val hole = doubleArrayOf(4.0, 4.0, 6.0, 4.0, 6.0, 6.0, 4.0, 6.0, 4.0, 4.0)
    private val farSquare = doubleArrayOf(100.0, 50.0, 110.0, 50.0, 110.0, 60.0, 100.0, 60.0, 100.0, 50.0)

    @Test
    fun `a point inside the ring is contained`() = assertTrue(RegionPolygon(listOf(square)).contains(5.0, 2.0))

    @Test
    fun `a point outside the ring is not contained`() = assertFalse(RegionPolygon(listOf(square)).contains(11.0, 2.0))

    @Test
    fun `a point in a hole is not contained`() = assertFalse(RegionPolygon(listOf(square, hole)).contains(5.0, 5.0))

    @Test
    fun `a point between the hole and the outer ring is contained`() =
        assertTrue(RegionPolygon(listOf(square, hole)).contains(2.0, 5.0))

    @Test
    fun `a second polygon contains its own points`() {
        val polygon = RegionPolygon(listOf(square, farSquare))
        assertTrue(polygon.contains(5.0, 5.0))
        assertTrue(polygon.contains(105.0, 55.0))
        assertFalse(polygon.contains(50.0, 30.0))
    }

    @Test
    fun `an empty polygon contains nothing`() = assertFalse(RegionPolygon(emptyList()).contains(0.0, 0.0))
}
