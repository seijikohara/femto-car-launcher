package io.github.seijikohara.femto.data.location

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val TAG = "TripStatePreferences"

/**
 * Trip accumulator snapshot persisted across process restarts.
 *
 * Only the fields that stay meaningful in a new process are stored: the
 * running totals, the trip-start wall-clock time, the wall-clock time of the
 * last accepted fix (the anchor for the parked-gap auto-reset, which must span
 * a reboot), and the trip counter. The GPS anchor and the current speed are
 * deliberately absent — the anchor's `elapsedRealtimeNanos` is boot-relative
 * (worthless after a reboot) and the speed re-establishes from the first live
 * fix.
 */
internal data class PersistedTrip(
    val totalMeters: Double,
    val totalSeconds: Double,
    val startedAtEpochMs: Long?,
    val lastFixEpochMs: Long?,
    val tripId: Long,
) {
    companion object {
        val Initial =
            PersistedTrip(
                totalMeters = 0.0,
                totalSeconds = 0.0,
                startedAtEpochMs = null,
                lastFixEpochMs = null,
                tripId = 0L,
            )
    }
}

/**
 * Read/write surface for [PersistedTrip]. [TripStatePreferences] is the
 * DataStore-backed production implementation; tests substitute an in-memory
 * fake so [TripRepository] can be exercised without real DataStore IO.
 */
internal interface TripStateStore {
    suspend fun read(): PersistedTrip

    suspend fun write(value: PersistedTrip)
}

// Deliberately its own DataStore file, NOT location_preferences: the Settings
// section reset clears that store wholesale, and resetting location *settings*
// must never wipe the running odometer.
private val Context.tripStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "trip_state")

// A total the odometer can never legitimately hold. The store is written
// through on every accepted fix, so a poisoned value outlives the fix that
// produced it: a pre-#351 build could accrue distance across a NaN coordinate,
// and the restored NaN then throws out of the hero row's roundToInt() on every
// launch — with the reset control behind the crash. Restore 0 instead.
// Internal so the guard is JVM-unit-testable without real DataStore IO.
internal fun Double?.orZeroWhenUnusable(): Double = this?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

// A null optional field is an absent key, not a stored sentinel, so the read
// path's plain `prefs[KEY]` yields null for it.
private fun MutablePreferences.setOrRemove(
    key: Preferences.Key<Long>,
    value: Long?,
) = when (value) {
    null -> remove(key)
    else -> set(key, value)
}

/** DataStore-backed accessor for [PersistedTrip]. */
internal class TripStatePreferences(
    private val context: Context,
) : TripStateStore {
    override suspend fun read(): PersistedTrip =
        context.tripStateDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                PersistedTrip(
                    totalMeters = prefs[TOTAL_METERS_KEY].orZeroWhenUnusable(),
                    totalSeconds = prefs[TOTAL_SECONDS_KEY].orZeroWhenUnusable(),
                    startedAtEpochMs = prefs[STARTED_AT_KEY],
                    lastFixEpochMs = prefs[LAST_FIX_KEY],
                    tripId = prefs[TRIP_ID_KEY] ?: 0L,
                )
            }.first()

    override suspend fun write(value: PersistedTrip) {
        context.tripStateDataStore.editOrLog(TAG) { prefs ->
            prefs[TOTAL_METERS_KEY] = value.totalMeters
            prefs[TOTAL_SECONDS_KEY] = value.totalSeconds
            prefs.setOrRemove(STARTED_AT_KEY, value.startedAtEpochMs)
            prefs.setOrRemove(LAST_FIX_KEY, value.lastFixEpochMs)
            prefs[TRIP_ID_KEY] = value.tripId
        }
    }

    private companion object {
        val TOTAL_METERS_KEY = doublePreferencesKey("trip_total_meters")
        val TOTAL_SECONDS_KEY = doublePreferencesKey("trip_total_seconds")
        val STARTED_AT_KEY = longPreferencesKey("trip_started_at_epoch_ms")
        val LAST_FIX_KEY = longPreferencesKey("trip_last_fix_epoch_ms")
        val TRIP_ID_KEY = longPreferencesKey("trip_id")
    }
}
