package io.github.seijikohara.femto.data.display

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The migration is exercised directly rather than through the DataStore: the
// displayDataStore delegate is a process-wide singleton whose migrations run
// once, on first access, so a test cannot seed retired keys and then observe
// the migration through it.
class RetiredMapboxKeysMigrationTest {
    private val backend = DisplayPreferences.MAP_BACKEND_KEY
    private val token = stringPreferencesKey("mapbox_access_token")
    private val style = stringPreferencesKey("mapbox_style")
    private val traffic = booleanPreferencesKey("mapbox_traffic")

    @Test
    fun `a store on the retired backend moves to OSM and drops the orphaned keys`() =
        runTest {
            val before =
                preferencesOf(
                    backend to "MAPBOX",
                    token to "pk.old",
                    style to "SATELLITE",
                    traffic to true,
                    DisplayPreferences.MAP_ZOOM_KEY to 12,
                )
            assertTrue(RetiredMapboxKeysMigration.shouldMigrate(before))

            val after = RetiredMapboxKeysMigration.migrate(before)
            assertEquals(MapBackend.OSM.name, after[backend])
            assertFalse(after.contains(token))
            assertFalse(after.contains(style))
            assertFalse(after.contains(traffic))
            // Unrelated settings survive untouched.
            assertEquals(12, after[DisplayPreferences.MAP_ZOOM_KEY])
        }

    @Test
    fun `orphaned keys alone trigger the migration without touching another backend`() =
        runTest {
            val before = preferencesOf(backend to MapBackend.GOOGLEMAPS.name, token to "pk.old")
            assertTrue(RetiredMapboxKeysMigration.shouldMigrate(before))

            val after = RetiredMapboxKeysMigration.migrate(before)
            assertEquals(MapBackend.GOOGLEMAPS.name, after[backend])
            assertFalse(after.contains(token))
        }

    @Test
    fun `a store with no retired state is left alone`() =
        runTest {
            assertFalse(RetiredMapboxKeysMigration.shouldMigrate(preferencesOf(backend to MapBackend.OSM.name)))
            assertFalse(RetiredMapboxKeysMigration.shouldMigrate(preferencesOf()))
        }
}
