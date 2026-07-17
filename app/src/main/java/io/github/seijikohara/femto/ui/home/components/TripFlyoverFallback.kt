package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import io.github.seijikohara.femto.data.location.TrackPointEntity
import io.github.seijikohara.femto.data.location.TripGeometry
import io.github.seijikohara.femto.data.location.TripWireframe
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TripSceneBackground
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// Camera constants kept in step with the native renderer so the two paths read
// the same. See flyover_renderer.cpp (kOrbitRate / kElevationRad).
private const val ORBIT_RATE = 0.16f
private const val ELEVATION_RAD = 0.58f
private const val FOV_RAD = 0.785f // 45 deg
private const val FLOATS = TripWireframe.FLOATS_PER_VERTEX

/**
 * Software wireframe flyover for devices where the native Vulkan renderer is
 * unavailable. It projects the same [TripWireframe] line list through the same
 * orbiting camera and strokes each segment twice — a wide dim pass plus a thin
 * bright core, both additive — for the neon glow, so the fallback still reads as
 * the same mesmerizing object, just lighter. The camera orbit is self-driven;
 * the draw-on [progress] is supplied by the panel's frame clock (as with the
 * native path). The vertex count is already bounded by TripGeometry's
 * downsample, so the whole line list is drawn without decimation (decimating the
 * flat GL_LINES list would sever the polyline into dashes).
 */
@Composable
internal fun TripFlyoverFallback(
    wireframe: FloatArray,
    progress: FloatState,
    modifier: Modifier = Modifier,
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedFrameClock { dtSeconds -> elapsed += dtSeconds }

    Box(modifier = modifier) {
        WireframeCanvas(
            segments = wireframe,
            elapsed = elapsed,
            progress = progress,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun WireframeCanvas(
    segments: FloatArray,
    elapsed: Float,
    progress: FloatState,
    modifier: Modifier = Modifier,
) {
    // Reused across the whole draw so the per-frame line loop allocates nothing.
    val a = remember { Projected() }
    val b = remember { Projected() }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || segments.isEmpty()) return@Canvas
        val phase = progress.floatValue

        // Intro dolly then a steady orbit — the same easing the native path uses.
        val intro = (elapsed / 3f).coerceAtMost(1f)
        val ease = intro * intro * (3f - 2f * intro)
        val radius = 3.5f - 0.9f * ease
        val az = elapsed * ORBIT_RATE
        val camera =
            Camera(
                eyeX = radius * cos(ELEVATION_RAD) * sin(az),
                eyeY = 0.12f + radius * sin(ELEVATION_RAD),
                eyeZ = radius * cos(ELEVATION_RAD) * cos(az),
                aspect = w / h,
            )

        var i = 0
        while (i + 2 * FLOATS <= segments.size) {
            val da = segments[i + 6]
            val db = segments[i + FLOATS + 6]
            val isChrome = da < 0f && db < 0f
            // Draw-on: hide a segment fully ahead of the playhead (chrome is always on).
            if ((isChrome || minOf(da, db) <= phase) &&
                camera.project(segments[i], segments[i + 1], segments[i + 2], w, h, a) &&
                camera.project(segments[i + FLOATS], segments[i + FLOATS + 1], segments[i + FLOATS + 2], w, h, b)
            ) {
                val head = if (isChrome) 0f else smoothstep(phase - 0.05f, phase, maxOf(da, db))
                val fog = ((a.depth + b.depth) * 0.5f).let { d -> (1f - (d - 2f) / 5f).coerceIn(0.12f, 1f) }
                val hb = head * 0.9f
                val r = lerp(segments[i + 3], segments[i + FLOATS + 3], 0.5f)
                val g = lerp(segments[i + 4], segments[i + FLOATS + 4], 0.5f)
                val bl = lerp(segments[i + 5], segments[i + FLOATS + 5], 0.5f)
                val start = Offset(a.x, a.y)
                val end = Offset(b.x, b.y)
                // Wide dim glow pass, then the thin bright core; both additive so
                // overlaps bloom. Butt cap on the core so shared joints don't
                // double-brighten into hot dots.
                val glow =
                    Color(
                        (r * fog).coerceIn(0f, 1f),
                        (g * fog).coerceIn(0f, 1f),
                        (bl * fog).coerceIn(0f, 1f),
                        alpha = 0.28f,
                    )
                drawLine(glow, start, end, strokeWidth = 6f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
                val core =
                    Color(
                        ((r + (1f - r) * hb) * fog).coerceIn(0f, 1f),
                        ((g + (1f - g) * hb) * fog).coerceIn(0f, 1f),
                        ((bl + (1f - bl) * hb) * fog).coerceIn(0f, 1f),
                        alpha = 1f,
                    )
                drawLine(core, start, end, strokeWidth = 1.6f, cap = StrokeCap.Butt, blendMode = BlendMode.Plus)
            }
            i += 2 * FLOATS
        }
    }
}

/** Runs [onFrame] with per-frame delta seconds while composed. */
@Composable
private fun LaunchedFrameClock(onFrame: (Float) -> Unit) {
    val current = rememberUpdatedState(onFrame)
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            current.value((now - last).coerceAtLeast(0L) / 1_000_000_000f)
            last = now
        }
    }
}

