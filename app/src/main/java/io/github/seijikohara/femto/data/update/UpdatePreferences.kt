package io.github.seijikohara.femto.data.update

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * that must outlive the process — the last check attempt, failed ones
 * included (the daily gate, and the UI's "last attempt"), the versionCode of
 * an install handed to the platform, and the [offer] the last check found. A
 * successful install kills this process, so only the next start can
 * reconcile that record against the running version.
 */
internal data class UpdateSettings(
    val autoCheck: Boolean,
    val lastCheckAttemptAt: Long?,
    val pendingInstallVersionCode: Int?,
    /**
     * The newer build a check found, until a check finds nothing newer or the
     * running build catches up with it. An AI box cold-boots every drive, and
     * the daily gate keeps each new process from checking, so an offer kept
     * only in memory would be gone until the next day's check.
     */
    val offer: UpdateManifest?,
) {
    companion object {
        val Default =
            UpdateSettings(
                autoCheck = DEFAULT_AUTO_CHECK,
                lastCheckAttemptAt = null,
                pendingInstallVersionCode = null,
                offer = null,
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

    /** Record the offer a check found; null clears the record. */
    suspend fun setOffer(manifest: UpdateManifest?)

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
                    offer = prefs[OFFER_KEY]?.let(::storedOfferOrNull),
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

    override suspend fun setOffer(manifest: UpdateManifest?) {
        context.updateDataStore.editOrLog(TAG) { it.setOrRemove(OFFER_KEY, manifest?.toJson()) }
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
        val OFFER_KEY = stringPreferencesKey("update_offer")
    }
}

// A record this build cannot read (damaged, or written in another shape) is no
// offer, the same as none: the next check that finds one writes it afresh.
private fun storedOfferOrNull(json: String): UpdateManifest? =
    runCatching { parseUpdateManifest(json) }
        .onFailure { Log.w(TAG, "stored offer unreadable", it) }
        .getOrNull()
