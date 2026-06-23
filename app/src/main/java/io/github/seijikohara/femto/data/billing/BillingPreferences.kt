package io.github.seijikohara.femto.data.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "BillingPreferences"

private val Context.billingDataStore by preferencesDataStore(name = "billing")

internal interface BillingEntitlementStore {
    val cached: Flow<Entitlement>

    suspend fun cache(entitlement: Entitlement)

    // The force-unlock flag persists across builds but is only HONORED in DEBUG builds
    // (BillingRepository overlays it on the real entitlement in recompute(),
    // gated on BuildConfig.DEBUG). Keeping the pref key in all build types avoids a
    // separate DataStore instance and lets a release build silently ignore any leftover
    // value without reading it.
    // Exposed as a Flow — BillingRepository collects it in its process-lifetime scope
    // and maintains a hot MutableStateFlow mirror so the entitlement StateFlow stays
    // consistent without needing a combine coroutine per subscriber.
    val debugForceUnlocked: Flow<Boolean>

    suspend fun setDebugForceUnlocked(value: Boolean)
}

internal class BillingPreferences(
    context: Context,
) : BillingEntitlementStore {
    private val dataStore = context.billingDataStore

    override val cached: Flow<Entitlement> =
        dataStore.data.catchIoAsDefaults(TAG).map { prefs ->
            Entitlement(
                mapboxUnlocked = prefs[UNLOCKED_KEY] ?: false,
                // Absent key means no successful verify yet — preserve null so callers
                // can distinguish "never verified" from "verified at epoch 0".
                lastVerifiedAtMillis = prefs[VERIFIED_AT_KEY],
            )
        }

    override suspend fun cache(entitlement: Entitlement) =
        dataStore.editOrLog(TAG) { prefs ->
            prefs[UNLOCKED_KEY] = entitlement.mapboxUnlocked
            // Remove the key when null so a Locked cache call resets the timestamp and
            // the read path returns null (absent key) rather than a stale Long.
            if (entitlement.lastVerifiedAtMillis != null) {
                prefs[VERIFIED_AT_KEY] = entitlement.lastVerifiedAtMillis
            } else {
                prefs.remove(VERIFIED_AT_KEY)
            }
        }

    override val debugForceUnlocked: Flow<Boolean> =
        dataStore.data.catchIoAsDefaults(TAG).map { it[DEBUG_FORCE_UNLOCKED_KEY] ?: false }

    override suspend fun setDebugForceUnlocked(value: Boolean) =
        dataStore.editOrLog(TAG) { it[DEBUG_FORCE_UNLOCKED_KEY] = value }

    private companion object {
        val UNLOCKED_KEY = booleanPreferencesKey("mapbox_unlocked")
        val VERIFIED_AT_KEY = longPreferencesKey("last_verified_at")
        val DEBUG_FORCE_UNLOCKED_KEY = booleanPreferencesKey("debug_force_unlocked")
    }
}
