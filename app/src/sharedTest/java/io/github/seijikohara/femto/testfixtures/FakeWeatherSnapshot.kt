package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.weather.DailyForecast
import io.github.seijikohara.femto.data.weather.HourlyForecast
import io.github.seijikohara.femto.data.weather.WeatherCode
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal fun fakeWeatherSnapshot(
    tempC: Double = 18.0,
    precipitationProbabilityPercent: Int? = 20,
    code: WeatherCode = WeatherCode.CLEAR,
    windKmh: Double = 9.6,
    windDirectionDeg: Double? = 225.0,
    humidityPercent: Int? = 58,
    uvIndex: Double? = 4.0,
    isDay: Boolean = true,
    sunrise: LocalTime? = LocalTime.of(5, 42),
    sunset: LocalTime? = LocalTime.of(19, 14),
    // The first 12 hours predate the precip fields and stay byte-identical for
    // the dashboard card's visible grid; the appended overnight hours carry a
    // rain window so the panel's curve, bars, and nowcast render a live shape.
    hourly: List<HourlyForecast> =
        listOf(
            HourlyForecast(LocalTime.of(12, 0), 19.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(13, 0), 20.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(14, 0), 21.0, WeatherCode.PARTLY_CLOUDY),
            HourlyForecast(LocalTime.of(15, 0), 21.0, WeatherCode.PARTLY_CLOUDY),
            HourlyForecast(LocalTime.of(16, 0), 20.0, WeatherCode.PARTLY_CLOUDY),
            HourlyForecast(LocalTime.of(17, 0), 19.0, WeatherCode.CLOUDY),
            HourlyForecast(
                LocalTime.of(18, 0),
                18.0,
                WeatherCode.CLOUDY,
                precipitationMm = 0.2,
                precipitationProbabilityPercent = 35,
            ),
            HourlyForecast(
                LocalTime.of(19, 0),
                16.0,
                WeatherCode.CLOUDY,
                precipitationMm = 0.6,
                precipitationProbabilityPercent = 55,
            ),
            HourlyForecast(LocalTime.of(20, 0), 15.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(21, 0), 14.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(22, 0), 13.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(23, 0), 12.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(0, 0), 11.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(1, 0), 11.0, WeatherCode.CLOUDY),
            HourlyForecast(LocalTime.of(2, 0), 10.0, WeatherCode.CLOUDY),
            HourlyForecast(
                LocalTime.of(3, 0),
                10.0,
                WeatherCode.RAIN,
                precipitationMm = 1.4,
                precipitationProbabilityPercent = 70,
            ),
            HourlyForecast(
                LocalTime.of(4, 0),
                9.0,
                WeatherCode.RAIN,
                precipitationMm = 2.2,
                precipitationProbabilityPercent = 80,
            ),
            HourlyForecast(
                LocalTime.of(5, 0),
                9.0,
                WeatherCode.RAIN_SHOWERS,
                precipitationMm = 1.0,
                precipitationProbabilityPercent = 65,
            ),
            HourlyForecast(LocalTime.of(6, 0), 10.0, WeatherCode.PARTLY_CLOUDY),
            HourlyForecast(LocalTime.of(7, 0), 12.0, WeatherCode.PARTLY_CLOUDY),
            HourlyForecast(LocalTime.of(8, 0), 14.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(9, 0), 15.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(10, 0), 17.0, WeatherCode.CLEAR),
            HourlyForecast(LocalTime.of(11, 0), 18.0, WeatherCode.CLEAR),
        ),
    daily: List<DailyForecast> =
        listOf(
            DailyForecast(LocalDate.of(2026, 5, 1), 22.0, 14.0, WeatherCode.CLEAR),
            DailyForecast(LocalDate.of(2026, 5, 2), 23.0, 15.0, WeatherCode.PARTLY_CLOUDY),
            DailyForecast(LocalDate.of(2026, 5, 3), 21.0, 14.0, WeatherCode.RAIN, precipitationProbabilityPercent = 65),
            DailyForecast(LocalDate.of(2026, 5, 4), 20.0, 13.0, WeatherCode.CLOUDY),
            DailyForecast(LocalDate.of(2026, 5, 5), 22.0, 14.0, WeatherCode.CLEAR),
            DailyForecast(
                LocalDate.of(2026, 5, 6),
                18.0,
                12.0,
                WeatherCode.RAIN_SHOWERS,
                precipitationProbabilityPercent = 75,
            ),
            DailyForecast(LocalDate.of(2026, 5, 7), 21.0, 13.0, WeatherCode.PARTLY_CLOUDY),
        ),
    fetchedAt: Instant = Instant.parse("2026-05-01T05:32:00Z"),
): WeatherSnapshot =
    WeatherSnapshot(
        tempC = tempC,
        precipitationProbabilityPercent = precipitationProbabilityPercent,
        code = code,
        windKmh = windKmh,
        windDirectionDeg = windDirectionDeg,
        humidityPercent = humidityPercent,
        uvIndex = uvIndex,
        isDay = isDay,
        sunrise = sunrise,
        sunset = sunset,
        hourly = hourly,
        daily = daily,
        fetchedAt = fetchedAt,
    )
