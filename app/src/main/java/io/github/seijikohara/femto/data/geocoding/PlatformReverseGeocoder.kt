package io.github.seijikohara.femto.data.geocoding

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

private const val TAG = "PlatformReverseGeocoder"

/**
 * Reverse-geocode with the on-device Android [Geocoder]. This is the free,
 * ToS-free default: no public reverse-geocoding API can be shipped compliantly,
 * so the platform backend (Google's on GMS devices) is used where present and
 * degrades to no address where absent — non-GMS AI boxes commonly have no
 * backend, which [Geocoder.isPresent] reports up front.
 *
 * Follows the device locale (read per request via [localeProvider]) so no single
 * market is privileged (AGENTS.md, multi-region distribution).
 */
internal class PlatformReverseGeocoder(
    context: Context,
    // Read per request rather than captured at construction: the launcher is
    // multi-region and outlives locale changes (a phone mounted as car nav
    // crosses borders) — a captured locale would pin the geocoder's output
    // language to the old locale until the process dies (mirrors ClockRepository's
    // zoneProvider).
    private val localeProvider: () -> Locale = Locale::getDefault,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReverseGeocoder {
    private val appContext = context.applicationContext

    override suspend fun reverse(
        latitude: Double,
        longitude: Double,
    ): ShortAddress? {
        // No backend on this device (typical of non-GMS head units): the address
        // row stays empty rather than blocking on a call that can never resolve.
        if (!Geocoder.isPresent()) return null
        return withContext(ioDispatcher) {
            // Construct per request so the current locale (read now, not at
            // construction) governs the returned address language.
            val geocoder = Geocoder(appContext, localeProvider())
            runCatching {
                suspendCancellableCoroutine<Address?> { continuation ->
                    // The minSdk-33 async listener variant; the legacy blocking
                    // overload is deprecated on API 33+.
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                continuation.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                Log.w(TAG, "reverse geocode error: $errorMessage")
                                continuation.resume(null)
                            }
                        },
                    )
                }?.toShortAddressOrNull()
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "reverse geocode failed", it)
            }.getOrNull()
        }
    }
}

/**
 * Reduce a platform [Address] to the dashboard's [ShortAddress], or null when it
 * carries no usable place name. [ShortAddress.line] uses the locale-formatted
 * first address line the platform produces; [ShortAddress.locality] falls
 * through the city-level fields so the weather card still names a place when the
 * primary locality is absent.
 */
internal fun Address.toShortAddressOrNull(): ShortAddress? {
    val place = locality ?: subLocality ?: subAdminArea ?: adminArea ?: return null
    return ShortAddress(
        locality = place,
        region = adminArea?.takeIf { it != place },
        line = getAddressLine(0).orEmpty(),
    )
}
