package io.github.seijikohara.femto.data.weather

import android.location.Location
import io.github.seijikohara.femto.data.clock.ClockTick
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
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

internal class WeatherRepository(
    private val api: MetNorwayApi,
    private val locationFlow: Flow<Location?>,
    private val clockFlow: Flow<ClockTick>,
    private val clock: Clock = Clock.systemUTC(),
    // Locationforecast times are UTC; the card shows local wall-clock times and
    // groups days by the local calendar. Injectable so tests pin a fixed zone.
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    // Serialises refresh: merge() lets the location and clock upstreams reach
    // refresh concurrently on the IO pool, and an unguarded check-fetch-write
    // sequence could double-fetch or bypass the outage throttle.
    private val mutex = Mutex()
    private var cached: WeatherSnapshot? = null
    private var lastFetchLocation: Location? = null

    // Tracks the most recent refresh attempt regardless of outcome so an outage
    // (with or without an older cached snapshot) cannot retry faster than
    // MIN_RETRY_INTERVAL against api.met.no, which risks a throttle/ban.
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
        // Record the attempt before the network call so a failing call still
        // throttles subsequent outage retries via MIN_RETRY_INTERVAL.
        lastAttemptAt = clock.instant()
        val forecast = api.forecast(location.latitude, location.longitude) ?: return cached
        val first = forecast.properties.timeseries.firstOrNull() ?: return cached
        val instant = first.data.instant.details
        val temp = instant.airTemperature ?: return cached
        val symbol = first.data.next1Hours
            ?.summary
            ?.symbolCode ?: first.data.next6Hours
            ?.summary
            ?.symbolCode
        val now = clock.instant()
        val sun = SunCalculator.compute(location.latitude, location.longitude, now.atZone(zone).toLocalDate(), zone)
        cached =
            WeatherSnapshot(
                tempC = temp,
                // MET provides no "feels like"; the air temperature stands in.
                apparentTempC = temp,
                code = WeatherCode.fromMetSymbol(symbol),
                windKmh = (instant.windSpeed ?: 0.0) * MS_TO_KMH,
                humidityPercent = instant.relativeHumidity?.roundToInt(),
                uvIndex = instant.ultravioletIndexClearSky,
                // Day unless the symbol explicitly carries the _night suffix
                // (_day / _polartwilight / no suffix all read as day).
                isDay = symbol?.endsWith("_night") != true,
                sunrise = sun.sunrise,
                sunset = sun.sunset,
                hourly = hourlySliceFrom(forecast.properties.timeseries),
                daily = dailyForecastsFrom(forecast.properties.timeseries),
                fetchedAt = now,
            )
        lastFetchLocation = location
        return cached
    }

    private fun shouldRefetch(location: Location): Boolean {
        // Attempt floor first, regardless of cache state. When it only guarded
        // the no-cache path, a STALE cache during an outage passed the age check
        // on every GPS tick (~1 Hz while moving) and hammered the endpoint — the
        // same storm the floor exists to prevent.
        val throttled =
            lastAttemptAt?.let { attempt ->
                Duration.between(attempt, clock.instant()).abs() < MIN_RETRY_INTERVAL
            } == true
        if (throttled) return false
        val snapshot = cached ?: return true
        val anchor = lastFetchLocation ?: return true
        val ageOk = Duration.between(snapshot.fetchedAt, clock.instant()).abs() < REFRESH_INTERVAL
        val nearOk = anchor.distanceTo(location) < REFRESH_DISTANCE_M
        return !(ageOk && nearOk)
    }

    // The first HOURLY_SLICE_LENGTH hourly-resolution entries (those carrying a
    // next_1_hours block) starting at the current hour — Locationforecast emits
    // them oldest-first from "now".
    private fun hourlySliceFrom(timeseries: List<MetForecast.Timeseries>): List<HourlyForecast> =
        timeseries
            .asSequence()
            .filter { it.data.next1Hours != null }
            .take(HOURLY_SLICE_LENGTH)
            .mapNotNull { entry ->
                val time = parseInstant(entry.time)?.atZone(zone)?.toLocalTime() ?: return@mapNotNull null
                val temp = entry.data.instant.details.airTemperature ?: return@mapNotNull null
                HourlyForecast(
                    time = time,
                    tempC = temp,
                    code = WeatherCode.fromMetSymbol(
                        entry.data.next1Hours
                            ?.summary
                            ?.symbolCode,
                    ),
                )
            }.toList()

    // Aggregate the timeseries into FORECAST_DAYS local days: max/min of the
    // instant air temperatures, and a representative symbol from the entry nearest
    // local noon. MET gives instant points, not pre-summarised days.
    private fun dailyForecastsFrom(timeseries: List<MetForecast.Timeseries>): List<DailyForecast> =
        timeseries
            .groupBy { parseInstant(it.time)?.atZone(zone)?.toLocalDate() }
            .entries
            .mapNotNull { (date, entries) ->
                date ?: return@mapNotNull null
                val temps = entries.mapNotNull { it.data.instant.details.airTemperature }
                if (temps.isEmpty()) return@mapNotNull null
                DailyForecast(
                    date = date,
                    tempMaxC = temps.max(),
                    tempMinC = temps.min(),
                    code = WeatherCode.fromMetSymbol(representativeSymbol(entries)),
                )
            }.sortedBy { it.date }
            .take(FORECAST_DAYS)

    private fun representativeSymbol(entries: List<MetForecast.Timeseries>): String? =
        entries
            .minByOrNull { abs((parseInstant(it.time)?.atZone(zone)?.hour ?: 0) - NOON_HOUR) }
            ?.let {
                it.data.next6Hours
                    ?.summary
                    ?.symbolCode
                    ?: it.data.next1Hours
                        ?.summary
                        ?.symbolCode
                    ?: it.data.next12Hours
                        ?.summary
                        ?.symbolCode
            }

    private fun parseInstant(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofMinutes(30)

        // Floor between refresh attempts — success or failure, with or without
        // a cached snapshot. Combined with If-Modified-Since this keeps api.met.no
        // traffic minimal (a refetch is usually a cheap 304).
        val MIN_RETRY_INTERVAL: Duration = Duration.ofMinutes(1)
        const val REFRESH_DISTANCE_M = 5_000f

        // Card layout: a fixed 5-day strip. The maximize panel shows a longer
        // hourly timeline, so the slice carries up to 12; the compact card caps
        // its own display at 5 (see WeatherCard.Forecast).
        const val FORECAST_DAYS = 5
        const val HOURLY_SLICE_LENGTH = 12
        const val NOON_HOUR = 12

        const val MS_TO_KMH = 3.6
    }
}
