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

private const val TAG = "PlaceLocator"

/** What a look-up of a place's text found. */
internal sealed interface PlaceLookup {
    data class Found(
        val latitude: Double,
        val longitude: Double,
    ) : PlaceLookup

    /** The geocoder answered and knows no such place. */
    data object NotFound : PlaceLookup

    /** The geocoder could not answer, as without a network. */
    data object Failed : PlaceLookup
}

/** Turns the text of a place into a position, for a route to typed text or a saved search. */
internal interface PlaceLocator {
    /** Whether the device has a geocoder at all; without one, text cannot become a route. */
    val available: Boolean

    suspend fun locate(text: String): PlaceLookup
}

/**
 * [PlaceLocator] on the platform [Geocoder], the same backend [PlatformReverseGeocoder] uses: the text goes to the
 * device's geocoder provider (Google's on a device with Google Play services), and a device without one reports
 * [available] false. The locale is read per request, as the reverse geocoder does.
 */
internal class PlatformPlaceLocator(
    context: Context,
    private val localeProvider: () -> Locale = Locale::getDefault,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaceLocator {
    private val appContext = context.applicationContext

    override val available: Boolean get() = Geocoder.isPresent()

    override suspend fun locate(text: String): PlaceLookup =
        if (!available) {
            PlaceLookup.Failed
        } else {
            withContext(ioDispatcher) {
                runCatching {
                    suspendCancellableCoroutine { continuation ->
                        // The minSdk-33 listener form; the blocking overload is deprecated from API 33.
                        Geocoder(appContext, localeProvider()).getFromLocationName(
                            text,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    continuation.resume(addresses.firstOrNull()?.toLookup() ?: PlaceLookup.NotFound)
                                }

                                override fun onError(errorMessage: String?) {
                                    Log.w(TAG, "geocode error: $errorMessage")
                                    continuation.resume(PlaceLookup.Failed)
                                }
                            },
                        )
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "geocode failed", error)
                    PlaceLookup.Failed
                }
            }
        }
}

private fun Address.toLookup(): PlaceLookup =
    if (hasLatitude() && hasLongitude()) PlaceLookup.Found(latitude, longitude) else PlaceLookup.NotFound
