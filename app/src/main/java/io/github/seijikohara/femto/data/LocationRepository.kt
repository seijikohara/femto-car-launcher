package io.github.seijikohara.femto.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
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

private const val TAG = "LocationRepository"

internal class LocationRepository(
    private val context: Context,
    // Repository-scoped scope owns the single shared GPS subscription. The default keeps
    // production wiring trivial; tests inject their own scope to drive shareIn deterministically.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val locationManager: LocationManager = checkNotNull(context.getSystemService())

    // Cold per-collector flow: each terminal collection registers updates and seeds
    // getLastKnownLocation. Never expose this directly; it is the upstream for the shared flow.
    //
    // Seeding intent: the cached last-known fix is forwarded on subscribe even when it is
    // stale. A head unit may sit cold for hours, and the first live fix can lag the host's
    // ~30 s boot delay; emitting the stale fix first lets the map/weather/address panels show
    // the last position immediately rather than an empty screen, and the next live update
    // overwrites it. A stale fix is strictly better than no fix for an at-a-glance dashboard.
    //
    // Both GPS_PROVIDER and NETWORK_PROVIDER are registered so the launcher honors the
    // ACCESS_COARSE_LOCATION manifest contract: a coarse-only ("Approximate") grant makes
    // GPS_PROVIDER throw SecurityException while NETWORK_PROVIDER still delivers fixes at
    // degraded precision, and a device without network location still gets GPS. Each provider
    // is guarded by its own runCatching so a SecurityException or a missing provider on one
    // never disturbs the other. A per-provider failure is dropped (no null emission) so a
    // working provider is never blanked by the other's absence.
    @SuppressLint("MissingPermission") // Caller checks fine or coarse location before subscribing.
    private fun rawLocationFlow(): Flow<Location?> =
        callbackFlow {
            // One listener shared across both registrations; each fix is forwarded verbatim.
            val listener = LocationListenerCompat { location -> trySend(location) }

            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                runCatching {
                    locationManager.getLastKnownLocation(provider)
                }.onSuccess { trySend(it) }
                    .onFailure { logProviderFailure("getLastKnownLocation", provider, it) }

                runCatching {
                    LocationManagerCompat.requestLocationUpdates(
                        locationManager,
                        provider,
                        LocationRequestCompat.Builder(LOCATION_INTERVAL_MS).build(),
                        listener,
                        Looper.getMainLooper(),
                    )
                }.onFailure { logProviderFailure("register", provider, it) }
            }

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

    private fun logProviderFailure(
        stage: String,
        provider: String,
        error: Throwable,
    ) = when (error) {
        // A coarse-only grant makes GPS_PROVIDER throw SecurityException by design.
        is SecurityException -> Log.d(TAG, "$stage $provider denied", error)

        else -> Log.w(TAG, "$stage $provider failed", error)
    }

    private companion object {
        const val LOCATION_INTERVAL_MS = 1_000L
    }
}
