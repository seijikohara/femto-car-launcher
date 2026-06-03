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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

internal class LocationRepository(
    private val context: Context,
    // Repository-scoped scope owns the single shared GPS subscription. The default keeps
    // production wiring trivial; tests inject their own scope to drive shareIn deterministically.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val locationManager: LocationManager = checkNotNull(context.getSystemService())

    // Cold per-collector flow: each terminal collection registers its own GPS updates and
    // getLastKnownLocation. Never expose this directly; it is the upstream for the shared flow.
    @SuppressLint("MissingPermission") // Caller checks ACCESS_FINE_LOCATION before subscribing.
    private fun rawLocationFlow(): Flow<Location?> =
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

    // Single hot fan-out of the cold raw flow. On an always-on head unit the four consumers
    // (ViewModel combine, ReverseGeocoder, Weather, Trip) collect the same instance, so they
    // share one platform GPS registration + one getLastKnownLocation instead of four each.
    // WhileSubscribed(5_000) gates that single registration: it starts on the first collector,
    // and stops ~5s after the last collector leaves (the grace window avoids tearing down and
    // re-registering GPS across brief recomposition / config-change gaps). replay = 1 hands a
    // late subscriber the most recent fix immediately rather than waiting for the next update.
    private val shared: SharedFlow<Location?> =
        rawLocationFlow().shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // The repository factory already builds one LocationRepository and passes the single flow
    // instance to all consumers, so returning the shared flow makes that sharing real.
    fun locationFlow(): Flow<Location?> = shared

    private companion object {
        const val LOCATION_INTERVAL_MS = 1_000L
    }
}
