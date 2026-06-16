package io.github.seijikohara.femto.data.weather

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * Mapping table for [WeatherCode.fromMetSymbol]: MET Norway symbol_code strings
 * collapse to a [WeatherCode] by precedence (thunder > frozen > liquid > sky),
 * with the _day / _night suffix ignored and unknown / blank / null falling back
 * to UNKNOWN.
 */
@RunWith(Parameterized::class)
internal class WeatherCodeTest(
    private val symbol: String?,
    private val expected: WeatherCode,
) {
    @Test
    fun `maps the MET symbol code to a WeatherCode`() {
        assertEquals(expected, WeatherCode.fromMetSymbol(symbol))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun cases(): List<Array<Any?>> =
            listOf(
                // Sky conditions (the _day / _night suffix is ignored).
                arrayOf("clearsky_day", WeatherCode.CLEAR),
                arrayOf("clearsky_night", WeatherCode.CLEAR),
                arrayOf("fair_day", WeatherCode.PARTLY_CLOUDY),
                arrayOf("partlycloudy_night", WeatherCode.PARTLY_CLOUDY),
                arrayOf("cloudy", WeatherCode.CLOUDY),
                arrayOf("fog", WeatherCode.FOG),
                // Precipitation by intensity and shower form.
                arrayOf("lightrain", WeatherCode.DRIZZLE),
                arrayOf("rain", WeatherCode.RAIN),
                arrayOf("heavyrain", WeatherCode.RAIN),
                arrayOf("lightrainshowers_day", WeatherCode.RAIN_SHOWERS),
                arrayOf("rainshowers_night", WeatherCode.RAIN_SHOWERS),
                arrayOf("snow", WeatherCode.SNOW),
                arrayOf("lightsnowshowers_day", WeatherCode.SNOW_SHOWERS),
                arrayOf("sleet", WeatherCode.FREEZING_RAIN),
                arrayOf("lightsleet", WeatherCode.FREEZING_RAIN),
                // Thunder takes precedence over the precipitation form (including
                // the MET "lights...andthunder" typo variants).
                arrayOf("rainandthunder", WeatherCode.THUNDERSTORM),
                arrayOf("lightssleetshowersandthunder_day", WeatherCode.THUNDERSTORM),
                // Unknown / blank / null fall back to UNKNOWN.
                arrayOf("definitelynotacode", WeatherCode.UNKNOWN),
                arrayOf("", WeatherCode.UNKNOWN),
                arrayOf(null, WeatherCode.UNKNOWN),
            )
    }
}
