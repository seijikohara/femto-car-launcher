package io.github.seijikohara.femto.data.routing

import kotlin.math.abs
import kotlin.math.max

/** The edge of one routing tile in degrees: brouter.de cuts the world into 5° squares. */
private const val TILE_DEGREES = 5

private const val MICRODEGREES = 1_000_000

// The margin around the box of origin and destination, per axis: AREA_MARGIN_SHARE of the box's span, at least
// MIN_AREA_MARGIN_DEGREES. Roads leave the straight line around coasts, bays and mountains, and a longer trip strays
// farther, hence a share of the span; the floor covers short trips next to a tile edge, where the matched road or the
// first detour can lie in the neighbouring tile (BRouter reads the data up to 1/16° around a point to match it to a
// road).
private const val AREA_MARGIN_SHARE = 0.2
private const val MIN_AREA_MARGIN_DEGREES = 0.25

// The largest longitude and latitude that still fall in a tile: E180 and N90 do not exist.
private const val MAX_LONGITUDE = 180.0 - 1.0 / MICRODEGREES
private const val MAX_LATITUDE = 90.0 - 1.0 / MICRODEGREES

/** One point on the earth, in degrees. */
internal data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
)

/**
 * The name of the routing tile that holds [point], as brouter.de names its files (`E135_N35` for `E135_N35.rd5`):
 * the tile's south-west corner, each axis floored to 5°.
 *
 * The rule copies BRouter's own (`RoutingParamCollector` and `NodesCache.fileForSegment`), rounding included: BRouter
 * rounds a coordinate to whole micro-degrees offset to positive, so a point a fraction of a micro-degree west of a
 * tile edge belongs to the tile east of it, as BRouter reads it.
 */
internal fun routingTileName(point: RoutePoint): String =
    tileName(
        westDegrees = tileCornerDegrees(point.longitude.coerceIn(-180.0, MAX_LONGITUDE), offset = 180),
        southDegrees = tileCornerDegrees(point.latitude.coerceIn(-90.0, MAX_LATITUDE), offset = 90),
    )

/**
 * The names of the tiles a route from [origin] to [destination] may need: every tile that touches their bounding box
 * expanded by the area margin, west to east within south to north.
 */
internal fun routingTilesAround(
    origin: RoutePoint,
    destination: RoutePoint,
): List<String> {
    val longitudes = axisRange(origin.longitude, destination.longitude, offset = 180, limit = MAX_LONGITUDE)
    val latitudes = axisRange(origin.latitude, destination.latitude, offset = 90, limit = MAX_LATITUDE)
    return latitudes.flatMap { south -> longitudes.map { west -> tileName(west, south) } }
}

// The tile corners, in degrees, along one axis of the expanded box.
private fun axisRange(
    first: Double,
    second: Double,
    offset: Int,
    limit: Double,
): IntProgression {
    val margin = max(MIN_AREA_MARGIN_DEGREES, abs(first - second) * AREA_MARGIN_SHARE)
    val low = (minOf(first, second) - margin).coerceIn(-offset.toDouble(), limit)
    val high = (maxOf(first, second) + margin).coerceIn(-offset.toDouble(), limit)
    return tileCornerDegrees(low, offset)..tileCornerDegrees(high, offset) step TILE_DEGREES
}

// BRouter's micro-degree rounding, then the floor to the tile edge; the offset keeps the division on positive numbers.
private fun tileCornerDegrees(
    degrees: Double,
    offset: Int,
): Int {
    val shiftedDegrees = ((degrees + offset) * MICRODEGREES + 0.5).toInt() / MICRODEGREES
    return shiftedDegrees - shiftedDegrees % TILE_DEGREES - offset
}

private fun tileName(
    westDegrees: Int,
    southDegrees: Int,
): String =
    (if (westDegrees < 0) "W${-westDegrees}" else "E$westDegrees") +
        "_" +
        (if (southDegrees < 0) "S${-southDegrees}" else "N$southDegrees")
