package io.github.seijikohara.femto.data.apps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import io.github.seijikohara.femto.data.common.toEnumOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-drawer tile layout. */
internal enum class DrawerLayout { GRID, LIST }

/** App-drawer icon size preset; MEDIUM matches the pre-preset dimensions. */
internal enum class DrawerIconSize { SMALL, MEDIUM, LARGE }

/**
 * Persisted app-drawer preferences: the tile [layout], the [iconSize] preset, and
 * the [pinned] apps (each a flattened [android.content.ComponentName]) in the
 * order they were pinned. Pinned apps surface in the dock fixed at the bottom of
 * the drawer sheet.
 */
internal interface DrawerSettingsStore {
    val layout: Flow<DrawerLayout>
    val iconSize: Flow<DrawerIconSize>
    val pinned: Flow<List<String>>

    /**
     * Components (flattened [android.content.ComponentName]) hidden from the
     * all-apps list — a user-curated declutter, unordered. Distinct from a
     * pin: a hidden app is filtered out of the grid / list / Recent row, while
     * a pin surfaces it in the bottom dock. The two are independent sets.
     */
    val hidden: Flow<Set<String>>

    suspend fun setLayout(value: DrawerLayout)

    suspend fun setIconSize(value: DrawerIconSize)

    /** Append the component if absent, remove it if present. */
    suspend fun togglePinned(flattenedComponent: String)

    /**
     * Replace the pin order wholesale (drag-reorder commit). Entries not
     * currently pinned are persisted as-is — the caller derives [value] from
     * the rendered dock, which is itself resolved from the persisted order.
     */
    suspend fun setPinnedOrder(value: List<String>)

    /** Hide the component if visible, unhide it if hidden. */
    suspend fun toggleHidden(flattenedComponent: String)
}

// A flattened ComponentName ("package/class") can never contain a newline, so it
// is a safe join separator for the ordered pin list.
private const val PIN_SEPARATOR = "\n"

/**
 * Resolve the ordered pin list from the persisted [order] string, falling back to
 * the legacy unordered [legacy] set (sorted for determinism) written by versions
 * that pinned into a section instead of the dock.
 */
internal fun resolvePinnedOrder(
    order: String?,
    legacy: Set<String>?,
): List<String> =
    order?.split(PIN_SEPARATOR)?.filter { it.isNotEmpty() }
        ?: legacy?.sorted()
        ?: emptyList()

private val Context.drawerDataStore: DataStore<Preferences> by preferencesDataStore(name = "drawer_preferences")

private const val TAG = "DrawerPreferences"

internal class DrawerPreferences(
    private val context: Context,
) : DrawerSettingsStore {
    override val layout: Flow<DrawerLayout> =
        context.drawerDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> prefs[LAYOUT_KEY].toEnumOr(DrawerLayout.GRID) }

    override val iconSize: Flow<DrawerIconSize> =
        context.drawerDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> prefs[ICON_SIZE_KEY].toEnumOr(DrawerIconSize.MEDIUM) }

    override val pinned: Flow<List<String>> =
        context.drawerDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> resolvePinnedOrder(prefs[PINNED_ORDER_KEY], prefs[LEGACY_PINNED_KEY]) }

    override val hidden: Flow<Set<String>> =
        context.drawerDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> prefs[HIDDEN_KEY].orEmpty() }

    override suspend fun setLayout(value: DrawerLayout) {
        context.drawerDataStore.editOrLog(TAG) { it[LAYOUT_KEY] = value.name }
    }

    override suspend fun setIconSize(value: DrawerIconSize) {
        context.drawerDataStore.editOrLog(TAG) { it[ICON_SIZE_KEY] = value.name }
    }

    override suspend fun togglePinned(flattenedComponent: String) {
        context.drawerDataStore.editOrLog(TAG) { prefs ->
            val current = resolvePinnedOrder(prefs[PINNED_ORDER_KEY], prefs[LEGACY_PINNED_KEY])
            val updated =
                if (flattenedComponent in current) current - flattenedComponent else current + flattenedComponent
            prefs[PINNED_ORDER_KEY] = updated.joinToString(PIN_SEPARATOR)
            // The ordered key is authoritative from the first write on.
            prefs.remove(LEGACY_PINNED_KEY)
        }
    }

    override suspend fun setPinnedOrder(value: List<String>) {
        context.drawerDataStore.editOrLog(TAG) { prefs ->
            prefs[PINNED_ORDER_KEY] = value.joinToString(PIN_SEPARATOR)
            prefs.remove(LEGACY_PINNED_KEY)
        }
    }

    override suspend fun toggleHidden(flattenedComponent: String) {
        context.drawerDataStore.editOrLog(TAG) { prefs ->
            val current = prefs[HIDDEN_KEY].orEmpty()
            prefs[HIDDEN_KEY] =
                if (flattenedComponent in current) current - flattenedComponent else current + flattenedComponent
        }
    }

    private companion object {
        val LAYOUT_KEY = stringPreferencesKey("drawer_layout")
        val ICON_SIZE_KEY = stringPreferencesKey("drawer_icon_size")
        val PINNED_ORDER_KEY = stringPreferencesKey("drawer_pinned_order")
        val LEGACY_PINNED_KEY = stringSetPreferencesKey("drawer_pinned")
        val HIDDEN_KEY = stringSetPreferencesKey("drawer_hidden")
    }
}
