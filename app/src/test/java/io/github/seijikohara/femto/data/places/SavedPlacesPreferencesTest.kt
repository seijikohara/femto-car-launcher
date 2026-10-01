package io.github.seijikohara.femto.data.places

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// The `preferencesDataStore` delegate behind SavedPlacesPreferences is a
// process-wide singleton bound to the first Application's filesDir, while
// Robolectric hands each test method a fresh Application and temp dir. All
// round-trip steps therefore live in one test method (mirrors
// CalendarPreferencesTest).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SavedPlacesPreferencesTest {
    private fun newStore() = SavedPlacesPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun places_append_in_insertion_order_delete_by_id_and_persist() =
        runTest {
            val store = newStore()
            assertEquals(emptyList(), store.places.first())

            store.add("Office", PlaceTarget.Query("1st & Pike, Seattle"))
            store.add("Home (new)", PlaceTarget.Point(35.681236, 139.767125))
            store.add("Gym", PlaceTarget.Query("東京駅"))
            val added = store.places.first()
            // Newest last: a saved place keeps its position as others are added.
            assertEquals(listOf("Office", "Home (new)", "Gym"), added.map { it.label })
            assertEquals(PlaceTarget.Point(35.681236, 139.767125), added[1].target)
            assertEquals(3, added.map { it.id }.toSet().size)

            store.delete(added[1].id)
            assertEquals(listOf("Office", "Gym"), store.places.first().map { it.label })

            // A fresh instance reads the same file: the list outlives the process.
            val reread = newStore().places.first()
            assertEquals(listOf(added[0], added[2]), reread)

            // An id is never reused, even the newest one's after its delete, so
            // a tap on a row that is just going away cannot hit the next place.
            store.delete(added[2].id)
            store.add("Park", PlaceTarget.Query("Park"))
            val park = store.places.first().single { it.label == "Park" }
            assertNotEquals(added[2].id, park.id)
            assertNotEquals(added[0].id, park.id)
        }
}
