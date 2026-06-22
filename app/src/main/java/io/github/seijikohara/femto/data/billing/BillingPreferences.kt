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
}

internal class BillingPreferences(context: Context) : BillingEntitlementStore {
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

    private companion object {
        val UNLOCKED_KEY = booleanPreferencesKey("mapbox_unlocked")
        val VERIFIED_AT_KEY = longPreferencesKey("last_verified_at")
    }
}
