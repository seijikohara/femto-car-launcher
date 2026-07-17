package io.github.seijikohara.femto.data.location

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
 *  - [lineScale] scales the per-vertex speed colours: `1.0` on dark (the turbo
 *    stops are already tuned bright-on-dark), darkened on light so the line
 *    reads against the light backdrop under alpha-over blending.
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
    val lineScale: Float,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is TripScenePalette &&
                    isDark == other.isDark &&
                    lineScale == other.lineScale &&
                    background.contentEquals(other.background) &&
                    grid.contentEquals(other.grid) &&
                    head.contentEquals(other.head)
            )

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        result = 31 * result + lineScale.hashCode()
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
         * runtime (the wireframe build reads only [grid] and [lineScale]); the
         * live scene clear comes from the UI palette via `nativeSetTheme`.
         */
        val Dark =
            TripScenePalette(
                isDark = true,
                background = floatArrayOf(0x05 / 255f, 0x08 / 255f, 0x10 / 255f),
                grid = floatArrayOf(0.10f, 0.36f, 0.46f),
                head = floatArrayOf(1f, 1f, 1f),
                lineScale = 1f,
            )
    }
}
