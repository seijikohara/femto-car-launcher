package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.ui.locale.SpeedUnit
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals

/** The speed readout's route row: its remaining distance and its arrival time. */
class RouteRowFormatTest {
    @Test
    fun `a short distance shows tenths of a kilometre`() {
        assertEquals("3.4", remainingDistanceText(3_420.0, SpeedUnit.KILOMETERS_PER_HOUR, Locale.US))
    }

    @Test
    fun `a long distance shows whole kilometres`() {
        assertEquals("124", remainingDistanceText(123_600.0, SpeedUnit.KILOMETERS_PER_HOUR, Locale.US))
    }

    @Test
    fun `a distance that rounds to ten shows whole kilometres`() {
        assertEquals("10", remainingDistanceText(9_960.0, SpeedUnit.KILOMETERS_PER_HOUR, Locale.US))
    }

    @Test
    fun `a distance just short of rounding to ten keeps its tenths`() {
        assertEquals("9.9", remainingDistanceText(9_940.0, SpeedUnit.KILOMETERS_PER_HOUR, Locale.US))
    }

    @Test
    fun `miles follow the miles speed unit`() {
        assertEquals("2.0", remainingDistanceText(3_218.7, SpeedUnit.MILES_PER_HOUR, Locale.US))
    }

    @Test
    fun `the decimal separator follows the locale`() {
        assertEquals("3,4", remainingDistanceText(3_420.0, SpeedUnit.KILOMETERS_PER_HOUR, Locale.GERMANY))
    }

    @Test
    fun `the arrival is now plus the time left on the 24-hour clock`() {
        assertEquals("10:38", routeArrivalText(NOW, ZoneOffset.UTC, remainingSeconds = 1_800.0, is24Hour = true))
    }

    // The day-period marker follows the JVM's locale, so the expectation goes
    // through the same shared formatter rather than a literal "PM".
    @Test
    fun `the arrival follows the 12-hour clock`() {
        assertEquals(
            LocalTime.of(13, 8).format(clockTimeFormatter(is24Hour = false)),
            routeArrivalText(NOW, ZoneOffset.UTC, remainingSeconds = 3 * 3_600.0, is24Hour = false),
        )
    }

    @Test
    fun `the arrival rounds to the nearest minute`() {
        assertEquals("10:09", routeArrivalText(NOW, ZoneOffset.UTC, remainingSeconds = 31.0, is24Hour = true))
        assertEquals("10:08", routeArrivalText(NOW, ZoneOffset.UTC, remainingSeconds = 29.0, is24Hour = true))
    }

    @Test
    fun `the arrival is on the clock's zone`() {
        assertEquals(
            "19:38",
            routeArrivalText(NOW, ZoneId.of("Asia/Tokyo"), remainingSeconds = 1_800.0, is24Hour = true),
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-05-01T10:08:00Z")
    }
}
