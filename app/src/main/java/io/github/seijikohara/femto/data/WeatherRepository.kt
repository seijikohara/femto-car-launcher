package io.github.seijikohara.femto.data

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal class WeatherRepository(
    private val api: OpenMeteoApi,
    private val locationFlow: Flow<Location?>,
    private val clockFlow: Flow<ClockTick>,
    private val clock: Clock = Clock.systemUTC(),
) {
    // Serialises refresh: merge() lets the location and clock upstreams reach
    // refresh concurrently on the IO pool, and an unguarded check-fetch-write
    // sequence could double-fetch or bypass the outage throttle.
    private val mutex = Mutex()
    private var cached: WeatherSnapshot? = null
    private var lastFetchLocation: Location? = null

    // Tracks the most recent refresh attempt regardless of outcome so a sustained
    // outage (cached == null) cannot retry faster than MIN_RETRY_INTERVAL against
    // the public Open-Meteo endpoint, which would risk a ban.
    private var lastAttemptAt: Instant? = null

    // Last-seen fix, updated by the location path and re-read on every clock tick.
    // The clock heartbeat lets weather re-evaluate refresh while parked indoors or
    // underground, where GPS stops emitting but the network is still reachable.
    private val latest = MutableStateFlow<Location?>(null)

    fun snapshotFlow(): Flow<WeatherSnapshot?> =
        merge(
            locationFlow.onEach { latest.value = it },
            // merge (not combine) keeps the location path working when clockFlow
            // is empty; combine would stall until the clock emitted at least once.
            clockFlow.map { latest.value },
        ).map { location -> refresh(location) ?: cached }
            .flowOn(Dispatchers.IO)

    private suspend fun refresh(location: Location?): WeatherSnapshot? {
        location ?: return null
        return mutex.withLock { refreshLocked(location) }
    }

    // Runs under [mutex]: the throttle read, network call, and cache write must
    // be one atomic step or two near-simultaneous ticks both pass shouldRefetch.
    private suspend fun refreshLocked(location: Location): WeatherSnapshot? {
        if (!shouldRefetch(location)) return cached
        // Record the attempt before the network call so a failing call still throttles
        // subsequent outage retries via MIN_RETRY_INTERVAL.
        lastAttemptAt = clock.instant()
        val response = api.forecast(location.latitude, location.longitude) ?: return cached
        val current = response.current ?: return cached
        cached =
            WeatherSnapshot(
                tempC = current.temperature_2m,
                // Fall back to the air temperature when "feels like" is absent so a
                // dropped secondary field never discards the usable reading.
                apparentTempC = current.apparent_temperature ?: current.temperature_2m,
                code = WeatherCode.fromWmo(current.weathercode),
                windKmh = current.windspeed_10m ?: 0.0,
                humidityPercent = current.relative_humidity_2m?.roundToInt(),
                uvIndex = current.uv_index,
                isDay = (current.is_day ?: 1) == 1,
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
        val snapshot =
            cached ?: return lastAttemptAt?.let { attempt ->
                // No successful cache yet: throttle outage retries so a sustained
                // failure does not fire once per GPS tick.
                Duration.between(attempt, clock.instant()).abs() >= MIN_RETRY_INTERVAL
            } ?: true
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

        // Floor between outage retries when no successful snapshot exists yet.
        val MIN_RETRY_INTERVAL: Duration = Duration.ofMinutes(1)
        const val REFRESH_DISTANCE_M = 5_000f
        const val HOURLY_SLICE_LENGTH = 5

        // Open-Meteo with timezone=auto returns naive local times (no offset suffix).
        val ISO_LOCAL: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
