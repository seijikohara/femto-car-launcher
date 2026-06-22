package io.github.seijikohara.femto.data.display

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DisplayPreferencesTest {
    // The displayDataStore delegate is a process-wide singleton bound to the
    // first Application's filesDir, while Robolectric hands each test method a
    // fresh Application. All DataStore round-trip steps therefore live in one
    // test method so the persisted file and the singleton never disagree across
    // tests (mirrors DrawerPreferencesTest). The migration tests below touch no
    // DataStore, so they stay separate.
    @Test
    fun `an empty store reads the defaults and resetToDefaults restores them`() =
        runTest {
            val store = DisplayPreferences(ApplicationProvider.getApplicationContext())
            // Clear any state left by a test that ran earlier in the same process.
            store.resetToDefaults()

            // Reset parity: every per-field read fallback is kept identical to
            // DisplaySettings.Default, so a fresh key-less store reads exactly
            // Default. A new field whose read fallback drifts from Default — the
            // failure mode the SettingsViewModel fake store cannot catch — breaks
            // this single equality.
            assertEquals(DisplaySettings.Default, store.settings.first())

            // Mutate a spread of fields across the stored types (enum / bool / int).
            store.setThemeMode(ThemeMode.DARK)
            store.setKeepScreenOn(false)
            store.setMapZoom(DEFAULT_MAP_ZOOM + 2)
            store.setMusicSpectrum(true)
            assertNotEquals(DisplaySettings.Default, store.settings.first())

            // resetToDefaults() clears every key, so the read falls back to Default
            // for all fields at once.
            store.resetToDefaults()
            assertEquals(DisplaySettings.Default, store.settings.first())
        }

    @Test
    fun `the render mode migrates the retired three-mode values to LIVE`() {
        // A user who picked a live map before the software backend was removed
        // keeps a live map, not the SNAPSHOT floor.
        assertEquals(MapRenderMode.LIVE, "LIVE_HARDWARE".toMapRenderModeOr(MapRenderMode.SNAPSHOT))
        assertEquals(MapRenderMode.LIVE, "LIVE_SOFTWARE".toMapRenderModeOr(MapRenderMode.SNAPSHOT))
    }

    @Test
    fun `the render mode decodes current values and falls back for unknowns`() {
        assertEquals(MapRenderMode.LIVE, "LIVE".toMapRenderModeOr(MapRenderMode.SNAPSHOT))
        assertEquals(MapRenderMode.SNAPSHOT, "SNAPSHOT".toMapRenderModeOr(MapRenderMode.LIVE))
        assertEquals(MapRenderMode.SNAPSHOT, "REMOVED".toMapRenderModeOr(MapRenderMode.SNAPSHOT))
        assertEquals(MapRenderMode.LIVE, null.toMapRenderModeOr(MapRenderMode.LIVE))
    }

    // All three backend-settings cases share one test method because the
    // displayDataStore singleton is bound to the process Application, not the test
    // method — separate methods would see each other's writes (same singleton
    // constraint as the round-trip test above).
    @Test
    fun `mapBackend mapboxStyle mapboxTraffic defaults migration and round-trip`() =
        runTest {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val store = newTestStore(ctx)
            // Clear any state left by a test that ran earlier in the same process.
            store.resetToDefaults()

            // Defaults: absent map_backend key resolves to OSM (the migration
            // semantic — any user whose store has only the legacy map_render_mode
            // key gets OSM automatically because map_backend is simply absent).
            assertFalse(store.settings.first().mapboxTraffic)
            assertEquals(MapBackend.OSM, store.settings.first().mapBackend)
            assertEquals(MapboxStyle.STANDARD, store.settings.first().mapboxStyle)

            // Legacy: map_render_mode written (simulating a pre-migration store)
            // with no map_backend key → mapBackend still resolves to OSM.
            writeRaw(ctx) { it[MAP_RENDER_MODE_KEY_TEST] = "SNAPSHOT" }
            assertEquals(MapBackend.OSM, store.settings.first().mapBackend)

            // Round-trip: write MAPBOX / SATELLITE / traffic-on, then read back.
            store.setMapBackend(MapBackend.MAPBOX)
            store.setMapboxStyle(MapboxStyle.SATELLITE)
            store.setMapboxTraffic(true)
            val s = store.settings.first()
            assertEquals(MapBackend.MAPBOX, s.mapBackend)
            assertEquals(MapboxStyle.SATELLITE, s.mapboxStyle)
            assertTrue(s.mapboxTraffic)
        }
}

// Creates a DisplayPreferences bound to the given Context (the Robolectric
// test Application). Callers must pass the same context for writeRaw so both
// helpers reach the same singleton DataStore.
private fun newTestStore(ctx: Context): DisplayPreferences = DisplayPreferences(ctx)

// Writes raw preferences directly into the displayDataStore so tests can
// simulate a legacy on-disk state without going through DisplayPreferences setters.
private suspend fun writeRaw(
    ctx: Context,
    transform: (MutablePreferences) -> Unit,
) = ctx.displayDataStore.edit(transform)

// The raw key that the legacy migration test needs to write — mirrors the
// production MAP_RENDER_MODE_KEY without importing the private companion member.
private val MAP_RENDER_MODE_KEY_TEST = stringPreferencesKey("map_render_mode")
