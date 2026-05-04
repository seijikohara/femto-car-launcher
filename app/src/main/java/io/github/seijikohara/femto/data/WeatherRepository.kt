package io.github.seijikohara.femto.data

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import java.time.Duration

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
        val current = api.currentWeather(location.latitude, location.longitude) ?: return cached
        cached =
            WeatherSnapshot(
                tempC = current.temperature,
                code = WeatherCode.fromWmo(current.weathercode),
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

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofMinutes(30)
        const val REFRESH_DISTANCE_M = 5_000f
    }
}
