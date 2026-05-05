package io.github.seijikohara.femto.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.getSystemService
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

internal class LocationRepository(
    private val context: Context,
) {
    private val locationManager: LocationManager = checkNotNull(context.getSystemService())

    @SuppressLint("MissingPermission") // Caller checks ACCESS_FINE_LOCATION before subscribing.
    fun locationFlow(): Flow<Location?> =
        callbackFlow {
            val listener = LocationListenerCompat { location -> trySend(location) }

            runCatching {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }.onSuccess { trySend(it) }

            runCatching {
                LocationManagerCompat.requestLocationUpdates(
                    locationManager,
                    LocationManager.GPS_PROVIDER,
                    LocationRequestCompat.Builder(LOCATION_INTERVAL_MS).build(),
                    listener,
                    Looper.getMainLooper(),
                )
            }.onFailure { trySend(null) }

            awaitClose { locationManager.removeUpdates(listener) }
        }.flowOn(Dispatchers.Main.immediate)

    private companion object {
        const val LOCATION_INTERVAL_MS = 1_000L
    }
}
