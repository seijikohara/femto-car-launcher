package io.github.seijikohara.femto.data.weather

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal data class WeatherSnapshot(
    val tempC: Double,
    val apparentTempC: Double,
    val code: WeatherCode,
    val windKmh: Double,
    val humidityPercent: Int?,
    val uvIndex: Double?,
    val isDay: Boolean,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val fetchedAt: Instant,
)

internal data class HourlyForecast(
    val time: LocalTime,
    val tempC: Double,
    val code: WeatherCode,
)

internal data class DailyForecast(
    val date: LocalDate,
    val tempMaxC: Double,
    val tempMinC: Double,
    val code: WeatherCode,
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
        internal fun fromWmo(code: Int): WeatherCode =
            when (code) {
                0 -> CLEAR
                1, 2 -> PARTLY_CLOUDY
                3 -> CLOUDY
                45, 48 -> FOG
                51, 53, 55 -> DRIZZLE
                56, 57 -> FREEZING_RAIN
                61, 63, 65 -> RAIN
                66, 67 -> FREEZING_RAIN
                71, 73, 75 -> SNOW
                77 -> SNOW_GRAINS
                80, 81, 82 -> RAIN_SHOWERS
                85, 86 -> SNOW_SHOWERS
                95, 96, 99 -> THUNDERSTORM
                else -> UNKNOWN
            }
    }
}
