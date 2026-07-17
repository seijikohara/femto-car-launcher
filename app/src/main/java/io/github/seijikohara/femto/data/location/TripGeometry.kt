package io.github.seijikohara.femto.data.location

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A trip's recorded points transformed into a normalized 3D wireframe ready to
 * render — as a native Vulkan vertex buffer or the 2D Compose fallback.
 *
 * The track is projected to a local east/up/north metric frame centered on the
 * trip, then scaled so its horizontal extent fits a unit box on the XZ plane
 * (X = east, Z = north, both in roughly [-1, 1]). Altitude maps to Y: the
 * trip's own altitude span is stretched to a fixed fraction of the scene
 * ([ALT_TARGET_VFRAC]) so even a gentle hill reads as visible relief and a flat
 * or altitude-less trip lies flat rather than as a wall. Each vertex also
 * carries a speed color ([turbo] colormap — bright across its whole range on a
 * dark scene, unlike viridis/inferno which sink at the low end) and a
 * cumulative distance fraction in [0, 1] that drives the draw-on animation.
 *
 * A recording gap longer than [MAX_GAP_SECONDS] (the trip math's own gap window,
 * the SSOT) splits [segments] so the renderer breaks the line there instead of
 * drawing a false straight leap across the hole.
 *
 * [vertices] is interleaved [FLOATS_PER_VERTEX] floats per vertex:
 * `x, y, z, r, g, b, distanceFraction`. It is a raw FloatArray so it can be
 * uploaded to a GPU buffer without a per-vertex object walk.
 */
internal class TripGeometry private constructor(
    val vertices: FloatArray,
    val vertexCount: Int,
    /** Index ranges (into vertices, in vertex units) of continuous line strips. */
    val segments: List<IntRange>,
    val stats: TripStats,
) {
    companion object {
        const val FLOATS_PER_VERTEX = 7

        // Cap the rendered vertex count; a multi-hour trip is strided down to
        // this before projection so the buffer and the draw stay bounded.
        const val MAX_RENDER_POINTS = 4_000

        // The altitude span stretches to this fraction of the normalized scene,
        // so relief is always legible regardless of the trip's real metres.
        private const val ALT_TARGET_VFRAC = 0.35f

        private const val METERS_PER_DEG_LAT = 111_320.0

        /**
         * Build a [TripGeometry] from one trip's ordered points, or null when
         * there is nothing renderable (fewer than two points).
         */
        fun from(points: List<TrackPointEntity>): TripGeometry? {
            val sampled = downsample(points)
            if (sampled.size < 2) return null

            val lat0 = sampled.sumOf { it.latitude } / sampled.size
            val lon0 = sampled.sumOf { it.longitude } / sampled.size
            val cosLat = cos(Math.toRadians(lat0))

            // East/north metres from the centroid, and the raw altitude (or 0).
            val east = DoubleArray(sampled.size)
            val north = DoubleArray(sampled.size)
            val up = DoubleArray(sampled.size)
            sampled.forEachIndexed { i, p ->
                east[i] = (p.longitude - lon0) * cosLat * METERS_PER_DEG_LAT
                north[i] = (p.latitude - lat0) * METERS_PER_DEG_LAT
                up[i] = p.altitudeM ?: 0.0
            }

            val halfExtent =
                max(
                    max(abs(east.min()), abs(east.max())),
                    max(abs(north.min()), abs(north.max())),
                ).takeIf { it > 0.0 } ?: 1.0
            val horizontalScale = 1.0 / halfExtent

            val altMin = up.min()
            val altSpan = up.max() - altMin
            val hasAltitude = sampled.any { it.altitudeM != null } && altSpan > 0.0

            val speeds = effectiveSpeeds(sampled)
            val (speedLow, speedHigh) = speedRange(speeds)
            val speedDenom = (speedHigh - speedLow).takeIf { it > 1e-3f } ?: 1f

            // Cumulative ground distance for the draw-on fraction + total.
            val cumulative = FloatArray(sampled.size)
            var totalMeters = 0.0
            for (i in 1 until sampled.size) {
                totalMeters += hypot(east[i] - east[i - 1], north[i] - north[i - 1])
                cumulative[i] = totalMeters.toFloat()
            }
            val distanceDenom = totalMeters.toFloat().takeIf { it > 1e-3f } ?: 1f

            val vertices = FloatArray(sampled.size * FLOATS_PER_VERTEX)
            sampled.indices.forEach { i ->
                val base = i * FLOATS_PER_VERTEX
                vertices[base] = (east[i] * horizontalScale).toFloat()
                vertices[base + 1] =
                    if (hasAltitude) (((up[i] - altMin) / altSpan).toFloat() * ALT_TARGET_VFRAC) else 0f
                vertices[base + 2] = (north[i] * horizontalScale).toFloat()
                val t = ((speeds[i] - speedLow) / speedDenom).coerceIn(0f, 1f)
                val color = turbo(t)
                vertices[base + 3] = color[0]
                vertices[base + 4] = color[1]
                vertices[base + 5] = color[2]
                vertices[base + 6] = cumulative[i] / distanceDenom
            }

            val stats =
                TripStats(
                    startMs = sampled.first().timeMs,
                    endMs = sampled.last().timeMs,
                    distanceMeters = totalMeters,
                    maxSpeedMps = speeds.maxOrNull() ?: 0f,
                    avgSpeedMps = if (speeds.isNotEmpty()) speeds.average().toFloat() else 0f,
                    hasAltitude = hasAltitude,
                    altitudeGainMeters = if (hasAltitude) altSpan else 0.0,
                )
            return TripGeometry(vertices, sampled.size, segmentsOf(sampled), stats)
        }

        // Even-stride downsample to MAX_RENDER_POINTS, always keeping the last
        // point so the trip's end is never dropped.
        private fun downsample(points: List<TrackPointEntity>): List<TrackPointEntity> {
            if (points.size <= MAX_RENDER_POINTS) return points
            val stride = (points.size + MAX_RENDER_POINTS - 1) / MAX_RENDER_POINTS
            return points.filterIndexed { i, _ -> i % stride == 0 || i == points.lastIndex }
        }

        // Split into continuous strips wherever the wall-clock gap exceeds the
        // trip math's gap window (the same threshold GpxWriter splits <trkseg> on).
        private fun segmentsOf(points: List<TrackPointEntity>): List<IntRange> {
            val gapMs = (MAX_GAP_SECONDS * 1_000).toLong()
            val ranges = mutableListOf<IntRange>()
            var start = 0
            for (i in 1 until points.size) {
                if (points[i].timeMs - points[i - 1].timeMs > gapMs) {
                    if (i - 1 > start) ranges += start..(i - 1)
                    start = i
                }
            }
            if (points.lastIndex > start) ranges += start..points.lastIndex
            return ranges
        }

        // Reported speed where present, else derived from the position/time delta,
        // so a speed-less chip still gets a plausible gradient.
        private fun effectiveSpeeds(points: List<TrackPointEntity>): FloatArray {
            val cosLat = cos(Math.toRadians(points.first().latitude))
            return FloatArray(points.size) { i ->
                points[i].speedMps ?: run {
                    if (i == 0) return@run 0f
                    val prev = points[i - 1]
                    val cur = points[i]
                    val dtSec = (cur.timeMs - prev.timeMs) / 1000.0
                    if (dtSec <= 0.0) return@run 0f
                    val dEast = (cur.longitude - prev.longitude) * cosLat * METERS_PER_DEG_LAT
                    val dNorth = (cur.latitude - prev.latitude) * METERS_PER_DEG_LAT
                    (hypot(dEast, dNorth) / dtSec).toFloat()
                }
            }
        }

        // Robust colour range: clip to the 5th/95th percentiles so one GPS speed
        // spike can't wash the whole gradient toward the low end.
        private fun speedRange(speeds: FloatArray): Pair<Float, Float> {
            if (speeds.isEmpty()) return 0f to 1f
            val sorted = speeds.sortedArray()
            val low = sorted[(sorted.size * 0.05f).roundToInt().coerceIn(0, sorted.lastIndex)]
            val high = sorted[(sorted.size * 0.95f).roundToInt().coerceIn(0, sorted.lastIndex)]
            return low to max(high, low + 1e-3f)
        }
    }
}

