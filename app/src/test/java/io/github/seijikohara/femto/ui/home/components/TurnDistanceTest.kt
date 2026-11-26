package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.ui.locale.SpeedUnit
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The turn card's distance: short distances in metres or feet in rounded steps, longer ones as the route row's. */
class TurnDistanceTest {
    @Test
    fun `below 200 m the distance rounds to ten metres`() {
        assertEquals(
            listOf("0" to "m", "90" to "m", "200" to "m"),
            listOf(0.0, 94.0, 196.0).map { metric(it) },
        )
    }

    @Test
    fun `from 200 m the distance rounds to fifty metres`() {
        assertEquals(
            listOf("200" to "m", "250" to "m", "950" to "m"),
            listOf(224.0, 226.0, 974.0).map { metric(it) },
        )
    }

    // 990 m rounds to 1,000 m, which reads in kilometres rather than as "1000 m".
    @Test
    fun `990 m and 1,000 m read in kilometres`() {
        assertEquals(listOf("1.0" to "km", "1.0" to "km"), listOf(990.0, 1_000.0).map { metric(it) })
    }

    @Test
    fun `kilometres read as the route row reads them`() {
        assertEquals(
            listOf("3.4" to "km", "124" to "km"),
            listOf(3_420.0, 123_600.0).map { metric(it) },
        )
    }

    @Test
    fun `990 ft and 1,000 ft read in miles`() {
        assertEquals(
            listOf("0.2" to "mi", "0.2" to "mi"),
            listOf(990.0, 1_000.0).map { feet -> imperial(feet * METERS_PER_FOOT) },
        )
    }

    @Test
    fun `feet round to ten below 200 ft and to fifty from there, up to 950 ft`() {
        assertEquals(
            listOf("150" to "ft", "300" to "ft", "950" to "ft"),
            listOf(147.0, 310.0, 970.0).map { feet -> imperial(feet * METERS_PER_FOOT) },
        )
    }

    // An Egyptian Arabic locale writes native digits by default; the card's numbers stay ASCII below and above
    // 1,000, as the English sentence beside them does.
    @Test
    fun `under a native-digit locale every distance uses ASCII digits`() {
        val values =
            listOf(90.0, 950.0, 3_420.0, 123_600.0)
                .map { turnDistance(it, SpeedUnit.KILOMETERS_PER_HOUR, EGYPTIAN_ARABIC).first }

        assertTrue(values.all { value -> value.all { !it.isDigit() || it in '0'..'9' } }, "values $values")
        assertEquals(listOf("90", "950", "124"), listOf(values[0], values[1], values[3]))
    }

    @Test
    fun `the decimal separator follows the locale`() {
        assertEquals("3,4" to "km", turnDistance(3_420.0, SpeedUnit.KILOMETERS_PER_HOUR, Locale.GERMANY))
    }

    private fun metric(meters: Double) = turnDistance(meters, SpeedUnit.KILOMETERS_PER_HOUR, Locale.US)

    private fun imperial(meters: Double) = turnDistance(meters, SpeedUnit.MILES_PER_HOUR, Locale.US)

    private companion object {
        const val METERS_PER_FOOT = 0.3048

        val EGYPTIAN_ARABIC: Locale = Locale.forLanguageTag("ar-EG")
    }
}
