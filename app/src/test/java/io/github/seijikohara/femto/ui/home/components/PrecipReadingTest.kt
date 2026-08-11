package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import org.junit.Test
import kotlin.test.assertEquals

/**
 * What the weather card's PRECIP slot reports, resolved before any formatting.
 *
 * The slot originally keyed on MET's `probability_of_precipitation` alone, which
 * MET publishes only inside its Nordic model domain — so outside it the card
 * rendered a permanent em dash. These cases pin the fallback that fixed it, and
 * the dry cut-off that keeps a trace from reading like rain.
 *
 * The cut-off tracks the display unit, so the imperial cases below are not
 * duplicates of the metric ones: they pin a different threshold.
 */
class PrecipReadingTest {
    @Test
    fun `a published chance wins over the amount`() {
        val reading =
            precipReading(
                fakeWeatherSnapshot(precipitationProbabilityPercent = 60, precipitationMm = 2.0),
                SpeedUnit.KILOMETERS_PER_HOUR,
            )

        // A probability answers the driver's question better than a quantity, so it
        // takes precedence wherever MET publishes one.
        assertEquals(PrecipReading.Chance(60), reading)
    }

    @Test
    fun `without a chance the amount is reported`() {
        // The case that covers most of the world: no probability in the response.
        val reading =
            precipReading(
                fakeWeatherSnapshot(precipitationProbabilityPercent = null, precipitationMm = 1.4),
                SpeedUnit.KILOMETERS_PER_HOUR,
            )

        assertEquals(PrecipReading.Amount(1.4), reading)
    }

    @Test
    fun `an amount below the dry threshold reads as dry, not as a number`() {
        // MET sends a bare 0.0 for most hours; a slot permanently reading "0.0 mm"
        // looks like a stuck gauge.
        val reading =
            precipReading(
                fakeWeatherSnapshot(precipitationProbabilityPercent = null, precipitationMm = 0.0),
                SpeedUnit.KILOMETERS_PER_HOUR,
            )

        assertEquals(PrecipReading.Dry, reading)
    }

    @Test
    fun `a trace under the threshold is dry even though it would format as 0_1`() {
        // The cut-off is a threshold, not the display rounding: %.1f would render
        // 0.09 as "0.1", which reads as real rain.
        val reading =
            precipReading(
                fakeWeatherSnapshot(precipitationProbabilityPercent = null, precipitationMm = 0.09),
                SpeedUnit.KILOMETERS_PER_HOUR,
            )

        assertEquals(PrecipReading.Dry, reading)
    }

    @Test
    fun `the threshold itself counts as an amount`() {
        val reading =
            precipReading(
                fakeWeatherSnapshot(precipitationProbabilityPercent = null, precipitationMm = 0.1),
                SpeedUnit.KILOMETERS_PER_HOUR,
            )

        assertEquals(PrecipReading.Amount(0.1), reading)
    }

    @Test
    fun `an amount the metric slot would show is dry when inches cannot print it`() {
        // 0.2 mm is 0.008 in, which "%.2f" renders as "0.00" — the imperial form of
        // the stuck-gauge reading the metric threshold exists to prevent.
        val reading =
            precipReading(
                fakeWeatherSnapshot(precipitationProbabilityPercent = null, precipitationMm = 0.2),
                SpeedUnit.MILES_PER_HOUR,
            )

        assertEquals(PrecipReading.Dry, reading)
    }

    @Test
    fun `one hundredth of an inch is the smallest imperial amount`() {
        val reading =
            precipReading(
                fakeWeatherSnapshot(precipitationProbabilityPercent = null, precipitationMm = 0.254),
                SpeedUnit.MILES_PER_HOUR,
            )

        assertEquals(PrecipReading.Amount(0.254), reading)
    }

    @Test
    fun `neither field present is unknown, not zero`() {
        // A missing reading must not be rendered as "no rain" — the card says so
        // with an em dash instead.
        val reading =
            precipReading(
                fakeWeatherSnapshot(precipitationProbabilityPercent = null, precipitationMm = null),
                SpeedUnit.KILOMETERS_PER_HOUR,
            )

        assertEquals(PrecipReading.Unknown, reading)
    }
}
