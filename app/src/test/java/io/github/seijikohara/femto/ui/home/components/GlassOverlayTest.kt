package io.github.seijikohara.femto.ui.home.components

import org.junit.Test
import kotlin.test.assertEquals

class GlassOverlayTest {
    @Test
    fun `glassTintAlpha scales base alpha by percent and clamps`() {
        // 100% leaves the base alpha unchanged; lower scales fade the tint; the
        // result is clamped into a valid [0, 1] alpha so 300% does not overshoot.
        assertEquals(0.6f, glassTintAlpha(0.6f, 100), 0.0001f)
        assertEquals(0.3f, glassTintAlpha(0.6f, 50), 0.0001f)
        assertEquals(0f, glassTintAlpha(0.6f, 0), 0.0001f)
        assertEquals(1f, glassTintAlpha(0.6f, 300), 0.0001f)
    }
}
