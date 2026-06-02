package io.github.seijikohara.femto.data

import android.location.Location
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.math.roundToLong

/**
 * Reverse-geocode the current location through OSM Nominatim and expose a
 * readable [ShortAddress].
 *
 * The OSM geocoding data is licensed under "© OpenStreetMap contributors";
 * the map surface already renders the OSM/MapLibre attribution that covers
 * this data, so no extra attribution UI is required here.
 */
internal class ReverseGeocoderRepository(
    private val locationFlow: Flow<Location?>,
    private val api: NominatimApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Injectable clock so the request throttle is deterministic under a test
    // dispatcher's virtual time. Production reads the wall clock.
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    // Per-bucket cache keyed by the 100 m bucket (lat/lon rounded to 3
    // decimals). A revisited bucket returns the cached address without a
    // network call, and a failed lookup falls back to the most recent value.
    private val cache = mutableMapOf<String, ShortAddress>()
    private var lastResult: ShortAddress? = null
    private var lastRequestAtMs = 0L

    fun addressFlow(): Flow<ShortAddress?> =
        locationFlow
            .distinctUntilChangedByBucket()
            .map { location -> location?.let { resolve(it) } }
            .flowOn(ioDispatcher)

    private suspend fun resolve(location: Location): ShortAddress? {
        val key = bucketKey(location.latitude, location.longitude)
        cache[key]?.let { return it }

        // Nominatim's usage policy caps callers at 1 request per second; the
        // 100 m bucket already collapses most calls, and this spacing guards
        // the remaining boundary crossings.
        throttle()

        return runCatching {
            api.reverse(location.latitude, location.longitude)?.address?.let {
                AddressComposer.composeAddress(it)
            }
        }.getOrNull()
            ?.also {
                cache[key] = it
                lastResult = it
            }
            ?: lastResult
    }

    private suspend fun throttle() {
        val elapsed = nowMs() - lastRequestAtMs
        if (elapsed < MIN_REQUEST_SPACING_MS) delay(MIN_REQUEST_SPACING_MS - elapsed)
        lastRequestAtMs = nowMs()
    }

    private fun bucketKey(
        lat: Double,
        lon: Double,
    ): String = "${(lat * 1000).roundToLong()}:${(lon * 1000).roundToLong()}"

    private fun Flow<Location?>.distinctUntilChangedByBucket(): Flow<Location?> =
        distinctUntilChanged { old, new ->
            (old == null && new == null) ||
                (old != null && new != null && old.distanceTo(new) < BUCKET_M)
        }

    private companion object {
        const val BUCKET_M = 100f
        const val MIN_REQUEST_SPACING_MS = 1_000L
    }
}
