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
    // Read per refresh rather than captured at construction: Locationforecast
    // times are UTC, but the card shows local wall-clock times and groups days by
    // the local calendar, and the repository outlives timezone changes (a phone
    // mounted as car nav crosses borders) — a captured ZoneId would keep grouping
    // by the old zone until the process dies (mirrors ClockRepository).
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
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
        ).map { location -> refresh(location) }
            .flowOn(Dispatchers.IO)

    // The cached fallback is read inside [mutex] alongside refreshLocked, so a
    // concurrent writer can never race it — rather than reading cached unlocked
    // at the call site.
    private suspend fun refresh(location: Location?): WeatherSnapshot? =
        mutex.withLock { location?.let { refreshLocked(it) } ?: cached }

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
        // One read per refresh so the whole snapshot (sun times plus the hourly
        // and daily grouping below) uses a single consistent zone.
        val zone = zoneProvider()
        val now = clock.instant()
        val sun = SunCalculator.compute(location.latitude, location.longitude, now.atZone(zone).toLocalDate(), zone)
        cached =
            WeatherSnapshot(
                tempC = temp,
                code = WeatherCode.fromMetSymbol(symbol),
                windKmh = (instant.windSpeed ?: 0.0) * MS_TO_KMH,
                windDirectionDeg = instant.windFromDirection,
                humidityPercent = instant.relativeHumidity?.roundToInt(),
                // The hour ahead, from the 1-hour block only: a 6-hour block would
                // answer a different question (the chance somewhere in the next six
                // hours) under the same label. Absent on the far tail, so null-safe.
                //
                // The probability is Nordic-domain only — MET's global model omits it
                // entirely, so it stays null for most of the world and the card reads
                // the amount instead.
                precipitationProbabilityPercent =
                    first.data.next1Hours
                        ?.details
                        ?.probabilityOfPrecipitation
                        ?.roundToInt(),
                precipitationMm = first.data.next1Hours
                    ?.details
                    ?.precipitationAmount,
                uvIndex = instant.ultravioletIndexClearSky,
                // Day unless the symbol explicitly carries the _night suffix
                // (_day / _polartwilight / no suffix all read as day).
                isDay = symbol?.endsWith("_night") != true,
                sunrise = sun.sunrise,
                sunset = sun.sunset,
                hourly = hourlySliceFrom(forecast.properties.timeseries, zone),
                daily = dailyForecastsFrom(forecast.properties.timeseries, zone),
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
    private fun hourlySliceFrom(
        timeseries: List<MetForecast.Timeseries>,
        zone: ZoneId,
    ): List<HourlyForecast> =
        timeseries
            .asSequence()
            .filter { it.data.next1Hours != null }
            .mapNotNull { entry ->
                val time = parseInstant(entry.time)?.atZone(zone)?.toLocalTime() ?: return@mapNotNull null
                val temp = entry.data.instant.details.airTemperature ?: return@mapNotNull null
                val hour = entry.data.next1Hours
                HourlyForecast(
                    time = time,
                    tempC = temp,
                    code = WeatherCode.fromMetSymbol(hour?.summary?.symbolCode),
                    precipitationMm = hour?.details?.precipitationAmount,
                    precipitationProbabilityPercent =
                        hour
                            ?.details
                            ?.probabilityOfPrecipitation
                            ?.roundToInt(),
                )
                // take AFTER the mapping, so an entry dropped for a missing
                // temperature or unparseable time doesn't silently shorten the
                // 24 h window it was counted against.
            }.take(HOURLY_SLICE_LENGTH)
            .toList()

    // Aggregate the timeseries into FORECAST_DAYS local days: max/min of the
    // instant air temperatures, and a representative symbol from the entry nearest
    // local noon. MET gives instant points, not pre-summarised days.
    private fun dailyForecastsFrom(
        timeseries: List<MetForecast.Timeseries>,
        zone: ZoneId,
    ): List<DailyForecast> =
        timeseries
            .groupBy { parseInstant(it.time)?.atZone(zone)?.toLocalDate() }
            .entries
            .mapNotNull { (date, entries) ->
                date ?: return@mapNotNull null
                val temps = entries.mapNotNull { it.data.instant.details.airTemperature }
                if (temps.isEmpty()) return@mapNotNull null
                // Far-tail entries (6 h apart) can miss the day's real extremes
                // between samples; their 6-hour envelope carries the truth. Only
                // tail entries contribute it — see [tailSixHourDetails].
                val envelopeMax = tailSixHourDetails(entries).mapNotNull { it.airTemperatureMax }
                val envelopeMin = tailSixHourDetails(entries).mapNotNull { it.airTemperatureMin }
                DailyForecast(
                    date = date,
                    tempMaxC = (temps + envelopeMax).max(),
                    tempMinC = (temps + envelopeMin).min(),
                    code = WeatherCode.fromMetSymbol(representativeSymbol(entries, zone)),
                    precipitationProbabilityPercent = peakPrecipProbability(entries),
                )
            }.sortedBy { it.date }
            .take(FORECAST_DAYS)

    // The day's peak probability. Near-term hourly entries carry BOTH a 1-hour
    // and a 6-hour block; a 6-hour block starting at 19:00-23:00 reaches into
    // the next morning, so counting it would pin tomorrow's overnight rain on
    // today (a wrong-day badge on the 7-day list). Rule: an entry's 6-hour
    // block counts only when the entry has no 1-hour block — i.e. only on the
    // far-tail days, whose entries are themselves 6 h apart.
    private fun peakPrecipProbability(entries: List<MetForecast.Timeseries>): Int? =
        entries
            .mapNotNull { entry ->
                val hour = entry.data.next1Hours
                when {
                    hour != null -> {
                        hour.details?.probabilityOfPrecipitation
                    }

                    else -> {
                        entry.data.next6Hours
                            ?.details
                            ?.probabilityOfPrecipitation
                    }
                }
            }.maxOrNull()
            ?.roundToInt()

    // 6-hour details from entries WITHOUT a 1-hour block (the far tail). Near
    // entries' 6-hour blocks overlap the next local day and would leak its
    // weather backwards — the same wrong-day hazard as the probability rule.
    private fun tailSixHourDetails(entries: List<MetForecast.Timeseries>): List<MetForecast.PeriodDetails> =
        entries
            .filter { it.data.next1Hours == null }
            .mapNotNull { it.data.next6Hours?.details }

    private fun representativeSymbol(
        entries: List<MetForecast.Timeseries>,
        zone: ZoneId,
    ): String? =
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

        // The maximize panel draws a 24 h temperature curve and a 7-day outlook;
        // the compact card caps its own display shorter (see WeatherCard).
        const val FORECAST_DAYS = 7
        const val HOURLY_SLICE_LENGTH = 24
        const val NOON_HOUR = 12

        const val MS_TO_KMH = 3.6
    }
}
