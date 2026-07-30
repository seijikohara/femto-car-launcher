package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.weather.WeatherCode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the condition families apart. Every wet or stormy code used to render one
 * cloud glyph in one tint, so rain, snow and thunder were indistinguishable
 * without reading a label the compact card never shows — these tests exist to
 * keep them from collapsing back together.
 */
class WeatherGlyphTest {
    private val wetOrStormy =
        listOf(
            WeatherCode.DRIZZLE,
            WeatherCode.RAIN,
            WeatherCode.RAIN_SHOWERS,
            WeatherCode.FREEZING_RAIN,
            WeatherCode.SNOW,
            WeatherCode.SNOW_SHOWERS,
            WeatherCode.THUNDERSTORM,
        )

    @Test
    fun `no precipitating condition shares the plain cloud glyph`() {
        val plainCloud = glyphIconFor(WeatherCode.CLOUDY, isDay = true)

        wetOrStormy.forEach { code ->
            assertTrue(glyphIconFor(code, isDay = true) !== plainCloud, "$code still renders the plain cloud")
        }
    }

    @Test
    fun `rain, snow and thunder each render a distinct glyph`() {
        val distinct =
            listOf(WeatherCode.RAIN, WeatherCode.SNOW, WeatherCode.THUNDERSTORM)
                .map { glyphIconFor(it, isDay = true) }

        assertEquals(3, distinct.distinct().size)
    }

    @Test
    fun `showers are separated from steady rain`() {
        assertTrue(
            glyphIconFor(WeatherCode.RAIN_SHOWERS, isDay = true) !==
                glyphIconFor(WeatherCode.RAIN, isDay = true),
        )
    }

    @Test
    fun `drizzle is separated from rain`() {
        assertTrue(
            glyphIconFor(WeatherCode.DRIZZLE, isDay = true) !==
                glyphIconFor(WeatherCode.RAIN, isDay = true),
        )
    }

    @Test
    fun `freezing rain reads as ice, not as rain or snow`() {
        val sleet = glyphIconFor(WeatherCode.FREEZING_RAIN, isDay = true)

        assertTrue(sleet !== glyphIconFor(WeatherCode.RAIN, isDay = true))
        assertTrue(sleet !== glyphIconFor(WeatherCode.SNOW, isDay = true))
    }

    @Test
    fun `fog is separated from plain cloud`() {
        assertTrue(
            glyphIconFor(WeatherCode.FOG, isDay = true) !==
                glyphIconFor(WeatherCode.CLOUDY, isDay = true),
        )
    }

    @Test
    fun `clear sky is the only code that changes with daylight`() {
        assertTrue(glyphIconFor(WeatherCode.CLEAR, isDay = true) !== glyphIconFor(WeatherCode.CLEAR, isDay = false))

        (WeatherCode.entries - WeatherCode.CLEAR).forEach { code ->
            assertTrue(
                glyphIconFor(code, isDay = true) === glyphIconFor(code, isDay = false),
                "$code should not vary with daylight",
            )
        }
    }

    @Test
    fun `every code resolves a label`() {
        // labelResFor now feeds the hourly chip's contentDescription, so a missing
        // branch would silence the chip rather than fail loudly.
        WeatherCode.entries.forEach { code ->
            assertTrue(labelResFor(code) != 0, "$code has no label resource")
        }
    }
}
