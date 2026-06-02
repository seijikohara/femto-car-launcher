package io.github.seijikohara.femto.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SystemUnitsTest {
    @Test
    fun `us country defaults to imperial speed and distance`() {
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(Locale.US))
        assertEquals(DistanceUnit.FEET, distanceUnitFor(Locale.US))
    }

    @Test
    fun `gb country defaults to imperial speed and distance`() {
        val gb = Locale
            .Builder()
            .setLanguage("en")
            .setRegion("GB")
            .build()
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(gb))
        assertEquals(DistanceUnit.FEET, distanceUnitFor(gb))
    }

    @Test
    fun `mm country defaults to imperial speed and distance`() {
        val mm = Locale
            .Builder()
            .setLanguage("my")
            .setRegion("MM")
            .build()
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(mm))
        assertEquals(DistanceUnit.FEET, distanceUnitFor(mm))
    }

    @Test
    fun `jp country defaults to metric speed and distance`() {
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(Locale.JAPAN))
        assertEquals(DistanceUnit.METERS, distanceUnitFor(Locale.JAPAN))
    }

    @Test
    fun `de country defaults to metric speed and distance`() {
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(Locale.GERMANY))
        assertEquals(DistanceUnit.METERS, distanceUnitFor(Locale.GERMANY))
    }

    @Test
    fun `unknown country defaults to metric`() {
        val xx = Locale
            .Builder()
            .setLanguage("xx")
            .setRegion("XX")
            .build()
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(xx))
        assertEquals(DistanceUnit.METERS, distanceUnitFor(xx))
    }

    @Test
    fun `tripDistanceFromMeters returns kilometres when speed unit is metric`() {
        assertEquals(1.0, SpeedUnit.KILOMETERS_PER_HOUR.tripDistanceFromMeters(1000.0), 1e-6)
    }

    @Test
    fun `tripDistanceFromMeters returns miles when speed unit is imperial`() {
        assertEquals(1.0, SpeedUnit.MILES_PER_HOUR.tripDistanceFromMeters(1609.344), 1e-6)
    }

    @Test
    fun `distanceLabel returns km when speed unit is metric`() {
        assertEquals("km", SpeedUnit.KILOMETERS_PER_HOUR.distanceLabel())
    }

    @Test
    fun `distanceLabel returns mi when speed unit is imperial`() {
        assertEquals("mi", SpeedUnit.MILES_PER_HOUR.distanceLabel())
    }

    @Test
    fun `temperatureUnitFor returns fahrenheit when country is us`() {
        assertEquals(TemperatureUnit.FAHRENHEIT, temperatureUnitFor(Locale.US))
    }

    @Test
    fun `temperatureUnitFor returns celsius when country is gb`() {
        val gb = Locale
            .Builder()
            .setLanguage("en")
            .setRegion("GB")
            .build()
        assertEquals(TemperatureUnit.CELSIUS, temperatureUnitFor(gb))
    }

    @Test
    fun `temperatureUnitFor returns celsius when country is jp`() {
        assertEquals(TemperatureUnit.CELSIUS, temperatureUnitFor(Locale.JAPAN))
    }

    @Test
    fun `fromCelsius returns 32 when zero celsius in fahrenheit`() {
        assertEquals(32.0, TemperatureUnit.FAHRENHEIT.fromCelsius(0.0), 1e-6)
    }

    @Test
    fun `fromCelsius returns 212 when hundred celsius in fahrenheit`() {
        assertEquals(212.0, TemperatureUnit.FAHRENHEIT.fromCelsius(100.0), 1e-6)
    }

    @Test
    fun `fromCelsius returns input when celsius`() {
        assertEquals(0.0, TemperatureUnit.CELSIUS.fromCelsius(0.0), 1e-6)
    }

    @Test
    fun `label returns degrees celsius when celsius`() {
        assertEquals("°C", TemperatureUnit.CELSIUS.label())
    }

    @Test
    fun `label returns degrees fahrenheit when fahrenheit`() {
        assertEquals("°F", TemperatureUnit.FAHRENHEIT.label())
    }
}
