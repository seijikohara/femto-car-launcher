package io.github.seijikohara.femto.data.location

import kotlin.math.abs

/**
 * Theme-derived colours the trip flyover renders with, shared by the native
 * Vulkan renderer and the 2D Compose fallback so both paths read identically.
 *
 * The wireframe glow is *additive* on a dark scene but *alpha-over* on a light
 * one — additive blending onto a light backdrop washes to white — so [isDark]
 * selects the blend model in each renderer while the colours below adapt to the
 * active Material scheme:
 *  - [background] is the scene clear / backdrop (near-black on dark, near-white
 *    on light).
 *  - [grid] tints the ground grid + chrome toward the scheme accent.
 *  - [head] is the comet-head mix target (white on dark, ink on light).
 *
 * On the light scene the per-vertex speed colours are additionally passed
 * through [lightSceneLineTone] when the wireframe is baked (TripWireframe), so
 * the turbo gradient reads as saturated jewel tones on the light backdrop
 * instead of sinking to near-black.
 *
 * Colours are plain RGB float triples in `[0, 1]` so this type stays free of any
 * UI / Compose dependency (`data/` never imports `ui/`); the UI builds it from
 * the Material scheme and hands it down.
 */
internal class TripScenePalette(
    val isDark: Boolean,
    val background: FloatArray,
    val grid: FloatArray,
    val head: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is TripScenePalette &&
                    isDark == other.isDark &&
                    background.contentEquals(other.background) &&
                    grid.contentEquals(other.grid) &&
                    head.contentEquals(other.head)
            )

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        result = 31 * result + background.contentHashCode()
        result = 31 * result + grid.contentHashCode()
        result = 31 * result + head.contentHashCode()
        return result
    }

    companion object {
        /**
         * Dark-scene default used where no Material scheme is in scope (unit
         * tests, previews). The UI builder replaces [grid] with an accent-derived
         * tint; this keeps the legacy teal floor as a neutral stand-in.
         * [background] mirrors the Kotlin `TripSceneBackground` (0xFF050810), the
         * single backdrop SSOT — as the exact 8-bit fractions, not rounded, and
         * pinned to it by `FlyoverSceneTest`. Note [background] is unused at
         * runtime by the wireframe build (it reads only [grid] and, on light,
         * recedes the curtain toward it); the live scene clear comes from the UI
         * palette via `nativeSetTheme`.
         */
        val Dark =
            TripScenePalette(
                isDark = true,
                background = floatArrayOf(0x05 / 255f, 0x08 / 255f, 0x10 / 255f),
                grid = floatArrayOf(0.10f, 0.36f, 0.46f),
                head = floatArrayOf(1f, 1f, 1f),
            )
    }
}

// The light scene's tone band: hue is preserved, saturation gets a floor and the
// HSV value is clamped into a mid band, so every speed colour reads as a
// saturated jewel tone on the near-white backdrop — the turbo gradient's dark
// navy low end would otherwise render as near-black line work (the "wires all
// black" report), and its bright yellows would wash out.
private const val LIGHT_TONE_SATURATION_FLOOR = 0.65f
private const val LIGHT_TONE_VALUE_MIN = 0.50f
private const val LIGHT_TONE_VALUE_MAX = 0.75f

// A colour this close to grey has no meaningful hue; forcing the saturation
// floor onto it would invent a red cast (hue defaults to 0), so greys keep
// their greyness and only the value band applies.
private const val LIGHT_TONE_GREY_EPSILON = 0.05f

/**
 * Tone one scene colour for the light backdrop: keep the hue, floor the
 * saturation, and clamp the HSV value into the light band (see the constants
 * above). Pure float math so both the wireframe bake (`data/`) and the UI grid
 * tint share it.
 */
internal fun lightSceneLineTone(
    r: Float,
    g: Float,
    b: Float,
): FloatArray {
    val value = maxOf(r, g, b)
    val minChannel = minOf(r, g, b)
    val chroma = value - minChannel
    val saturation = if (value <= 1e-6f) 0f else chroma / value
    val v = value.coerceIn(LIGHT_TONE_VALUE_MIN, LIGHT_TONE_VALUE_MAX)
    if (saturation < LIGHT_TONE_GREY_EPSILON) return floatArrayOf(v, v, v)

    // Hue in [0, 6) from the dominant channel, then back to RGB at the toned
    // saturation/value (the standard HSV round trip, kept dependency-free).
    val hue =
        when (value) {
            r -> ((g - b) / chroma).mod(6f)
            g -> (b - r) / chroma + 2f
            else -> (r - g) / chroma + 4f
        }
    val s = maxOf(saturation, LIGHT_TONE_SATURATION_FLOOR)
    val c = v * s
    val x = c * (1f - abs(hue.mod(2f) - 1f))
    val m = v - c
    return when (hue.toInt()) {
        0 -> floatArrayOf(c + m, x + m, m)
        1 -> floatArrayOf(x + m, c + m, m)
        2 -> floatArrayOf(m, c + m, x + m)
        3 -> floatArrayOf(m, x + m, c + m)
        4 -> floatArrayOf(x + m, m, c + m)
        else -> floatArrayOf(c + m, m, x + m)
    }
}
