package io.github.seijikohara.femto.data.weather

import org.junit.Test
import kotlin.test.assertEquals

class WeatherCodeTest {
    @Test
    fun `maps MET sky-condition symbols`() {
        assertEquals(WeatherCode.CLEAR, WeatherCode.fromMetSymbol("clearsky_day"))
        assertEquals(WeatherCode.CLEAR, WeatherCode.fromMetSymbol("clearsky_night"))
        assertEquals(WeatherCode.PARTLY_CLOUDY, WeatherCode.fromMetSymbol("fair_day"))
        assertEquals(WeatherCode.PARTLY_CLOUDY, WeatherCode.fromMetSymbol("partlycloudy_night"))
        assertEquals(WeatherCode.CLOUDY, WeatherCode.fromMetSymbol("cloudy"))
        assertEquals(WeatherCode.FOG, WeatherCode.fromMetSymbol("fog"))
    }

    @Test
    fun `maps MET precipitation symbols by intensity and shower form`() {
        assertEquals(WeatherCode.DRIZZLE, WeatherCode.fromMetSymbol("lightrain"))
        assertEquals(WeatherCode.RAIN, WeatherCode.fromMetSymbol("rain"))
        assertEquals(WeatherCode.RAIN, WeatherCode.fromMetSymbol("heavyrain"))
        assertEquals(WeatherCode.RAIN_SHOWERS, WeatherCode.fromMetSymbol("lightrainshowers_day"))
        assertEquals(WeatherCode.RAIN_SHOWERS, WeatherCode.fromMetSymbol("rainshowers_night"))
        assertEquals(WeatherCode.SNOW, WeatherCode.fromMetSymbol("snow"))
        assertEquals(WeatherCode.SNOW_SHOWERS, WeatherCode.fromMetSymbol("lightsnowshowers_day"))
        assertEquals(WeatherCode.FREEZING_RAIN, WeatherCode.fromMetSymbol("sleet"))
        assertEquals(WeatherCode.FREEZING_RAIN, WeatherCode.fromMetSymbol("lightsleet"))
    }

    @Test
    fun `thunder takes precedence over the precipitation form`() {
        assertEquals(WeatherCode.THUNDERSTORM, WeatherCode.fromMetSymbol("rainandthunder"))
        // The MET typo variants ("lights...andthunder") still resolve to thunder.
        assertEquals(WeatherCode.THUNDERSTORM, WeatherCode.fromMetSymbol("lightssleetshowersandthunder_day"))
    }

    @Test
    fun `unknown, blank, and null symbols map to UNKNOWN`() {
        assertEquals(WeatherCode.UNKNOWN, WeatherCode.fromMetSymbol("definitelynotacode"))
        assertEquals(WeatherCode.UNKNOWN, WeatherCode.fromMetSymbol(""))
        assertEquals(WeatherCode.UNKNOWN, WeatherCode.fromMetSymbol(null))
    }
}
