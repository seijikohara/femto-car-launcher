package io.github.seijikohara.femto.data.weather

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal data class WeatherSnapshot(
    val tempC: Double,
    val apparentTempC: Double,
    val code: WeatherCode,
    val windKmh: Double,
    // Meteorological "from" direction in degrees (0 = from north, clockwise);
    // null when MET omits it.
    val windDirectionDeg: Double? = null,
    val humidityPercent: Int?,
    val uvIndex: Double?,
    val isDay: Boolean,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val fetchedAt: Instant,
)

// A snapshot older than this reads as stale. Under normal operation the weather
// refreshes every REFRESH_INTERVAL (30 min), so crossing 2x that means at least
// two missed refresh windows — a real outage (e.g. a 429 throttle from
// api.met.no), not a routine gap. The card surfaces an "as of HH:mm" caption past
// this age so hours-old data is never shown as current.
internal val WEATHER_STALE_THRESHOLD: Duration = Duration.ofMinutes(60)

/**
 * Return whether this snapshot is older than [WEATHER_STALE_THRESHOLD] at [now].
 * `abs()` tolerates a clock that moved backwards between fetch and read (NTP
 * correction, manual time change) rather than reporting a future fetch as fresh
 * forever.
 */
internal fun WeatherSnapshot.isStale(now: Instant): Boolean =
    Duration.between(fetchedAt, now).abs() >= WEATHER_STALE_THRESHOLD

internal data class HourlyForecast(
    val time: LocalTime,
    val tempC: Double,
    val code: WeatherCode,
    // Expected precipitation over the following hour (mm) and its probability
    // (percent). Both null when MET omits the block (probability coverage is
    // regional) — consumers degrade quietly rather than showing zeros.
    val precipitationMm: Double? = null,
    val precipitationProbabilityPercent: Int? = null,
)

internal data class DailyForecast(
    val date: LocalDate,
    val tempMaxC: Double,
    val tempMinC: Double,
    val code: WeatherCode,
    // The day's peak precipitation probability (percent); null when MET carries
    // no probability for any of the day's periods.
    val precipitationProbabilityPercent: Int? = null,
)

internal enum class WeatherCode {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    FREEZING_RAIN,
    SNOW,
    SNOW_GRAINS,
    RAIN_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    UNKNOWN,
    ;

    companion object {
        // MET Norway symbol codes are concatenated word tokens with an optional
        // _day / _night / _polartwilight suffix, e.g. "lightrainshowers_day".
        // Match the base token by precedence: thunder > frozen (sleet/snow) >
        // liquid (rain) > sky condition. SNOW_GRAINS / DRIZZLE have no exact MET
        // equivalent — light rain maps to DRIZZLE; snow grains never occur.
        internal fun fromMetSymbol(symbolCode: String?): WeatherCode {
            val base = symbolCode?.substringBefore('_').orEmpty()
            return when {
                base.isEmpty() -> {
                    UNKNOWN
                }

                base.contains("thunder") -> {
                    THUNDERSTORM
                }

                base.contains("sleet") -> {
                    FREEZING_RAIN
                }

                base.contains("snow") -> {
                    if (base.contains("showers")) SNOW_SHOWERS else SNOW
                }

                base.contains("rain") -> {
                    when {
                        base.contains("showers") -> RAIN_SHOWERS
                        base.startsWith("light") -> DRIZZLE
                        else -> RAIN
                    }
                }

                base == "fog" -> {
                    FOG
                }

                base == "cloudy" -> {
                    CLOUDY
                }

                base == "partlycloudy" || base == "fair" -> {
                    PARTLY_CLOUDY
                }

                base == "clearsky" -> {
                    CLEAR
                }

                else -> {
                    UNKNOWN
                }
            }
        }
    }
}
