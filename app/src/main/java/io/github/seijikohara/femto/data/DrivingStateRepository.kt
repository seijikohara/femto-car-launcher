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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/**
 * Driving-state signal source.
 *
 * The single source of truth for the driving-lockout policy is the
 * `gate-driving-visible-feature` skill under `.claude/skills/`. This
 * class is the runtime that satisfies the "real signal" requirement
 * the skill describes.
 *
 * Strategy:
 * - Subscribe to [LocationManager.GPS_PROVIDER] at 1 Hz. GPS is the
 *   canonical source of vehicle speed; the network and fused
 *   providers are deliberately omitted so a missing GPS chip on a
 *   given AI Box keeps the launcher in the safe (locked) state
 *   instead of spuriously unlocking on a Wi-Fi fix.
 * - Locked when the most recent fix reports `speed >=`
 *   [UNLOCK_SPEED_M_PER_S], or when no fix has arrived yet.
 * - Caller is responsible for checking
 *   `Manifest.permission.ACCESS_FINE_LOCATION` before subscribing;
 *   subscribing without the permission throws.
 */
class DrivingStateRepository(
    private val context: Context,
) {
    private val locationManager: LocationManager = checkNotNull(context.getSystemService())

    /** Emits `true` when the launcher should render the locked surface. */
    fun lockedFlow(): Flow<Boolean> =
        locationFlow().map { location ->
            location == null || location.speed >= UNLOCK_SPEED_M_PER_S
        }

    @SuppressLint("MissingPermission") // Caller checks ACCESS_FINE_LOCATION before subscribing.
    private fun locationFlow(): Flow<Location?> =
        callbackFlow {
            val listener = LocationListenerCompat { location -> trySend(location) }

            // Seed with the most recent cached fix so the gate has a value
            // before the first real update arrives.
            runCatching {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }.onSuccess { trySend(it) }

            // Subscribe; if the provider is unavailable on this device,
            // emit null so the lockedFlow consumer falls back to the safe
            // default.
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
        }

    private companion object {
        const val LOCATION_INTERVAL_MS = 1_000L
        const val UNLOCK_SPEED_KMH = 5f
        const val UNLOCK_SPEED_M_PER_S = UNLOCK_SPEED_KMH * 1000f / 3600f
    }
}
