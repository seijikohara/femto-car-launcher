package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.weather.HourlyForecast
import io.github.seijikohara.femto.data.weather.WeatherCode
import org.junit.Test
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrecipOutlookTest {
    @Test
    fun `a dry window yields no outlook`() {
        assertNull(precipOutlookOrNull(hours { _ -> null }))
        assertNull(precipOutlookOrNull(emptyList()))
        // Trace amounts below the onset threshold stay silent.
        assertNull(precipOutlookOrNull(hours { i -> if (i == 3) 0.05 else null }))
    }

    @Test
    fun `rain in the first hour reads as now`() {
        val outlook = precipOutlookOrNull(hours { i -> if (i == 0) 0.8 else null })
        val now = assertIs<PrecipOutlook.Now>(outlook)
        assertEquals(60, now.probabilityPercent)
    }

    @Test
    fun `the first wet hour within the horizon reads as upcoming with its time`() {
        val outlook = precipOutlookOrNull(hours { i -> if (i >= 5) 1.2 else null })
        val upcoming = assertIs<PrecipOutlook.Upcoming>(outlook)
        assertEquals(LocalTime.of(17, 0), upcoming.at)
    }

    @Test
    fun `rain beyond the horizon is not called out`() {
        // Wet only at hour 14 — past the 12 h nowcast window.
        assertNull(precipOutlookOrNull(hours(count = 24) { i -> if (i == 14) 2.0 else null }))
    }

    @Test
    fun `snow codes carry the snow flag`() {
        val outlook =
            precipOutlookOrNull(
                listOf(HourlyForecast(LocalTime.of(9, 0), -1.0, WeatherCode.SNOW, precipitationMm = 0.7)),
            )
        assertTrue(assertIs<PrecipOutlook.Now>(outlook).snow)
    }

    private fun hours(
        count: Int = 12,
        mmAt: (Int) -> Double?,
    ): List<HourlyForecast> =
        (0 until count).map { i ->
            val mm = mmAt(i)
            HourlyForecast(
                time = LocalTime.of((12 + i) % 24, 0),
                tempC = 15.0,
                code = if (mm != null) WeatherCode.RAIN else WeatherCode.CLEAR,
                precipitationMm = mm,
                precipitationProbabilityPercent = if (mm != null) 60 else null,
            )
        }
}