/** Headline metrics for the trip HUD, derived while building the geometry. */
internal data class TripStats(
    val startMs: Long,
    val endMs: Long,
    val distanceMeters: Double,
    val maxSpeedMps: Float,
    val avgSpeedMps: Float,
    val hasAltitude: Boolean,
    val altitudeGainMeters: Double,
)

// Google "Turbo" colormap (Mikhailov, 2019) as a 17-stop LUT with linear
// interpolation — perceptually near-uniform and, unlike viridis/inferno, bright
// and saturated across its whole range so low speeds stay visible on the dark
// scene. t is clamped to [0, 1].
private val TurboStops =
    floatArrayOf(
        0.190f,
        0.072f,
        0.232f,
        0.246f,
        0.242f,
        0.653f,
        0.269f,
        0.404f,
        0.884f,
        0.259f,
        0.556f,
        0.988f,
        0.180f,
        0.702f,
        0.966f,
        0.118f,
        0.827f,
        0.850f,
        0.155f,
        0.911f,
        0.699f,
        0.331f,
        0.960f,
        0.502f,
        0.532f,
        0.982f,
        0.318f,
        0.703f,
        0.976f,
        0.204f,
        0.842f,
        0.925f,
        0.176f,
        0.944f,
        0.842f,
        0.185f,
        0.995f,
        0.720f,
        0.156f,
        0.982f,
        0.563f,
        0.109f,
        0.925f,
        0.393f,
        0.060f,
        0.826f,
        0.240f,
        0.025f,
        0.698f,
        0.132f,
        0.020f,
    )

internal fun turbo(t: Float): FloatArray {
    val clamped = t.coerceIn(0f, 1f)
    val stops = TurboStops.size / 3
    val pos = clamped * (stops - 1)
    val i = pos.toInt().coerceIn(0, stops - 2)
    val f = pos - i
    val a = i * 3
    val b = (i + 1) * 3
    return floatArrayOf(
        TurboStops[a] + (TurboStops[b] - TurboStops[a]) * f,
        TurboStops[a + 1] + (TurboStops[b + 1] - TurboStops[a + 1]) * f,
        TurboStops[a + 2] + (TurboStops[b + 2] - TurboStops[a + 2]) * f,
    )
}
