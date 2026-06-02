package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.DailyForecast
import io.github.seijikohara.femto.data.HourlyForecast
import io.github.seijikohara.femto.data.WeatherCode
import io.github.seijikohara.femto.data.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal fun fakeWeatherSnapshot(
    tempC: Double = 18.0,
    apparentTempC: Double = 17.0,
    code: WeatherCode = WeatherCode.CLEAR,
    windKmh: Double = 9.6,
    humidityPercent: Int? = 58,
    uvIndex: Double? = 4.0,
    isDay: Boolean = true,
    sunrise: LocalTime? = LocalTime.of(5, 42),
    sunset: LocalTime? = LocalTime.of(19, 14),
    hourly: List<HourlyForecast> =
        listOf(
            HourlyForecast(LocalTime.of(12, 0), 19.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(13, 0), 20.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(14, 0), 21.0, WeatherCode.PARTLY_CLOUDY),
            HourlyForecast(LocalTime.of(15, 0), 21.0, WeatherCode.PARTLY_CLOUDY),
        ),
    daily: List<DailyForecast> =
        listOf(
            DailyForecast(LocalDate.of(2026, 5, 1), 22.0, 14.0, WeatherCode.CLEAR),
            DailyForecast(LocalDate.of(2026, 5, 2), 23.0, 15.0, WeatherCode.PARTLY_CLOUDY),
            DailyForecast(LocalDate.of(2026, 5, 3), 21.0, 14.0, WeatherCode.RAIN),
            DailyForecast(LocalDate.of(2026, 5, 4), 20.0, 13.0, WeatherCode.CLOUDY),
            DailyForecast(LocalDate.of(2026, 5, 5), 22.0, 14.0, WeatherCode.CLEAR),
        ),
    fetchedAt: Instant = Instant.parse("2026-05-01T05:32:00Z"),
): WeatherSnapshot =
    WeatherSnapshot(
        tempC = tempC,
        apparentTempC = apparentTempC,
        code = code,
        windKmh = windKmh,
        humidityPercent = humidityPercent,
        uvIndex = uvIndex,
        isDay = isDay,
        sunrise = sunrise,
        sunset = sunset,
        hourly = hourly,
        daily = daily,
        fetchedAt = fetchedAt,
    )
