package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.seijikohara.femto.data.weather.HourlyForecast
import io.github.seijikohara.femto.data.weather.WeatherCode
import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherTempCurveTest {
    @Test
    fun `geometry normalizes x to the unit range and heat to the temp extremes`() {
        val geometry =
            assertNotNull(
                tempCurveGeometry(
                    hours(temps = listOf(10.0, 12.0, 20.0, 14.0)),
                ),
            )
        assertEquals(0f, geometry.xs.first())
        assertEquals(1f, geometry.xs.last())
        // Span (10) exceeds the minimum pad, so the extremes hit 0 and 1 exactly.
        assertEquals(0f, geometry.heat[0], 1e-6f)
        assertEquals(1f, geometry.heat[2], 1e-6f)
        assertEquals(3f, geometry.spanHours)
    }

    @Test
    fun `a flat day is padded to the minimum span instead of a full-height wall`() {
        val geometry = assertNotNull(tempCurveGeometry(hours(temps = listOf(18.0, 18.0, 18.0))))
        // All points sit mid-band: the 0-degree span pads to MIN_TEMP_SPAN_C
        // centred on the data, so a flat line renders midway, not clamped.
        geometry.heat.forEach { assertEquals(0.5f, it, 1e-6f) }
    }

    @Test
    fun `geometry requires at least two points`() {
        assertNull(tempCurveGeometry(hours(temps = listOf(18.0))))
        assertNull(tempCurveGeometry(emptyList()))
    }

    @Test
    fun `temp colour clamps outside the ramp and lerps between stops`() {
        val stops = listOf(0f to Color.Blue, 10f to Color.Green, 20f to Color.Red)
        assertEquals(Color.Blue, tempColorAt(stops, -5f))
        assertEquals(Color.Red, tempColorAt(stops, 99f))
        assertEquals(lerp(Color.Green, Color.Red, 0.5f), tempColorAt(stops, 15f))
    }

    @Test
    fun `sun event fraction handles same-day, overnight wrap, and out-of-window`() {
        // 12:00 window start, 23 h span: 18:00 is 6 h in.
        assertEquals(6f / 23f, assertNotNull(sunEventFraction(LocalTime.of(12, 0), LocalTime.of(18, 0), 23f)), 1e-6f)
        // Overnight wrap: 05:00 next morning is 17 h after a 12:00 start.
        assertEquals(17f / 23f, assertNotNull(sunEventFraction(LocalTime.of(12, 0), LocalTime.of(5, 0), 23f)), 1e-6f)
        // Beyond a short window: 12:00 is 12 h after midnight, span only 6 h.
        assertNull(sunEventFraction(LocalTime.of(0, 0), LocalTime.of(12, 0), 6f))
    }

    @Test
    fun `heat stays within the unit range for extreme swings`() {
        val geometry = assertNotNull(tempCurveGeometry(hours(temps = listOf(-25.0, 5.0, 41.0))))
        geometry.heat.forEach { assertTrue(it in 0f..1f) }
    }

    private fun hours(temps: List<Double>): List<HourlyForecast> =
        temps.mapIndexed { i, t ->
            HourlyForecast(LocalTime.of(i, 0), t, WeatherCode.CLEAR)
        }
}
