package io.github.seijikohara.femto.data

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal class WeatherRepository(
    private val api: OpenMeteoApi,
    private val locationFlow: Flow<Location?>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var cached: WeatherSnapshot? = null
    private var lastFetchLocation: Location? = null

    fun snapshotFlow(): Flow<WeatherSnapshot?> =
        flow {
            locationFlow.collect { location ->
                emit(refresh(location) ?: cached)
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun refresh(location: Location?): WeatherSnapshot? {
        location ?: return null
        if (!shouldRefetch(location)) return cached
        val response = api.forecast(location.latitude, location.longitude) ?: return cached
        val current = response.current ?: return cached
        cached =
            WeatherSnapshot(
                tempC = current.temperature_2m,
                apparentTempC = current.apparent_temperature,
                code = WeatherCode.fromWmo(current.weathercode),
                windKmh = current.windspeed_10m,
                humidityPercent = current.relative_humidity_2m?.roundToInt(),
                uvIndex = current.uv_index,
                isDay = current.is_day == 1,
                sunrise = response.daily
                    ?.sunrise
                    ?.firstOrNull()
                    ?.let(::parseLocalTime),
                sunset = response.daily
                    ?.sunset
                    ?.firstOrNull()
                    ?.let(::parseLocalTime),
                hourly = response.hourly?.let { hourlySliceFrom(it, current.time) }.orEmpty(),
                daily = response.daily?.let(::dailyForecastsFrom).orEmpty(),
                fetchedAt = clock.instant(),
            )
        lastFetchLocation = location
        return cached
    }

    private fun shouldRefetch(location: Location): Boolean {
        val snapshot = cached ?: return true
        val anchor = lastFetchLocation ?: return true
        val ageOk = Duration.between(snapshot.fetchedAt, clock.instant()).abs() < REFRESH_INTERVAL
        val nearOk = anchor.distanceTo(location) < REFRESH_DISTANCE_M
        return !(ageOk && nearOk)
    }

    private fun hourlySliceFrom(
        hourly: OpenMeteoApi.Hourly,
        currentTime: String,
    ): List<HourlyForecast> {
        val now = runCatching { LocalDateTime.parse(currentTime) }.getOrNull() ?: return emptyList()
        val start =
            hourly.time
                .indexOfFirst { entry ->
                    runCatching { LocalDateTime.parse(entry) }
                        .getOrNull()
                        ?.let { !it.isBefore(now) } == true
                }.takeIf { it >= 0 } ?: return emptyList()
        val end = (start + HOURLY_SLICE_LENGTH).coerceAtMost(hourly.time.size)
        return (start until end).mapNotNull { i ->
            val time = runCatching { LocalDateTime.parse(hourly.time[i]).toLocalTime() }.getOrNull()
                ?: return@mapNotNull null
            val temp = hourly.temperature_2m.getOrNull(i) ?: return@mapNotNull null
            val code = hourly.weathercode.getOrNull(i)?.let(WeatherCode::fromWmo) ?: return@mapNotNull null
            HourlyForecast(time = time, tempC = temp, code = code)
        }
    }

    private fun dailyForecastsFrom(daily: OpenMeteoApi.Daily): List<DailyForecast> =
        daily.time.indices.mapNotNull { i ->
            val date = runCatching { LocalDate.parse(daily.time[i]) }.getOrNull() ?: return@mapNotNull null
            val max = daily.temperature_2m_max.getOrNull(i) ?: return@mapNotNull null
            val min = daily.temperature_2m_min.getOrNull(i) ?: return@mapNotNull null
            val code = daily.weathercode.getOrNull(i)?.let(WeatherCode::fromWmo) ?: return@mapNotNull null
            DailyForecast(date = date, tempMaxC = max, tempMinC = min, code = code)
        }

    private fun parseLocalTime(iso: String): LocalTime? =
        runCatching { LocalDateTime.parse(iso, ISO_LOCAL).toLocalTime() }.getOrNull()

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofMinutes(30)
        const val REFRESH_DISTANCE_M = 5_000f
        const val HOURLY_SLICE_LENGTH = 5

        // Open-Meteo with timezone=auto returns naive local times (no offset suffix).
        val ISO_LOCAL: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
