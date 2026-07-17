package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.location.TripWireframe
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// Camera + pacing constants kept in step with the native renderer so the two
// paths read the same. See flyover_renderer.cpp.
private const val ORBIT_RATE = 0.16f
private const val ELEVATION_RAD = 0.58f
private const val FOV_RAD = 0.785f // 45 deg
private const val FLOATS = TripWireframe.FLOATS_PER_VERTEX

// Bound the fallback's per-frame line count for weak (Vulkan-less) devices by
// striding the track segments; the grid and curtain are already sparse.
private const val MAX_FALLBACK_SEGMENTS = 1_400

/**
 * Software wireframe flyover for devices where the native Vulkan renderer is
 * unavailable. It projects the same [TripWireframe] line list through the same
 * orbiting camera and strokes each segment with additive blend plus a blurred
 * backing copy for the neon glow, so the fallback still reads as the same
 * mesmerizing object — just lighter. The camera orbit is self-driven; the
 * draw-on [progress] is supplied by the panel's frame clock (as with the native
 * path).
 */
@Composable
internal fun TripFlyoverFallback(
    wireframe: FloatArray,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedFrameClock(Unit) { dtSeconds -> elapsed += dtSeconds }

    val segments = remember(wireframe) { strideSegments(wireframe) }

    Box(modifier = modifier) {
        // Blurred backing copy → cheap bloom for the neon look.
        WireframeCanvas(
            segments = segments,
            elapsed = elapsed,
            progress = progress,
            glow = true,
            modifier = Modifier.fillMaxSize().blur(7.dp),
        )
        WireframeCanvas(
            segments = segments,
            elapsed = elapsed,
            progress = progress,
            glow = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun WireframeCanvas(
    segments: FloatArray,
    elapsed: Float,
    progress: Float,
    glow: Boolean,
    modifier: Modifier = Modifier,
) = Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f || segments.isEmpty()) return@Canvas

    // Intro dolly then a steady orbit — the same easing the native path uses.
    val intro = (elapsed / 3f).coerceAtMost(1f)
    val ease = intro * intro * (3f - 2f * intro)
    val radius = 3.5f - 0.9f * ease
    val az = elapsed * ORBIT_RATE
    val cx = radius * cos(ELEVATION_RAD) * sin(az)
    val cy = 0.12f + radius * sin(ELEVATION_RAD)
    val cz = radius * cos(ELEVATION_RAD) * cos(az)
    val camera = Camera(eyeX = cx, eyeY = cy, eyeZ = cz, aspect = w / h)
    val strokePx = if (glow) 4.5f else 1.6f
    val alpha = if (glow) 0.5f else 1f

    // Each stride entry is two vertices (14 floats): x,y,z,r,g,b,dist per end.
    var i = 0
    while (i + 2 * FLOATS <= segments.size) {
        val da = segments[i + 6]
        val db = segments[i + FLOATS + 6]
        val isChrome = da < 0f && db < 0f
        // Draw-on: hide a segment fully ahead of the playhead (chrome is always on).
        if (!isChrome && minOf(da, db) > progress) {
            i += 2 * FLOATS
            continue
        }
        val a = camera.project(segments[i], segments[i + 1], segments[i + 2], w, h)
        val b = camera.project(segments[i + FLOATS], segments[i + FLOATS + 1], segments[i + FLOATS + 2], w, h)
        if (a != null && b != null) {
            // Comet-head brighten just behind the playhead.
            val head = if (isChrome) 0f else smoothstep(progress - 0.05f, progress, maxOf(da, db))
            val fog = ((a.depth + b.depth) * 0.5f).let { d -> (1f - (d - 2f) / 5f).coerceIn(0.12f, 1f) }
            val r = lerp(segments[i + 3], segments[i + FLOATS + 3], 0.5f)
            val g = lerp(segments[i + 4], segments[i + FLOATS + 4], 0.5f)
            val bl = lerp(segments[i + 5], segments[i + FLOATS + 5], 0.5f)
            val hb = head * 0.9f
            drawLine(
                color =
                    Color(
                        red = ((r + (1f - r) * hb) * fog).coerceIn(0f, 1f),
                        green = ((g + (1f - g) * hb) * fog).coerceIn(0f, 1f),
                        blue = ((bl + (1f - bl) * hb) * fog).coerceIn(0f, 1f),
                        alpha = alpha,
                    ),
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Plus,
            )
        }
        i += 2 * FLOATS
    }
}

/** Runs [onFrame] with per-frame delta seconds while composed. */
@Composable
private fun LaunchedFrameClock(
    key: Any,
    onFrame: (Float) -> Unit,
) {
    val current = rememberUpdatedState(onFrame)
    LaunchedEffect(key) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            current.value((now - last).coerceAtLeast(0L) / 1_000_000_000f)
            last = now
        }
    }
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
        val cX = 0f
        val cY = 0.12f
        val cZ = 0f
        var fx = cX - eyeX
        var fy = cY - eyeY
        var fz = cZ - eyeZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-6f)
        fx /= fl
        fy /= fl
        fz /= fl
        // side = normalize(cross(f, up))
        var sx = fy * 0f - fz * 1f
        var sy = fz * 0f - fx * 0f
        var sz = fx * 1f - fy * 0f
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
                sx,
                ux,
                -fx,
                sy,
                uy,
                -fy,
                sz,
                uz,
                -fz,
                -(sx * eyeX + sy * eyeY + sz * eyeZ),
                -(ux * eyeX + uy * eyeY + uz * eyeZ),
                (fx * eyeX + fy * eyeY + fz * eyeZ),
            )
    }

    fun project(
        x: Float,
        y: Float,
        z: Float,
        width: Float,
        height: Float,
    ): Projected? {
        // View-space coords.
        val vx = view[0] * x + view[3] * y + view[6] * z + view[9]
        val vy = view[1] * x + view[4] * y + view[7] * z + view[10]
        val vz = view[2] * x + view[5] * y + view[8] * z + view[11]
        val w = -vz // perspective w
        if (w <= 0.02f) return null
        // Perspective (Vulkan-style -f handles Y; Compose Y is also down).
        val ndcX = (f / aspect) * vx / w
        val ndcY = (-f) * vy / w
        return Projected(
            x = (ndcX * 0.5f + 0.5f) * width,
            y = (ndcY * 0.5f + 0.5f) * height,
            depth = w,
        )
    }
}

private class Projected(
    val x: Float,
    val y: Float,
    val depth: Float,
)

private fun strideSegments(wireframe: FloatArray): FloatArray {
    val segCount = wireframe.size / (2 * FLOATS)
    if (segCount <= MAX_FALLBACK_SEGMENTS) return wireframe
    val stride = (segCount + MAX_FALLBACK_SEGMENTS - 1) / MAX_FALLBACK_SEGMENTS
    val out = ArrayList<Float>(MAX_FALLBACK_SEGMENTS * 2 * FLOATS)
    var s = 0
    while (s < segCount) {
        val base = s * 2 * FLOATS
        for (k in 0 until 2 * FLOATS) out.add(wireframe[base + k])
        s += stride
    }
    return out.toFloatArray()
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