// Mutable projection result, reused to keep the draw loop allocation-free.
private class Projected {
    var x = 0f
    var y = 0f
    var depth = 0f
}

private class Camera(
    eyeX: Float,
    eyeY: Float,
    eyeZ: Float,
    private val aspect: Float,
) {
    // view (lookAt eye -> center (0,0.12,0), up +Y) then perspective, flattened.
    private val f = 1f / tan(FOV_RAD * 0.5f)
    private val view: FloatArray

    init {
        val cY = 0.12f
        var fx = -eyeX
        var fy = cY - eyeY
        var fz = -eyeZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-6f)
        fx /= fl
        fy /= fl
        fz /= fl
        // side = normalize(cross(f, up)) with up = (0,1,0)
        var sx = -fz
        var sy = 0f
        var sz = fx
        val sl = sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(1e-6f)
        sx /= sl
        sy /= sl
        sz /= sl
        // up = cross(side, f)
        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx
        view =
            floatArrayOf(
                sx, ux, -fx,
                sy, uy, -fy,
                sz, uz, -fz,
                -(sx * eyeX + sy * eyeY + sz * eyeZ),
                -(ux * eyeX + uy * eyeY + uz * eyeZ),
                (fx * eyeX + fy * eyeY + fz * eyeZ),
            )
    }

    /** Project into [out]; returns false when behind the camera. */
    fun project(
        x: Float,
        y: Float,
        z: Float,
        width: Float,
        height: Float,
        out: Projected,
    ): Boolean {
        val vx = view[0] * x + view[3] * y + view[6] * z + view[9]
        val vy = view[1] * x + view[4] * y + view[7] * z + view[10]
        val vz = view[2] * x + view[5] * y + view[8] * z + view[11]
        val depth = -vz // perspective forward distance
        if (depth <= 0.02f) return false
        // Vulkan-style -f on Y matches Compose's Y-down screen space.
        out.x = ((f / aspect) * vx / depth * 0.5f + 0.5f) * width
        out.y = ((-f) * vy / depth * 0.5f + 0.5f) * height
        out.depth = depth
        return true
    }
}

private fun smoothstep(
    edge0: Float,
    edge1: Float,
    x: Float,
): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerp(
    a: Float,
    b: Float,
    t: Float,
): Float = a + (b - a) * t

@PreviewLightDark
@Composable
private fun TripFlyoverFallbackPreview() {
    // A synthetic climbing S-curve so the preview shows the wireframe, curtain,
    // grid, and speed gradient statically (the orbit/draw-on animate at runtime).
    val geometry =
        TripGeometry.from(
            (0 until 60).map { i ->
                TrackPointEntity(
                    tripId = 0L,
                    timeMs = i * 1_000L,
                    latitude = 35.6580 + i * 0.0004,
                    longitude = 139.7016 + 0.0008 * sin(i * 0.25f),
                    speedMps = 6f + 10f * (0.5f + 0.5f * sin(i * 0.4f)),
                    bearingDeg = null,
                    altitudeM = 20.0 + i * 2.5,
                    accuracyM = 5f,
                )
            },
        )
    FemtoTheme {
        Box(Modifier.fillMaxSize().background(TripSceneBackground)) {
            geometry?.let {
                TripFlyoverFallback(
                    wireframe = TripWireframe.build(it),
                    progress = remember { mutableFloatStateOf(0.7f) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
