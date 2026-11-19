package io.github.seijikohara.femto.data.map.offline

/** Rings as packed longitude/latitude pairs; containment is even-odd over all rings, so holes work. */
internal class RegionPolygon(
    val rings: List<DoubleArray>,
) {
    fun contains(
        longitude: Double,
        latitude: Double,
    ): Boolean = rings.count { ringContains(it, longitude, latitude) } % 2 == 1

    private fun ringContains(
        ring: DoubleArray,
        longitude: Double,
        latitude: Double,
    ): Boolean {
        // Ray casting walks each edge against its predecessor, so it needs the previous index; an index loop is the clear form.
        var inside = false
        var previous = ring.size / 2 - 1
        for (current in 0 until ring.size / 2) {
            val x1 = ring[current * 2]
            val y1 = ring[current * 2 + 1]
            val x2 = ring[previous * 2]
            val y2 = ring[previous * 2 + 1]
            if ((y1 > latitude) != (y2 > latitude) && longitude < (x2 - x1) * (latitude - y1) / (y2 - y1) + x1) {
                inside = !inside
            }
            previous = current
        }
        return inside
    }
}
