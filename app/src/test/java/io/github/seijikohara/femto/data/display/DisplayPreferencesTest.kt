package io.github.seijikohara.femto.data.display

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
            // for all 26 fields at once.
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
}
