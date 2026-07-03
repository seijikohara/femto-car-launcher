package io.github.seijikohara.femto.ui.home

import io.github.seijikohara.femto.data.display.PresetMode
import org.junit.Test
import kotlin.test.assertEquals

class PresetResolverTest {
    private fun resolve(
        mode: PresetMode = PresetMode.AUTO,
        speedKmh: Double = 0.0,
        threshold: Int = 8,
        previous: PresetId = PresetId.COCKPIT,
        passenger: Boolean = false,
    ) = resolvePreset(mode, speedKmh, threshold, previous, passenger)

    @Test fun `passenger unlock forces cockpit even while driving-fast`() =
        assertEquals(PresetId.COCKPIT, resolve(speedKmh = 120.0, previous = PresetId.DRIVING, passenger = true))

    @Test fun `manual cockpit and driving win over speed`() {
        assertEquals(PresetId.COCKPIT, resolve(mode = PresetMode.COCKPIT, speedKmh = 120.0))
        assertEquals(PresetId.DRIVING, resolve(mode = PresetMode.DRIVING, speedKmh = 0.0))
    }

    @Test fun `auto enters driving only above threshold plus band`() {
        assertEquals(PresetId.DRIVING, resolve(speedKmh = 11.0, previous = PresetId.COCKPIT)) // 8+3
        assertEquals(PresetId.COCKPIT, resolve(speedKmh = 10.9, previous = PresetId.COCKPIT))
    }

    @Test fun `auto returns to cockpit only below threshold minus band`() {
        assertEquals(PresetId.COCKPIT, resolve(speedKmh = 5.0, previous = PresetId.DRIVING)) // 8-3
        assertEquals(PresetId.DRIVING, resolve(speedKmh = 5.1, previous = PresetId.DRIVING))
    }

    @Test fun `inside the band the previous preset holds (no flap)`() {
        assertEquals(PresetId.COCKPIT, resolve(speedKmh = 8.0, previous = PresetId.COCKPIT))
        assertEquals(PresetId.DRIVING, resolve(speedKmh = 8.0, previous = PresetId.DRIVING))
    }
}
