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
import kotlin.test.assertNull

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

    @Test
    fun rename_sets_a_name_and_keeps_the_label() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("1st & Pike, Seattle"))
            val id = store.places
                .first()
                .single()
                .id

            store.rename(id, "Work")

            val place = store.places.first().single()
            assertEquals("Work", place.name)
            assertEquals("Office", place.label)
            assertEquals(PlaceTarget.Query("1st & Pike, Seattle"), place.target)
        }

    @Test
    fun rename_trims_the_name_and_persists_it() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("Office"))
            val id = store.places
                .first()
                .single()
                .id

            store.rename(id, "  Work  ")

            // A fresh instance reads the same file: the name outlives the process.
            assertEquals(
                "Work",
                SavedPlacesPreferences(context)
                    .places
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun a_blank_name_clears_it() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("Office"))
            val id = store.places
                .first()
                .single()
                .id
            store.rename(id, "Work")

            store.rename(id, "  \t ")

            assertNull(
                store.places
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun a_null_name_clears_it() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("Office"))
            val id = store.places
                .first()
                .single()
                .id
            store.rename(id, "Work")

            store.rename(id, null)

            assertNull(
                store.places
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun a_name_equal_to_the_label_is_not_stored() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("Office"))
            val id = store.places
                .first()
                .single()
                .id
            store.rename(id, "Work")

            // The label already says it: renaming back to it returns the row to how it was.
            store.rename(id, " Office ")

            assertNull(
                store.places
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun a_name_is_cut_to_the_cap_without_splitting_a_surrogate_pair() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("Office"))
            val id = store.places
                .first()
                .single()
                .id
            // 59 letters, then a character outside the BMP (two UTF-16 units) that straddles the cap.
            val name = "a".repeat(MAX_PLACE_NAME_LENGTH - 1) + "🏠" + "tail"

            store.rename(id, name)

            assertEquals(
                "a".repeat(MAX_PLACE_NAME_LENGTH - 1),
                store.places
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun rename_of_a_missing_id_changes_nothing() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("Office"))
            val before = storedJson()

            store.rename(99L, "Work")

            assertEquals(before, storedJson())
        }

    @Test
    fun rename_after_a_delete_does_not_bring_the_place_back() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("Office"))
            val id = store.places
                .first()
                .single()
                .id
            store.delete(id)

            store.rename(id, "Work")

            assertEquals(emptyList(), store.places.first())
        }

    @Test
    fun rename_leaves_an_unreadable_list_alone() =
        runTest {
            val store = clearedStore()
            val unreadable = """[{"id":1,"label":"Ferry","target":{"type":"ferry","route":"7"}}]"""
            context.savedPlacesDataStore.edit { it[PlacesKey] = unreadable }

            store.rename(1L, "Crossing")

            assertEquals(unreadable, storedJson())
        }

    @Test
    fun a_stored_place_without_a_name_still_reads() =
        runTest {
            val store = clearedStore()
            context.savedPlacesDataStore.edit {
                it[PlacesKey] = """[{"id":1,"label":"Office","target":{"type":"query","text":"Office"}}]"""
            }

            assertEquals(SavedPlace(1L, "Office", PlaceTarget.Query("Office")), store.places.first().single())
        }

    @Test
    fun a_place_without_a_name_stores_no_name_key() =
        runTest {
            val store = clearedStore()
            store.add("Office", PlaceTarget.Query("Office"))

            // An older build must read the list as it always did.
            assertFalse(storedJson().orEmpty().contains("name"), storedJson())
        }

    @Test
    fun the_title_is_the_name_else_the_label() {
        val place = SavedPlace(1L, "Office", PlaceTarget.Query("Office"))

        assertEquals("Office", place.title)
        assertEquals("Work", place.copy(name = "Work").title)
    }

    @Test
    fun an_unnamed_query_place_shows_a_line_only_when_its_text_differs_from_the_label() {
        assertNull(SavedPlace(1L, "Office", PlaceTarget.Query("Office")).detail)
        assertEquals("1st & Pike", SavedPlace(1L, "Office", PlaceTarget.Query("1st & Pike")).detail)
    }

    @Test
    fun an_unnamed_point_place_shows_no_line() =
        assertNull(SavedPlace(1L, "35.68124, 139.76713", PlaceTarget.Point(35.68124, 139.76713)).detail)

    @Test
    fun a_named_query_place_says_its_text_underneath() =
        assertEquals(
            "1st & Pike, Seattle",
            SavedPlace(1L, "1st & Pike, Seattle", PlaceTarget.Query("1st & Pike, Seattle"), name = "Work").detail,
        )

    @Test
    fun a_named_point_place_says_its_label_underneath() =
        assertEquals(
            "1-9-1 Marunouchi",
            SavedPlace(1L, "1-9-1 Marunouchi", PlaceTarget.Point(35.68, 139.76), name = "Home").detail,
        )

    private companion object {
        val PlacesKey = stringPreferencesKey("saved_places")
    }
}
