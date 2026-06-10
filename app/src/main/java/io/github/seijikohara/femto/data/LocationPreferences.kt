package io.github.seijikohara.femto.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "LocationPreferences"

/**
 * GNSS request quality, mirroring `LocationRequestCompat.QUALITY_*`. Defaults to
 * [HIGH_ACCURACY]: the head unit runs on vehicle power, so there is no battery
 * budget to trade away; the lower tiers exist for hardware that struggles to keep
 * up with a high-accuracy duty cycle.
 */
internal enum class LocationQualitySetting { HIGH_ACCURACY, BALANCED, LOW_POWER }

/**
 * Default location-request interval (ms). 250 ms asks for fixes faster than the
 * common 1 Hz GNSS cadence so chips capable of more deliver more; a 1 Hz-capped
 * chip simply keeps emitting at 1 Hz.
 */
internal const val DEFAULT_LOCATION_INTERVAL_MS = 250L

/** Default minimum distance (m) between fixes; 0 delivers every fix. */
internal const val DEFAULT_LOCATION_MIN_DISTANCE_M = 0

/**
 * User-tunable location request parameters. The defaults ask for maximum
 * precision; Settings exposes each knob so the user can dial the request back
 * when the head-unit hardware struggles at chip-native rates.
 */
internal data class LocationSettings(
    val quality: LocationQualitySetting,
    val intervalMillis: Long,
    val minUpdateDistanceMeters: Int,
) {
    companion object {
        val Default =
            LocationSettings(
                quality = LocationQualitySetting.HIGH_ACCURACY,
                intervalMillis = DEFAULT_LOCATION_INTERVAL_MS,
                minUpdateDistanceMeters = DEFAULT_LOCATION_MIN_DISTANCE_M,
            )
    }
}

/**
 * Read/write surface for [LocationSettings]. [LocationPreferences] is the
 * DataStore-backed production implementation; tests substitute an in-memory fake
 * so consumers can be exercised without real DataStore IO.
 */
internal interface LocationSettingsStore {
    val settings: Flow<LocationSettings>

    suspend fun setQuality(value: LocationQualitySetting)

    suspend fun setIntervalMillis(value: Long)

    suspend fun setMinUpdateDistanceMeters(value: Int)

    /** Restore every location setting to [LocationSettings.Default]. */
    suspend fun resetToDefaults()
}

private val Context.locationDataStore: DataStore<Preferences> by preferencesDataStore(name = "location_preferences")

/** DataStore-backed accessor for [LocationSettings]. Modelled on [DisplayPreferences]. */
internal class LocationPreferences(
    private val context: Context,
) : LocationSettingsStore {
    override val settings: Flow<LocationSettings> =
        context.locationDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                LocationSettings(
                    quality = prefs[QUALITY_KEY].toEnumOr(LocationQualitySetting.HIGH_ACCURACY),
                    intervalMillis = prefs[INTERVAL_KEY] ?: DEFAULT_LOCATION_INTERVAL_MS,
                    minUpdateDistanceMeters = prefs[MIN_DISTANCE_KEY] ?: DEFAULT_LOCATION_MIN_DISTANCE_M,
                )
            }

    override suspend fun setQuality(value: LocationQualitySetting) {
        context.locationDataStore.editOrLog(TAG) { it[QUALITY_KEY] = value.name }
    }

    override suspend fun setIntervalMillis(value: Long) {
        context.locationDataStore.editOrLog(TAG) { it[INTERVAL_KEY] = value }
    }

    override suspend fun setMinUpdateDistanceMeters(value: Int) {
        context.locationDataStore.editOrLog(TAG) { it[MIN_DISTANCE_KEY] = value }
    }

    // Clearing every key makes the read path fall back to its per-field defaults,
    // which are kept identical to LocationSettings.Default — so a reset restores
    // the defaults without duplicating the default literals here.
    override suspend fun resetToDefaults() {
        context.locationDataStore.editOrLog(TAG) { it.clear() }
    }

    private companion object {
        val QUALITY_KEY = stringPreferencesKey("location_quality")
        val INTERVAL_KEY = longPreferencesKey("location_interval_ms")
        val MIN_DISTANCE_KEY = intPreferencesKey("location_min_distance_m")
    }
}
