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

    @Test
    fun `glassShadowAlpha maps intensity percent to alpha and clamps`() {
        // The intensity percent maps straight to a [0, 1] alpha: 0% is invisible,
        // 100% is full strength, and out-of-range values clamp rather than overshoot.
        assertEquals(0f, glassShadowAlpha(0), 0.0001f)
        assertEquals(0.4f, glassShadowAlpha(40), 0.0001f)
        assertEquals(1f, glassShadowAlpha(100), 0.0001f)
        assertEquals(1f, glassShadowAlpha(150), 0.0001f)
        assertEquals(0f, glassShadowAlpha(-20), 0.0001f)
    }
}
