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
 * an install handed to the platform, the [offer] the last check found, the
 * newest build the update prompt has asked about, the build the user
 * skipped, and a build refused as signed with another key. A successful
 * install kills this process, so only the next start can reconcile the
 * pending install against the running version.
 */
internal data class UpdateSettings(
    val autoCheck: Boolean,
    val lastCheckAttemptAt: Long?,
    val pendingInstallVersionCode: Int?,
    /**
     * The newer build the updater offers, until an outcome withdraws it (a
     * check that finds nothing newer or no manifest, a download that fails
     * verification, a build refused as signed with another key) or the running
     * build catches up with it. An AI box cold-boots every drive, and the daily
     * gate keeps each new process from checking, so an offer kept only in
     * memory would be gone until the next day's check.
     */
    val offer: UpdateManifest?,
    /**
     * The newest versionCode the dashboard's update prompt has asked about, or
     * the Updates section has shown: the prompt never asks about it, or an
     * older build, again. Null until the first one. Kept apart from [offer],
     * which follows what the updater offers; this follows what the user has
     * already seen.
     */
    val promptedVersionCode: Int?,
    /**
     * The versionCode of the build the user chose to skip, or null. That build
     * raises no dock dot and no dashboard prompt, while Settings still names it
     * and still installs it on request. A newer build is offered as usual,
     * because it may carry the fix the user waited for: the updater clears the
     * record once it offers a newer build or the running build reaches it.
     */
    val skippedVersionCode: Int?,
    /**
     * The versionCode of a build the platform refused as signed with another
     * key, or null. Such a build never installs over this one, so later checks
     * offer only builds newer than it. Cleared once the running build reaches
     * it. CI signs every build with one key, so this guards a future key
     * change.
     */
    val refusedVersionCode: Int?,
) {
    companion object {
        val Default =
            UpdateSettings(
                autoCheck = DEFAULT_AUTO_CHECK,
                lastCheckAttemptAt = null,
                pendingInstallVersionCode = null,
                offer = null,
                promptedVersionCode = null,
                skippedVersionCode = null,
                refusedVersionCode = null,
            )
    }
}

/**
 * Whether the dashboard's update prompt has asked about [versionCode] already,
 * or the Updates section has shown it (see [UpdateSettings.promptedVersionCode]).
 * Build numbers only grow on a channel, so one record covers every build up
 * to it.
 */
internal fun UpdateSettings.promptedFor(versionCode: Int): Boolean =
    promptedVersionCode?.let { it >= versionCode } == true

/**
 * Whether the user skipped the build [versionCode] (see
 * [UpdateSettings.skippedVersionCode]). Only that very build: a newer one is
 * not skipped.
 */
internal fun UpdateSettings.skipped(versionCode: Int): Boolean = skippedVersionCode == versionCode

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

    /**
     * Record that the update prompt has asked about [versionCode], or the
     * Updates section has shown it. A code below the recorded one leaves the
     * record as it is.
     */
    suspend fun recordPrompted(versionCode: Int)

    /** Record the versionCode of the build the user skipped; null clears the record. */
    suspend fun setSkippedVersionCode(versionCode: Int?)

    /** Record the versionCode of a build refused as signed with another key; null clears the record. */
    suspend fun setRefusedVersionCode(versionCode: Int?)

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
                    promptedVersionCode = prefs[PROMPTED_KEY],
                    skippedVersionCode = prefs[SKIPPED_KEY],
                    refusedVersionCode = prefs[REFUSED_KEY],
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

    // Read and written in one edit, so two writers (the prompt's answer and the
    // Updates section's record of what it showed) cannot lower the record.
    override suspend fun recordPrompted(versionCode: Int) {
        context.updateDataStore.editOrLog(TAG) { prefs ->
            prefs[PROMPTED_KEY] = maxOf(versionCode, prefs[PROMPTED_KEY] ?: versionCode)
        }
    }

    override suspend fun setSkippedVersionCode(versionCode: Int?) {
        context.updateDataStore.editOrLog(TAG) { it.setOrRemove(SKIPPED_KEY, versionCode) }
    }

    override suspend fun setRefusedVersionCode(versionCode: Int?) {
        context.updateDataStore.editOrLog(TAG) { it.setOrRemove(REFUSED_KEY, versionCode) }
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
        val PROMPTED_KEY = intPreferencesKey("update_prompted_version_code")
        val SKIPPED_KEY = intPreferencesKey("update_skipped_version_code")
        val REFUSED_KEY = intPreferencesKey("update_refused_version_code")
    }
}

// A record this build cannot read (damaged, or written in another shape) is no
// offer, the same as none: the next check that finds one writes it afresh.
private fun storedOfferOrNull(json: String): UpdateManifest? =
    runCatching { parseUpdateManifest(json) }
        .onFailure { Log.w(TAG, "stored offer unreadable", it) }
        .getOrNull()
