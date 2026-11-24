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

/**
 * A destination the user kept: a [label] and the [target] it hands off, and
 * optionally a [name] of the user's own. [label] is what the place was saved as
 * (the typed text, an address or coordinates) and never changes; [name] sits on
 * top of it and is null until the user gives one. Absent from the stored JSON
 * while null, so an older build reads the place unchanged.
 */
@Serializable
internal data class SavedPlace(
    val id: Long,
    val label: String,
    val target: PlaceTarget,
    val name: String? = null,
) {
    /** What the row, the screen-reader labels and a point's pin call the place: the name, else the label. */
    val title: String get() = name ?: label

    /**
     * The line under the [title], or null for none. A named place says what it was saved as (the typed text, else
     * the label); an unnamed one repeats nothing, except that a typed text differing from its label still shows, so
     * "Office" says where it goes.
     */
    val detail: String?
        get() {
            val text = (target as? PlaceTarget.Query)?.text
            return if (name != null) text ?: label else text?.takeIf { it != label }
        }
}

// A name is a row title and, for a point, the pin label inside a geo: URI, where
// each non-ASCII character costs up to nine characters once percent-encoded (three
// UTF-8 bytes). 60 characters keep even an all-CJK pin label near 540 characters,
// well short of what a maps app or an intent takes without trouble, and are more
// than a row shows before it ellipsizes.
internal const val MAX_PLACE_NAME_LENGTH = 60

/**
 * Cut [text] to [MAX_PLACE_NAME_LENGTH] without leaving half a surrogate pair at the end. The panel's field and
 * [placeNameOrNull] share it, so what the field lets through is what the store keeps.
 */
internal fun capPlaceName(text: String): String =
    text.take(MAX_PLACE_NAME_LENGTH).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }

/**
 * The name to store for a place saved as [label]: [name] trimmed and capped, or null when nothing is left or it
 * equals the label (the label already says it, so the row is as it was before).
 */
internal fun placeNameOrNull(
    name: String?,
    label: String,
): String? =
    name
        ?.trim()
        ?.let(::capPlaceName)
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != label }

/**
 * The DataStore file name. The backup and data-extraction rules exclude
 * `datastore/<name>.preferences_pb`, so a rename must move those rules too.
 */
internal const val SAVED_PLACES_STORE_NAME = "saved_places"

// Internal (not private): SavedPlacesPreferencesTest seeds and clears the raw store.
internal val Context.savedPlacesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = SAVED_PLACES_STORE_NAME)

// ignoreUnknownKeys: a later build may add fields, and a downgrade must still
// read the places it understands.
private val PlacesJson = Json { ignoreUnknownKeys = true }

/**
 * The destination panel's saved places, in insertion order (newest last, so a
 * place keeps its position as others are added). Location data: the store
 * stays on the device and out of backup and device transfer, and nothing here
 * logs a label, a name, a query or a coordinate.
 */
internal interface SavedPlacesStore {
    val places: Flow<List<SavedPlace>>

    suspend fun add(
        label: String,
        target: PlaceTarget,
    )

    suspend fun delete(id: Long)

    /**
     * Give the place [id] the name [name], or take its name away with null; [placeNameOrNull] decides what is
     * stored. A place that is no longer there stays gone: nothing is added.
     */
    suspend fun rename(
        id: Long,
        name: String?,
    )
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
            .map { prefs -> prefs.placesOrNull().orEmpty() }

    // The id comes from a counter kept beside the list rather than from the
    // list's largest id, so deleting the newest place never frees its id for
    // the next one: a tap on a row on its way out cannot hit a different place.
    // An unreadable list skips the write (see placesOrNull).
    override suspend fun add(
        label: String,
        target: PlaceTarget,
    ) {
        context.savedPlacesDataStore.editOrLog(TAG) { prefs ->
            prefs.placesOrNull()?.let { current ->
                val id = maxOf(prefs[NEXT_ID_KEY] ?: 1L, (current.maxOfOrNull { it.id } ?: 0L) + 1)
                prefs[PLACES_KEY] = PlacesJson.encodeToString(current + SavedPlace(id, label, target))
                prefs[NEXT_ID_KEY] = id + 1
            }
        }
    }

    override suspend fun delete(id: Long) {
        context.savedPlacesDataStore.editOrLog(TAG) { prefs ->
            prefs.placesOrNull()?.let { current ->
                prefs[PLACES_KEY] = PlacesJson.encodeToString(current.filterNot { it.id == id })
            }
        }
    }

    // Maps over the stored list and never inserts, so a rename that lands after the
    // place was deleted changes nothing instead of bringing it back. Like add and
    // delete, it leaves an unreadable list alone.
    override suspend fun rename(
        id: Long,
        name: String?,
    ) {
        context.savedPlacesDataStore.editOrLog(TAG) { prefs ->
            prefs
                .placesOrNull()
                ?.takeIf { current -> current.any { it.id == id } }
                ?.let { current ->
                    prefs[PLACES_KEY] = PlacesJson.encodeToString(
                        current.map { if (it.id == id) it.copy(name = placeNameOrNull(name, it.label)) else it },
                    )
                }
        }
    }

    // The stored list, empty when none is stored, or null when one is stored
    // that this build cannot read (damaged, or a place type from a newer
    // build). The panel then shows no places, and add and delete leave the
    // value alone rather than replace it: a downgrade must not wipe what a
    // newer build saved.
    private fun Preferences.placesOrNull(): List<SavedPlace>? =
        when (val json = this[PLACES_KEY]) {
            null -> emptyList()
            else -> storedPlacesOrNull(json)
        }

    private companion object {
        val PLACES_KEY = stringPreferencesKey("saved_places")
        val NEXT_ID_KEY = longPreferencesKey("saved_places_next_id")
    }
}

// The log names the failure class only: the stored text is location data.
private fun storedPlacesOrNull(json: String): List<SavedPlace>? =
    runCatching { PlacesJson.decodeFromString<List<SavedPlace>>(json) }
        .onFailure { Log.w(TAG, "saved places unreadable: ${it.javaClass.simpleName}") }
        .getOrNull()
