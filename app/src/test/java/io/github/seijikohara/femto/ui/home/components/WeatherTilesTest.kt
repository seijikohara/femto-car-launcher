package io.github.seijikohara.femto.ui.home.components

import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeatherTilesTest {
    @Test
    fun `daylight fraction spans sunrise to sunset and is null outside`() {
        val sunrise = LocalTime.of(6, 0)
        val sunset = LocalTime.of(18, 0)
        assertEquals(0f, assertNotNull(daylightFraction(sunrise, sunset, LocalTime.of(6, 0))), 1e-6f)
        assertEquals(0.5f, assertNotNull(daylightFraction(sunrise, sunset, LocalTime.of(12, 0))), 1e-6f)
        assertEquals(1f, assertNotNull(daylightFraction(sunrise, sunset, LocalTime.of(18, 0))), 1e-6f)
        assertNull(daylightFraction(sunrise, sunset, LocalTime.of(22, 0)))
        // Degenerate polar input never divides by zero.
        assertNull(daylightFraction(sunrise, sunrise, LocalTime.of(12, 0)))
    }

    @Test
    fun `uv bands follow the WHO scale`() {
        assertEquals(0, uvBandIndex(0.0))
        assertEquals(0, uvBandIndex(2.9))
        assertEquals(1, uvBandIndex(3.0))
        assertEquals(2, uvBandIndex(6.0))
        assertEquals(3, uvBandIndex(8.0))
        assertEquals(4, uvBandIndex(11.0))
    }
}
