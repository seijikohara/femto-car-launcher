package io.github.seijikohara.femto.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import io.github.seijikohara.femto.data.common.setOrRemove
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "UpdatePreferences"

/**
 * The daily check is on by default: a sideloaded app has no store to deliver
 * its fixes, so an update the user never hears of is never installed. A check
 * reads one small manifest; a download always waits for the user.
 */
internal const val DEFAULT_AUTO_CHECK = true

/**
 * What the updater persists: the user's auto-check choice, plus bookkeeping
 * that must outlive the process — the last check attempt (the daily gate, and
 * "last checked" for the UI) and the versionCode of an install handed to the
 * platform. A successful install kills this process, so only the next start
 * can reconcile that record against the running version.
 */
internal data class UpdateSettings(
    val autoCheck: Boolean,
    val lastCheckAttemptAt: Long?,
    val pendingInstallVersionCode: Int?,
) {
    companion object {
        val Default =
            UpdateSettings(
                autoCheck = DEFAULT_AUTO_CHECK,
                lastCheckAttemptAt = null,
                pendingInstallVersionCode = null,
            )
    }
}

/**
 * Read/write surface for [UpdateSettings]. [UpdatePreferences] is the
 * DataStore-backed production implementation; tests substitute an in-memory
 * fake so the repository can be exercised without real DataStore IO.
 */
internal interface UpdateSettingsStore {
    val settings: Flow<UpdateSettings>

    suspend fun setAutoCheck(value: Boolean)

    suspend fun setLastCheckAttemptAt(epochMs: Long)

    /** Record the versionCode of an install handed to the platform; null clears the record. */
    suspend fun setPendingInstallVersionCode(versionCode: Int?)

    /** Restore the auto-check setting to its default; the bookkeeping is not a setting and stays. */
    suspend fun resetToDefaults()
}

// Internal (not private): UpdatePreferencesTest clears the raw store between
// tests, because resetToDefaults() deliberately keeps the bookkeeping keys.
internal val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_preferences")

/** DataStore-backed accessor for [UpdateSettings]. Modelled on `LocationPreferences`. */
internal class UpdatePreferences(
    private val context: Context,
) : UpdateSettingsStore {
    override val settings: Flow<UpdateSettings> =
        context.updateDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                UpdateSettings(
                    autoCheck = prefs[AUTO_CHECK_KEY] ?: DEFAULT_AUTO_CHECK,
                    lastCheckAttemptAt = prefs[LAST_CHECK_ATTEMPT_KEY],
                    pendingInstallVersionCode = prefs[PENDING_INSTALL_KEY],
                )
            }

    override suspend fun setAutoCheck(value: Boolean) {
        context.updateDataStore.editOrLog(TAG) { it[AUTO_CHECK_KEY] = value }
    }

    override suspend fun setLastCheckAttemptAt(epochMs: Long) {
        context.updateDataStore.editOrLog(TAG) { it[LAST_CHECK_ATTEMPT_KEY] = epochMs }
    }

    override suspend fun setPendingInstallVersionCode(versionCode: Int?) {
        context.updateDataStore.editOrLog(TAG) { it.setOrRemove(PENDING_INSTALL_KEY, versionCode) }
    }

    // Only the setting's key: clearing the file would also drop a pending-install
    // record, and the successor of that install would then never announce itself.
    override suspend fun resetToDefaults() {
        context.updateDataStore.editOrLog(TAG) { it.remove(AUTO_CHECK_KEY) }
    }

    private companion object {
        val AUTO_CHECK_KEY = booleanPreferencesKey("update_auto_check")
        val LAST_CHECK_ATTEMPT_KEY = longPreferencesKey("update_last_check_attempt_at")
        val PENDING_INSTALL_KEY = intPreferencesKey("update_pending_install_version_code")
    }
}
