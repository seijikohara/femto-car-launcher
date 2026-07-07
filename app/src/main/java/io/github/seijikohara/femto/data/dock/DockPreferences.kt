package io.github.seijikohara.femto.data.dock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.enums.enumEntries

/**
 * Stable identity for each dock nav destination, independent of its rendered
 * position. The declaration order is the factory default order (today's
 * Phone / Apps / Music / Navigation / Browser / Assistant / Settings dock) —
 * [DashboardDock][io.github.seijikohara.femto.ui.home.components.DashboardDock]
 * maps each id to its icon / label / action.
 */
internal enum class DockNavId { PHONE, APPS, MUSIC, NAVIGATION, BROWSER, ASSISTANT, SETTINGS }

/**
 * Stable identity for each dock status-cluster indicator, independent of its
 * rendered position. The declaration order is the factory default order
 * (today's cellular / Wi-Fi / Bluetooth / GPS / battery cluster) —
 * [StatusCluster][io.github.seijikohara.femto.ui.home.components.StatusCluster]
 * maps each id to its rendered icon(s).
 */
internal enum class DockStatusId { CELLULAR, WIFI, BLUETOOTH, GPS, BATTERY }

// A dock-id name can never contain a newline, so it is a safe join separator
// for a persisted order list (mirrors DrawerPreferences' PIN_SEPARATOR).
private const val ORDER_SEPARATOR = "\n"

/**
 * Resolve a persisted, newline-joined order string into the full ordered list
 * of [T]: entries named in [persisted] are kept in that order (a name that no
 * longer matches any [T] entry — a removed / renamed id — is silently
 * dropped), then every [T] entry absent from that list (added to the enum
 * since, or [persisted] itself null/blank) is appended in [T]'s own
 * declaration order. The read path therefore never loses or duplicates an
 * entry across an enum change, and a fresh / cleared store resolves to
 * exactly [T]'s declared order — today's factory default.
 */
internal inline fun <reified T : Enum<T>> resolveDockOrder(persisted: String?): List<T> {
    val all = enumEntries<T>()
    val kept =
        persisted
            ?.split(ORDER_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { name -> all.firstOrNull { it.name == name } }
            ?.distinct()
            ?: emptyList()
    return kept + all.filterNot { it in kept }
}

/**
 * Resolve a persisted name set into a [T] set, dropping any name that no
 * longer matches a [T] entry (a removed / renamed id).
 */
internal inline fun <reified T : Enum<T>> resolveDockHidden(persisted: Set<String>?): Set<T> =
    persisted
        ?.mapNotNull { name -> enumEntries<T>().firstOrNull { it.name == name } }
        ?.toSet()
        ?: emptySet()

private val Context.dockDataStore: DataStore<Preferences> by preferencesDataStore(name = "dock_preferences")

private const val TAG = "DockPreferences"

/**
 * Read/write surface for the dock's nav-button and status-indicator layout:
 * each set's order plus which ids are hidden. [DockPreferences] is the
 * DataStore-backed production implementation, modelled on [DrawerPreferences]
 * [io.github.seijikohara.femto.data.apps.DrawerPreferences].
 */
internal interface DockSettingsStore {
    val navOrder: Flow<List<DockNavId>>
    val navHidden: Flow<Set<DockNavId>>
    val statusOrder: Flow<List<DockStatusId>>
    val statusHidden: Flow<Set<DockStatusId>>

    /** Replace the nav order wholesale (a future drag-reorder commit). */
    suspend fun setNavOrder(value: List<DockNavId>)

    /** Hide [id] if currently shown, show it again if currently hidden. */
    suspend fun toggleNavHidden(id: DockNavId)

    /** Replace the status-indicator order wholesale. */
    suspend fun setStatusOrder(value: List<DockStatusId>)

    /** Hide [id] if currently shown, show it again if currently hidden. */
    suspend fun toggleStatusHidden(id: DockStatusId)

    /** Restore the nav and status order/hidden sets to their factory defaults. */
    suspend fun resetToDefaults()
}

internal class DockPreferences(
    private val context: Context,
) : DockSettingsStore {
    override val navOrder: Flow<List<DockNavId>> =
        context.dockDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> resolveDockOrder(prefs[NAV_ORDER_KEY]) }

    override val navHidden: Flow<Set<DockNavId>> =
        context.dockDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> resolveDockHidden(prefs[NAV_HIDDEN_KEY]) }

    override val statusOrder: Flow<List<DockStatusId>> =
        context.dockDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> resolveDockOrder(prefs[STATUS_ORDER_KEY]) }

    override val statusHidden: Flow<Set<DockStatusId>> =
        context.dockDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> resolveDockHidden(prefs[STATUS_HIDDEN_KEY]) }

    override suspend fun setNavOrder(value: List<DockNavId>) {
        context.dockDataStore.editOrLog(
            TAG,
        ) { it[NAV_ORDER_KEY] = value.joinToString(ORDER_SEPARATOR) { id -> id.name } }
    }

    override suspend fun toggleNavHidden(id: DockNavId) {
        context.dockDataStore.editOrLog(TAG) { prefs ->
            val current = resolveDockHidden<DockNavId>(prefs[NAV_HIDDEN_KEY])
            val updated = if (id in current) current - id else current + id
            prefs[NAV_HIDDEN_KEY] = updated.mapTo(mutableSetOf()) { it.name }
        }
    }

    override suspend fun setStatusOrder(value: List<DockStatusId>) {
        context.dockDataStore.editOrLog(TAG) {
            it[STATUS_ORDER_KEY] = value.joinToString(ORDER_SEPARATOR) { id -> id.name }
        }
    }

    override suspend fun toggleStatusHidden(id: DockStatusId) {
        context.dockDataStore.editOrLog(TAG) { prefs ->
            val current = resolveDockHidden<DockStatusId>(prefs[STATUS_HIDDEN_KEY])
            val updated = if (id in current) current - id else current + id
            prefs[STATUS_HIDDEN_KEY] = updated.mapTo(mutableSetOf()) { it.name }
        }
    }

    // Clearing every key makes the read path above fall back to each enum's
    // declared order with an empty hidden set — today's factory default dock.
    override suspend fun resetToDefaults() {
        context.dockDataStore.editOrLog(TAG) { it.clear() }
    }

    private companion object {
        val NAV_ORDER_KEY = stringPreferencesKey("dock_nav_order")
        val NAV_HIDDEN_KEY = stringSetPreferencesKey("dock_nav_hidden")
        val STATUS_ORDER_KEY = stringPreferencesKey("dock_status_order")
        val STATUS_HIDDEN_KEY = stringSetPreferencesKey("dock_status_hidden")
    }
}
