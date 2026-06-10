package io.github.seijikohara.femto.data

import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToLong

private const val TAG = "ReverseGeocoder"

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
    //
    // Access-order LinkedHashMap so the map doubles as a bounded LRU: the
    // eldest (least-recently-read-or-written) bucket is evicted once the map
    // exceeds MAX_ENTRIES. This caps the cache at a few hundred buckets so a
    // long drive cannot grow it without bound. A pure-JVM LinkedHashMap is
    // preferred over android.util.LruCache so the cache needs no Robolectric
    // shadow to exercise.
    private val cache =
        object : LinkedHashMap<String, CacheEntry>(MAX_ENTRIES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, CacheEntry>): Boolean = size > MAX_ENTRIES
        }
    private var lastResult: ShortAddress? = null
    private var lastRequestAtMs = 0L

    // Serialises resolve: the flow runs on the IO pool where overlapping
    // collections are possible, and the throttle's read-then-update pair must
    // be atomic or two callers can both pass the spacing check and violate
    // Nominatim's 1 request/second policy. The lock also covers the LRU map,
    // which is not safe under concurrent mutation.
    private val mutex = Mutex()

    fun addressFlow(): Flow<ShortAddress?> =
        locationFlow
            .distinctUntilChangedByBucket()
            .map { location -> location?.let { resolve(it) } }
            .flowOn(ioDispatcher)

    private suspend fun resolve(location: Location): ShortAddress? =
        mutex.withLock {
            val key = bucketKey(location.latitude, location.longitude)
            cachedAddress(key)?.let { return it }

            // Nominatim's usage policy caps callers at 1 request per second; the
            // 100 m bucket already collapses most calls, and this spacing guards
            // the remaining boundary crossings. Holding the lock across the delay
            // and the request keeps the spacing global, not per-caller.
            throttle()

            runCatching {
                api.reverse(location.latitude, location.longitude)?.address?.let {
                    AddressComposer.composeAddress(it)
                }
            }.onFailure {
                // runCatching also traps cancellation; rethrow so a cancelled
                // collector propagates instead of being misread as a lookup
                // failure that falls back to lastResult.
                if (it is CancellationException) throw it
                Log.w(TAG, "reverse geocode resolve failed", it)
            }.getOrNull()
                ?.also {
                    cache[key] = CacheEntry(it, nowMs())
                    lastResult = it
                }
                ?: lastResult
        }

    // Return the cached address only while it is within the TTL window. A
    // stale entry is dropped so the next visit re-queries and can recover from
    // a low-quality first geocode.
    private fun cachedAddress(key: String): ShortAddress? =
        cache[key]?.let { entry ->
            if (nowMs() - entry.resolvedAtMs < TTL_MS) {
                entry.address
            } else {
                cache.remove(key)
                null
            }
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

    // Cached address paired with the wall-clock time it was resolved, so the
    // TTL check can age out a stale or low-quality bucket.
    private data class CacheEntry(
        val address: ShortAddress,
        val resolvedAtMs: Long,
    )

    private companion object {
        const val BUCKET_M = 100f
        const val MIN_REQUEST_SPACING_MS = 1_000L

        // Cap the per-bucket cache so a long drive cannot grow it without
        // bound; ~256 buckets covers a large daily travel envelope while
        // bounding the worst-case memory footprint.
        const val MAX_ENTRIES = 256
        const val LOAD_FACTOR = 0.75f

        // Re-resolve a bucket after a day so a stale or low-quality first
        // geocode recovers without restarting the process.
        const val TTL_MS = 24L * 60L * 60L * 1_000L
    }
}
