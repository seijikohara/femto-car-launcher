package io.github.seijikohara.femto.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-drawer tile layout. */
internal enum class DrawerLayout { GRID, LIST }

/**
 * Persisted app-drawer preferences: the tile [layout] and the set of [pinned] apps
 * (each a flattened [android.content.ComponentName]). Pinned apps surface in a
 * section at the top of the drawer.
 */
internal interface DrawerSettingsStore {
    val layout: Flow<DrawerLayout>
    val pinned: Flow<Set<String>>

    suspend fun setLayout(value: DrawerLayout)

    /** Add the component if absent, remove it if present. */
    suspend fun togglePinned(flattenedComponent: String)
}

private val Context.drawerDataStore: DataStore<Preferences> by preferencesDataStore(name = "drawer_preferences")

private const val TAG = "DrawerPreferences"

internal class DrawerPreferences(
    private val context: Context,
) : DrawerSettingsStore {
    override val layout: Flow<DrawerLayout> =
        context.drawerDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                prefs[LAYOUT_KEY]
                    ?.let { name -> DrawerLayout.entries.firstOrNull { it.name == name } }
                    ?: DrawerLayout.GRID
            }

    override val pinned: Flow<Set<String>> =
        context.drawerDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> prefs[PINNED_KEY] ?: emptySet() }

    override suspend fun setLayout(value: DrawerLayout) {
        context.drawerDataStore.editOrLog(TAG) { it[LAYOUT_KEY] = value.name }
    }

    override suspend fun togglePinned(flattenedComponent: String) {
        context.drawerDataStore.editOrLog(TAG) { prefs ->
            val current = prefs[PINNED_KEY] ?: emptySet()
            prefs[PINNED_KEY] =
                if (flattenedComponent in current) current - flattenedComponent else current + flattenedComponent
        }
    }

    private companion object {
        val LAYOUT_KEY = stringPreferencesKey("drawer_layout")
        val PINNED_KEY = stringSetPreferencesKey("drawer_pinned")
    }
}
