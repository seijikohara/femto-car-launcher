package io.github.seijikohara.femto.data.apps

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecentAppsPreferencesTest {
    // The `preferencesDataStore` delegate behind RecentAppsPreferences is a
    // process-wide singleton bound to the first Application's filesDir, while
    // Robolectric hands each test method a fresh Application and temp dir. All
    // round-trip steps therefore live in one test method, so the persisted
    // file and the singleton never disagree across tests (same caveat as
    // DrawerPreferencesTest).
    @Test
    fun `recordLaunch round-trips ordering and caps history at RECENT_APPS_MAX_COUNT`() =
        runTest {
            val store = RecentAppsPreferences(ApplicationProvider.getApplicationContext())

            store.recordLaunch(MAPS, atMillis = 100L)
            store.recordLaunch(MUSIC, atMillis = 200L)
            assertEquals(listOf(MUSIC, MAPS), store.recentComponents.first())

            // Re-launching an older entry bumps it back to the front.
            store.recordLaunch(MAPS, atMillis = 300L)
            assertEquals(listOf(MAPS, MUSIC), store.recentComponents.first())

            (0 until RECENT_APPS_MAX_COUNT + 2).forEach { i ->
                store.recordLaunch("com.example.app$i/.Main", atMillis = 1_000L + i)
            }
            val recent = store.recentComponents.first()
            assertEquals(RECENT_APPS_MAX_COUNT, recent.size)
            // The most recently launched components survive; the oldest are trimmed.
            assertEquals("com.example.app${RECENT_APPS_MAX_COUNT + 1}/.Main", recent.first())
        }

    private companion object {
        const val MAPS = "com.example.maps/.MapsActivity"
        const val MUSIC = "com.example.music/.MainActivity"
    }
}
