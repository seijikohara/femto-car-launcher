package io.github.seijikohara.femto.ui.home.components

import org.junit.Test
import kotlin.test.assertEquals

class PlaybackSeekBarTest {
    @Test
    fun `seekTargetMs maps a fraction into the duration`() {
        assertEquals(30_000L, seekTargetMs(0.5f, 60_000L))
    }

    @Test
    fun `seekTargetMs clamps fractions outside the track`() {
        assertEquals(0L, seekTargetMs(-0.2f, 60_000L))
        assertEquals(60_000L, seekTargetMs(1.4f, 60_000L))
    }

    @Test
    fun `seekTargetMs is zero for a zero or negative duration`() {
        assertEquals(0L, seekTargetMs(0.5f, 0L))
        assertEquals(0L, seekTargetMs(0.5f, -10L))
    }
}
