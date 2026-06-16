package io.github.seijikohara.femto.data.weather

import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SunTimesTest {
    @Test
    fun `computes Tokyo sunrise and sunset near the published times at the summer solstice`() {
        // NAOJ published 2026-06-21 Tokyo: sunrise 04:25, sunset 19:00 JST.
        // The algorithm is ~1 min accurate; allow a generous window.
        val sun =
            SunCalculator.compute(
                latitude = 35.6580,
                longitude = 139.7016,
                date = LocalDate.of(2026, 6, 21),
                zone = ZoneId.of("Asia/Tokyo"),
            )

        val sunrise = requireNotNull(sun.sunrise)
        val sunset = requireNotNull(sun.sunset)
        assertTrue(sunrise in LocalTime.of(4, 15)..LocalTime.of(4, 35), "sunrise was $sunrise")
        assertTrue(sunset in LocalTime.of(18, 50)..LocalTime.of(19, 10), "sunset was $sunset")
        assertTrue(sunrise < sunset)
    }

    @Test
    fun `returns null sun times during polar day`() {
        // High Arctic at the summer solstice: the sun never sets.
        val sun =
            SunCalculator.compute(
                latitude = 78.0,
                longitude = 15.0,
                date = LocalDate.of(2026, 6, 21),
                zone = ZoneOffset.UTC,
            )

        assertNull(sun.sunrise)
        assertNull(sun.sunset)
    }

    @Test
    fun `returns null sun times during polar night`() {
        // High Arctic at the winter solstice: the sun never rises.
        val sun =
            SunCalculator.compute(
                latitude = 78.0,
                longitude = 15.0,
                date = LocalDate.of(2026, 12, 21),
                zone = ZoneOffset.UTC,
            )

        assertNull(sun.sunrise)
        assertNull(sun.sunset)
    }
}
