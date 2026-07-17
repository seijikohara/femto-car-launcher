package io.github.seijikohara.femto.data.location

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import io.github.seijikohara.femto.data.common.toEnumOr
import io.github.seijikohara.femto.data.display.DisplayPreferences
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
 * Background ranging is opt-in (default off): it starts a location foreground
 * service so the trip distance / average keep accruing while another app (e.g. a
 * navigation app) is in front. Off by default because it runs continuous GPS and
 * posts an ongoing notification — a deliberate user choice, not a silent default.
 */
internal const val DEFAULT_BACKGROUND_RANGING_ENABLED = false

/**
 * Track recording is on by default: the history never leaves the device (which
 * also keeps it outside Play's Data-safety "collected" definition), the point
 * of recording is that data exists before the user thinks to ask for it, and a
 * Settings toggle plus a delete-history action keep the choice reversible.
 */
internal const val DEFAULT_TRACK_RECORDING_ENABLED = true

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1_000

/**
 * How long recorded track points are kept before pruning. A privacy knob, not
 * a storage one — even unlimited 1 Hz logging grows only ~0.3 MB per driving
 * hour. The 90-day default mirrors the most conservative comparable product
 * (Google Maps Timeline's on-device rolling window); UNLIMITED matches the
 * GPX-recorder genre where the user deletes manually.
 */
internal enum class TrackRetentionSetting(
    val maxAgeMs: Long?,
) {
    DAYS_30(30 * MILLIS_PER_DAY),
    DAYS_90(90 * MILLIS_PER_DAY),
    DAYS_365(365 * MILLIS_PER_DAY),
    UNLIMITED(null),
    ;

    companion object {
        val Default = DAYS_90
    }
}

/**
 * User-tunable location request parameters. The defaults ask for maximum
 * precision; Settings exposes each knob so the user can dial the request back
 * when the head-unit hardware struggles at chip-native rates.
 */
internal data class LocationSettings(
    val quality: LocationQualitySetting,
    val intervalMillis: Long,
    val minUpdateDistanceMeters: Int,
    val backgroundRangingEnabled: Boolean,
    val trackRecordingEnabled: Boolean,
    val trackRetention: TrackRetentionSetting,
) {
    companion object {
        val Default =
            LocationSettings(
                quality = LocationQualitySetting.HIGH_ACCURACY,
                intervalMillis = DEFAULT_LOCATION_INTERVAL_MS,
                minUpdateDistanceMeters = DEFAULT_LOCATION_MIN_DISTANCE_M,
                backgroundRangingEnabled = DEFAULT_BACKGROUND_RANGING_ENABLED,
                trackRecordingEnabled = DEFAULT_TRACK_RECORDING_ENABLED,
                trackRetention = TrackRetentionSetting.Default,
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

    suspend fun setBackgroundRangingEnabled(value: Boolean)

    suspend fun setTrackRecordingEnabled(value: Boolean)

    suspend fun setTrackRetention(value: TrackRetentionSetting)

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
                    backgroundRangingEnabled =
                        prefs[BACKGROUND_RANGING_KEY] ?: DEFAULT_BACKGROUND_RANGING_ENABLED,
                    trackRecordingEnabled =
                        prefs[TRACK_RECORDING_KEY] ?: DEFAULT_TRACK_RECORDING_ENABLED,
                    trackRetention = prefs[TRACK_RETENTION_KEY].toEnumOr(TrackRetentionSetting.Default),
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

    override suspend fun setBackgroundRangingEnabled(value: Boolean) {
        context.locationDataStore.editOrLog(TAG) { it[BACKGROUND_RANGING_KEY] = value }
    }

    override suspend fun setTrackRecordingEnabled(value: Boolean) {
        context.locationDataStore.editOrLog(TAG) { it[TRACK_RECORDING_KEY] = value }
    }

    override suspend fun setTrackRetention(value: TrackRetentionSetting) {
        context.locationDataStore.editOrLog(TAG) { it[TRACK_RETENTION_KEY] = value.name }
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
        val BACKGROUND_RANGING_KEY = booleanPreferencesKey("background_ranging_enabled")
        val TRACK_RECORDING_KEY = booleanPreferencesKey("track_recording_enabled")
        val TRACK_RETENTION_KEY = stringPreferencesKey("track_retention")
    }
}
