package io.github.seijikohara.femto.data.location

/**
 * Expands a [TripGeometry] into a flat GL_LINES vertex list — track polyline +
 * elevation curtain + ground grid — shared byte-for-byte by both renderers: the
 * native Vulkan flyover uploads it as a vertex buffer, and the 2D Compose
 * fallback projects and strokes the same segments. Building it here (not in
 * either renderer) keeps the native side a dumb uploader and the aesthetic
 * unit-testable.
 *
 * Each vertex is [FLOATS_PER_VERTEX] floats: `x, y, z, r, g, b, distanceFraction`.
 * distanceFraction gates the draw-on reveal per vertex; chrome that should
 * always be visible (the ground grid) carries [ALWAYS_ON] (-1), which is below
 * any real fraction so the shader never hides it.
 *
 * Colours are baked here from the active [TripScenePalette] so the native and
 * fallback paths upload the identical buffer: on the dark scene the turbo speed
 * colours pass through verbatim (they are tuned bright-on-dark); on the light
 * scene they go through [lightSceneLineTone] so the gradient reads as saturated
 * jewel tones on the near-white backdrop instead of near-black line work. The
 * curtain recedes toward [TripScenePalette.background] (on dark that backdrop is
 * near-black, so receding is the same dimming as before; on light it fades the
 * ribs into the light scene). The ground grid takes [TripScenePalette.grid]
 * (accent-tinted). The list is theme-derived, so it is rebuilt when the palette
 * changes.
 */
internal object TripWireframe {
    const val FLOATS_PER_VERTEX = TripGeometry.FLOATS_PER_VERTEX
    const val ALWAYS_ON = -1f

    // Grid half-extent (a little past the normalized [-1, 1] track box) and
    // division count — a receding sci-fi floor the track flies over.
    private const val GRID_HALF = 1.15f
    private const val GRID_DIVISIONS = 16

    // The curtain is sampled (not every point) so it reads as ribs, not a solid
    // wall, and receded toward the backdrop so the bright track line stays the
    // hero. The top keeps ~a third of the line colour (the historical 0.32 dim
    // against the near-black scene); the bottom fades almost fully into the
    // ground plane.
    private const val CURTAIN_STRIDE = 3
    private const val CURTAIN_TOP_RECEDE = 0.68f
    private const val CURTAIN_BOTTOM_RECEDE = 0.872f

    /** Build the full line list. Returns an empty array for an empty geometry. */
    fun build(
        geometry: TripGeometry,
        palette: TripScenePalette = TripScenePalette.Dark,
    ): FloatArray {
        val out = ArrayList<Float>(geometry.vertexCount * FLOATS_PER_VERTEX * 3)
        appendTrack(geometry, palette, out)
        appendCurtain(geometry, palette, out)
        appendGrid(palette, out)
        return out.toFloatArray()
    }

    private fun appendTrack(
        geometry: TripGeometry,
        palette: TripScenePalette,
        out: ArrayList<Float>,
    ) {
        val v = geometry.vertices
        // Each continuous segment becomes consecutive line-list pairs.
        geometry.segments.forEach { range ->
            for (i in range.first until range.last) {
                appendTrackVertex(out, v, i, palette)
                appendTrackVertex(out, v, i + 1, palette)
            }
        }
    }

    private fun appendCurtain(
        geometry: TripGeometry,
        palette: TripScenePalette,
        out: ArrayList<Float>,
    ) {
        if (!geometry.stats.hasAltitude) return
        val v = geometry.vertices
        val bg = palette.background
        geometry.segments.forEach { range ->
            var i = range.first
            while (i <= range.last) {
                val base = i * FLOATS_PER_VERTEX
                val x = v[base]
                val y = v[base + 1]
                val z = v[base + 2]
                val dist = v[base + 6]
                val color = sceneLineColor(v, base, palette)
                // Top: the track point's colour receded toward the backdrop.
                out.add(x)
                out.add(y)
                out.add(z)
                out.add(recede(color[0], bg[0], CURTAIN_TOP_RECEDE))
                out.add(recede(color[1], bg[1], CURTAIN_TOP_RECEDE))
                out.add(recede(color[2], bg[2], CURTAIN_TOP_RECEDE))
                out.add(dist)
                // Bottom: straight down to the ground plane, same reveal fraction.
                out.add(x)
                out.add(0f)
                out.add(z)
                out.add(recede(color[0], bg[0], CURTAIN_BOTTOM_RECEDE))
                out.add(recede(color[1], bg[1], CURTAIN_BOTTOM_RECEDE))
                out.add(recede(color[2], bg[2], CURTAIN_BOTTOM_RECEDE))
                out.add(dist)
                i += CURTAIN_STRIDE
            }
        }
    }

    private fun appendGrid(
        palette: TripScenePalette,
        out: ArrayList<Float>,
    ) {
        val step = (GRID_HALF * 2f) / GRID_DIVISIONS
        for (i in 0..GRID_DIVISIONS) {
            val p = -GRID_HALF + i * step
            // Line parallel to X (varying Z held at p), then parallel to Z.
            gridVertex(out, palette.grid, -GRID_HALF, p)
            gridVertex(out, palette.grid, GRID_HALF, p)
            gridVertex(out, palette.grid, p, -GRID_HALF)
            gridVertex(out, palette.grid, p, GRID_HALF)
        }
    }

    private fun gridVertex(
        out: ArrayList<Float>,
        color: FloatArray,
        x: Float,
        z: Float,
    ) {
        out.add(x)
        out.add(0f)
        out.add(z)
        out.add(color[0])
        out.add(color[1])
        out.add(color[2])
        out.add(ALWAYS_ON)
    }

    // Position + distanceFraction verbatim, speed colour toned for the scene.
    private fun appendTrackVertex(
        out: ArrayList<Float>,
        v: FloatArray,
        index: Int,
        palette: TripScenePalette,
    ) {
        val base = index * FLOATS_PER_VERTEX
        val color = sceneLineColor(v, base, palette)
        out.add(v[base])
        out.add(v[base + 1])
        out.add(v[base + 2])
        out.add(color[0])
        out.add(color[1])
        out.add(color[2])
        out.add(v[base + 6])
    }

    private fun sceneLineColor(
        v: FloatArray,
        base: Int,
        palette: TripScenePalette,
    ): FloatArray =
        if (palette.isDark) {
            floatArrayOf(v[base + 3], v[base + 4], v[base + 5])
        } else {
            lightSceneLineTone(v[base + 3], v[base + 4], v[base + 5])
        }

    private fun recede(
        channel: Float,
        background: Float,
        amount: Float,
    ): Float = channel + (background - channel) * amount
}
