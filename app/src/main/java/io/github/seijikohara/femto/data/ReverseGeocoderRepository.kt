package io.github.seijikohara.femto.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

internal class ReverseGeocoderRepository(
    context: Context,
    private val locationFlow: Flow<Location?>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val geocoder: Geocoder = Geocoder(context, Locale.getDefault()),
) {
    fun addressFlow(): Flow<ShortAddress?> =
        locationFlow
            .distinctUntilChangedByBucket()
            .map { location -> location?.let { resolve(it) } }
            .flowOn(ioDispatcher)

    private suspend fun resolve(location: Location): ShortAddress? =
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            requestAddresses(location)
                .firstOrNull()
                ?.let { ShortAddress(locality = it.locality.orEmpty(), region = it.adminArea) }
                ?.takeIf { it.locality.isNotEmpty() }
        }.getOrNull()

    @Suppress("DEPRECATION")
    private suspend fun requestAddresses(location: Location): List<android.location.Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                    cont.resume(addresses)
                }
            }
        } else {
            geocoder.getFromLocation(location.latitude, location.longitude, 1) ?: emptyList()
        }

    private fun Flow<Location?>.distinctUntilChangedByBucket(): Flow<Location?> =
        distinctUntilChanged { old, new ->
            (old == null && new == null) ||
                (old != null && new != null && old.distanceTo(new) < BUCKET_M)
        }

    private companion object {
        const val BUCKET_M = 100f
    }
}
