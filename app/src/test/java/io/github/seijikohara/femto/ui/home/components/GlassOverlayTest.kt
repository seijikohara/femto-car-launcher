package io.github.seijikohara.femto.ui.home.components

import org.junit.Test
import kotlin.test.assertEquals

class GlassOverlayTest {
    @Test
    fun `glassTintAlpha maps opacity percent to alpha and clamps`() {
        // The opacity percent maps straight to a [0, 1] alpha: 100% is fully
        // opaque, 0% is clear, and an over-range value clamps rather than overshoot.
        assertEquals(1f, glassTintAlpha(100), 0.0001f)
        assertEquals(0.5f, glassTintAlpha(50), 0.0001f)
        assertEquals(0f, glassTintAlpha(0), 0.0001f)
        assertEquals(1f, glassTintAlpha(150), 0.0001f)
    }
}
