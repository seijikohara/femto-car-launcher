package io.github.seijikohara.femto.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SystemUnitsTest {
    @Test
    fun `us country defaults to imperial speed`() {
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(Locale.US))
    }

    @Test
    fun `gb country defaults to imperial speed`() {
        val gb = Locale
            .Builder()
            .setLanguage("en")
            .setRegion("GB")
            .build()
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(gb))
    }

    @Test
    fun `mm country defaults to imperial speed`() {
        val mm = Locale
            .Builder()
            .setLanguage("my")
            .setRegion("MM")
            .build()
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(mm))
    }

    @Test
    fun `jp country defaults to metric speed`() {
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(Locale.JAPAN))
    }

    @Test
    fun `de country defaults to metric speed`() {
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(Locale.GERMANY))
    }

    @Test
    fun `unknown country defaults to metric`() {
        val xx = Locale
            .Builder()
            .setLanguage("xx")
            .setRegion("XX")
            .build()
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(xx))
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
    fun `temperatureUnitFor returns fahrenheit for us territory guam`() {
        // GU follows US weather conventions but is absent from the platform
        // country table on the JVM; the expanded fallback set must carry it.
        val guam = Locale
            .Builder()
            .setLanguage("en")
            .setRegion("GU")
            .build()
        assertEquals(TemperatureUnit.FAHRENHEIT, temperatureUnitFor(guam))
    }

    @Test
    fun `temperatureUnitFor honors u-mu-celsius override against fahrenheit country`() {
        // A US locale that explicitly requests Celsius via CLDR must win over
        // the country default.
        val usCelsius = Locale.forLanguageTag("en-US-u-mu-celsius")
        assertEquals(TemperatureUnit.CELSIUS, temperatureUnitFor(usCelsius))
    }

    @Test
    fun `temperatureUnitFor honors u-mu-fahrenhe override against celsius country`() {
        // A metric locale that explicitly requests Fahrenheit via CLDR must win
        // over the country default.
        val deFahrenheit = Locale.forLanguageTag("de-DE-u-mu-fahrenhe")
        assertEquals(TemperatureUnit.FAHRENHEIT, temperatureUnitFor(deFahrenheit))
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

    @Test
    fun `windLabel returns metres per second when speed unit is metric`() {
        // 36 km/h is 10 m/s; the metric branch keeps m/s per the mockup.
        assertEquals("10 m/s", windLabel(36.0, SpeedUnit.KILOMETERS_PER_HOUR))
    }

    @Test
    fun `windLabel returns miles per hour when speed unit is imperial`() {
        // 100 km/h rounds to 62 mph, matching the SpeedOverlay reading.
        assertEquals("62 mph", windLabel(100.0, SpeedUnit.MILES_PER_HOUR))
    }
}
