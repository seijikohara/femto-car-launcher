package io.github.seijikohara.femto

import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class TripReadoutTest {
    private lateinit var originalLocale: Locale

    // Pin the locale so the decimal separator in the distance string is
    // deterministic; the production formatter localises it on purpose.
    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `formats metric speed distance and average`() {
        val readout =
            TripState(distanceMeters = 1_234.0, avgSpeedMs = 5.0, currentSpeedMs = 10.0)
                .tripReadout(SpeedUnit.KILOMETERS_PER_HOUR)

        assertEquals("36 km/h", readout.speed) // 10 m/s -> 36 km/h
        assertEquals("1.2 km", readout.distance) // 1234 m -> 1.2 km
        assertEquals("18 km/h", readout.averageSpeed) // 5 m/s -> 18 km/h
    }

    @Test
    fun `formats imperial speed distance and average`() {
        val readout =
            TripState(distanceMeters = 1_609.344, avgSpeedMs = 5.0, currentSpeedMs = 10.0)
                .tripReadout(SpeedUnit.MILES_PER_HOUR)

        assertEquals("22 mph", readout.speed) // 10 m/s -> 22.37 mph -> 22
        assertEquals("1.0 mi", readout.distance) // 1609.344 m -> 1.0 mi
        assertEquals("11 mph", readout.averageSpeed) // 5 m/s -> 11.18 mph -> 11
    }

    @Test
    fun `renders a fresh trip as zeroes`() {
        val readout = TripState.Initial.tripReadout(SpeedUnit.KILOMETERS_PER_HOUR)

        assertEquals("0 km/h", readout.speed)
        assertEquals("0.0 km", readout.distance)
        assertEquals("0 km/h", readout.averageSpeed)
    }
}
