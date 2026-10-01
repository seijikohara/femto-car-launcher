package io.github.seijikohara.femto.data.places

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "SavedPlacesPreferences"

/** A destination the user kept: a [label] and the [target] it hands off. */
@Serializable
internal data class SavedPlace(
    val id: Long,
    val label: String,
    val target: PlaceTarget,
)

/**
 * The DataStore file name. The backup and data-extraction rules exclude
 * `datastore/<name>.preferences_pb`, so a rename must move those rules too.
 */
internal const val SAVED_PLACES_STORE_NAME = "saved_places"

private val Context.savedPlacesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = SAVED_PLACES_STORE_NAME)

// ignoreUnknownKeys: a later build may add fields, and a downgrade must still
// read the places it understands.
private val PlacesJson = Json { ignoreUnknownKeys = true }

/**
 * The destination panel's saved places, in insertion order (newest last, so a
 * place keeps its position as others are added). Location data: the store
 * stays on the device and out of backup and device transfer, and nothing here
 * logs a label, a query or a coordinate.
 */
internal interface SavedPlacesStore {
    val places: Flow<List<SavedPlace>>

    suspend fun add(
        label: String,
        target: PlaceTarget,
    )

    suspend fun delete(id: Long)
}

/**
 * DataStore-backed [SavedPlacesStore]. The list is one JSON value: a handful
 * of places needs no table, and one key reads and writes the order whole.
 */
internal class SavedPlacesPreferences(
    private val context: Context,
) : SavedPlacesStore {
    override val places: Flow<List<SavedPlace>> =
        context.savedPlacesDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs -> prefs[PLACES_KEY]?.let(::storedPlacesOrEmpty).orEmpty() }

    // The id comes from a counter kept beside the list rather than from the
    // list's largest id, so deleting the newest place never frees its id for
    // the next one: a tap on a row on its way out cannot hit a different place.
    override suspend fun add(
        label: String,
        target: PlaceTarget,
    ) {
        context.savedPlacesDataStore.editOrLog(TAG) { prefs ->
            val current = prefs[PLACES_KEY]?.let(::storedPlacesOrEmpty).orEmpty()
            val id = maxOf(prefs[NEXT_ID_KEY] ?: 1L, (current.maxOfOrNull { it.id } ?: 0L) + 1)
            prefs[PLACES_KEY] = PlacesJson.encodeToString(current + SavedPlace(id, label, target))
            prefs[NEXT_ID_KEY] = id + 1
        }
    }

    override suspend fun delete(id: Long) {
        context.savedPlacesDataStore.editOrLog(TAG) { prefs ->
            val current = prefs[PLACES_KEY]?.let(::storedPlacesOrEmpty).orEmpty()
            prefs[PLACES_KEY] = PlacesJson.encodeToString(current.filterNot { it.id == id })
        }
    }

    private companion object {
        val PLACES_KEY = stringPreferencesKey("saved_places")
        val NEXT_ID_KEY = longPreferencesKey("saved_places_next_id")
    }
}

// An unreadable list (damaged, or written in a shape this build cannot read)
// reads as none rather than crashing the panel. The log names the failure
// only: the stored text is location data.
private fun storedPlacesOrEmpty(json: String): List<SavedPlace> =
    runCatching { PlacesJson.decodeFromString<List<SavedPlace>>(json) }
        .onFailure { Log.w(TAG, "saved places unreadable: ${it.javaClass.simpleName}") }
        .getOrDefault(emptyList())
