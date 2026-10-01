package io.github.seijikohara.femto.data.places

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// The savedPlacesDataStore delegate is a process-wide singleton bound to the
// first Application's filesDir, while Robolectric hands each test method a
// fresh Application (mirrors UpdatePreferencesTest), so every test clears the
// raw store first.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SavedPlacesPreferencesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private suspend fun clearedStore(): SavedPlacesPreferences {
        context.savedPlacesDataStore.edit { it.clear() }
        return SavedPlacesPreferences(context)
    }

    private suspend fun storedJson(): String? = context.savedPlacesDataStore.data.first()[PlacesKey]

    @Test
    fun places_append_in_insertion_order_delete_by_id_and_persist() =
        runTest {
            val store = clearedStore()
            assertEquals(emptyList(), store.places.first())

            store.add("Office", PlaceTarget.Query("1st & Pike, Seattle"))
            store.add("Home (new)", PlaceTarget.Point(35.681236, 139.767125))
            store.add("Gym", PlaceTarget.Query("東京駅"))
            val added = store.places.first()
            // Newest last: a saved place keeps its position as others are added.
            assertEquals(listOf("Office", "Home (new)", "Gym"), added.map { it.label })
            assertEquals(PlaceTarget.Point(35.681236, 139.767125), added[1].target)

            store.delete(added[1].id)
            assertEquals(listOf("Office", "Gym"), store.places.first().map { it.label })

            // A fresh instance reads the same file: the list outlives the process.
            assertEquals(listOf(added[0], added[2]), SavedPlacesPreferences(context).places.first())
        }

    @Test
    fun an_id_is_never_issued_twice_even_after_its_place_is_deleted() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("Office"))
            store.add("Home", PlaceTarget.Query("Home"))
            store.add("Gym", PlaceTarget.Query("Gym"))
            val issued = store.places
                .first()
                .map { it.id }
                .toSet()

            // Delete a middle and the newest place: the list's largest id plus
            // one would then hand out an id issued before.
            store.places
                .first()
                .drop(1)
                .forEach { store.delete(it.id) }
            store.add("Park", PlaceTarget.Query("Park"))

            val park = store.places.first().single { it.label == "Park" }
            assertFalse(park.id in issued, "id ${park.id} was issued before: $issued")
        }

    @Test
    fun a_list_this_build_cannot_read_reads_empty_and_is_never_overwritten() =
        runTest {
            val store = clearedStore()
            // A place with a target subtype from a newer build.
            val unreadable = """[{"id":1,"label":"Ferry","target":{"type":"ferry","route":"7"}}]"""
            context.savedPlacesDataStore.edit { it[PlacesKey] = unreadable }
            assertEquals(emptyList(), store.places.first())

            store.add("Office", PlaceTarget.Query("Office"))
            store.delete(1L)

            assertEquals(unreadable, storedJson())
        }

    private companion object {
        val PlacesKey = stringPreferencesKey("saved_places")
    }
}
