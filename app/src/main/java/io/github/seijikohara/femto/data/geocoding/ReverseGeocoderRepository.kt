package io.github.seijikohara.femto.data.geocoding

import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
 * Reverse-geocode the current location through a pluggable [ReverseGeocoder] and
 * expose a readable [ShortAddress]. The default source is the on-device platform
 * geocoder; a self-hosted Nominatim-compatible host is substituted via
 * GEOCODER_BASE_URL.
 *
 * When the source is OSM-backed, the map surface already renders the
 * "© OpenStreetMap contributors" attribution that covers the geocoding data, so
 * no extra attribution UI is required here.
 */
internal class ReverseGeocoderRepository(
    private val locationFlow: Flow<Location?>,
    private val geocoder: ReverseGeocoder,
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
    private var lastResult: ResolvedFallback? = null
    private var lastRequestAtMs: Long? = null

    // Serialises resolve: the flow runs on the IO pool where overlapping
    // collections are possible, and the pacing gate's read-then-update pair
    // must be atomic or two callers can both pass the spacing check. The lock
    // also covers the LRU map, which is not safe under concurrent mutation.
    private val mutex = Mutex()

    fun addressFlow(): Flow<ShortAddress?> =
        locationFlow
            .distinctUntilChangedByBucket()
            .map { location -> location?.let { resolve(it) } }
            .flowOn(ioDispatcher)

    private suspend fun resolve(location: Location): ShortAddress? =
        mutex.withLock {
            val key = bucketKey(location.latitude, location.longitude)
            cachedAddressOrNull(key)?.let { return it }

            // Pace lookups: sustained 1 Hz traffic from a moving vehicle reads
            // as bulk use to any geocoding backend (a self-hosted Nominatim's
            // usage policy, or the platform backend's own throttle), so
            // unresolved buckets only reach the source once per pacing window. A
            // bucket inside the window reuses the last resolved address instead
            // of waiting — adjacent 100 m buckets share their locality-level
            // address, and skipping (rather than delaying) keeps the flow from
            // queueing stale fixes behind a timer. A null lastRequestAtMs means
            // no lookup has been issued yet, so the first fix resolves
            // immediately.
            val sinceLastRequestMs = lastRequestAtMs?.let { nowMs() - it }
            if (sinceLastRequestMs != null && sinceLastRequestMs < NETWORK_PACING_MS) {
                return fallbackFor(location)
            }
            // Stamped before the call on purpose: a failed lookup still spent
            // the backend's goodwill (and a dead source gains nothing from a
            // tight retry loop), so failures consume the pacing window too.
            lastRequestAtMs = nowMs()

            runCatching {
                geocoder.reverse(location.latitude, location.longitude)
            }.onFailure {
                // runCatching also traps cancellation; rethrow so a cancelled
                // collector propagates instead of being misread as a lookup
                // failure that falls back to lastResult.
                if (it is CancellationException) throw it
                Log.w(TAG, "reverse geocode resolve failed", it)
            }.getOrNull()
                ?.also {
                    cache[key] = CacheEntry(it, nowMs())
                    lastResult = ResolvedFallback(it, location)
                }
                ?: fallbackFor(location)
        }

    // Serve the last resolved address only while the new fix is still near
    // where that address was true. Beyond the bound (e.g. a long network
    // outage while driving) a stale address is worse than none, so the row
    // degrades to its placeholder instead. The entry is kept — not cleared —
    // so driving back into range restores it.
    private fun fallbackFor(location: Location): ShortAddress? =
        lastResult
            ?.takeIf { it.resolvedAt.distanceTo(location) <= FALLBACK_MAX_DISTANCE_M }
            ?.address

    // Return the cached address only while it is within the TTL window. A
    // stale entry is dropped so the next visit re-queries and can recover from
    // a low-quality first geocode.
    private fun cachedAddressOrNull(key: String): ShortAddress? =
        cache[key]?.let { entry ->
            if (nowMs() - entry.resolvedAtMs < TTL_MS) {
                entry.address
            } else {
                cache.remove(key)
                null
            }
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

    // The last successfully resolved address paired with the fix it was
    // resolved for, so the fallback can refuse to serve it once the vehicle
    // has moved far from where the address was true.
    private data class ResolvedFallback(
        val address: ShortAddress,
        val resolvedAt: Location,
    )

    // Internal (not private) so tests reference these bounds directly instead
    // of mirroring the values (AGENTS.md#ssot-dry).
    internal companion object {
        const val BUCKET_M = 100f

        // Minimum spacing between lookups. Kept generous on purpose: any
        // geocoding backend treats sustained high-rate traffic as bulk use, and
        // at motorway speed a 15 s window still refreshes the address every few
        // hundred metres — finer than the locality-level line the overlay
        // renders.
        const val NETWORK_PACING_MS = 15_000L

        // Drop the failure fallback once the fix is this far from where the
        // last address was resolved. Inside the bound a slightly stale
        // neighbouring address is still truthful at the displayed
        // granularity; beyond it the row shows its placeholder instead.
        const val FALLBACK_MAX_DISTANCE_M = 5_000f

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
