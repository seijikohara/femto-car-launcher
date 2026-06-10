package io.github.seijikohara.femto.data

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
class DrawerPreferencesTest {
    // The `preferencesDataStore` delegate behind DrawerPreferences is a
    // process-wide singleton bound to the first Application's filesDir, while
    // Robolectric hands each test method a fresh Application and temp dir. All
    // round-trip steps therefore live in one test method, so the persisted file
    // and the singleton never disagree across tests.
    @Test
    fun `togglePinned round-trips pin, unpin, and re-pin without touching other pins`() =
        runTest {
            val store = DrawerPreferences(ApplicationProvider.getApplicationContext())

            store.togglePinned(OTHER)
            store.togglePinned(COMPONENT)
            assertEquals(setOf(OTHER, COMPONENT), store.pinned.first())

            store.togglePinned(COMPONENT)
            assertEquals(setOf(OTHER), store.pinned.first())

            store.togglePinned(COMPONENT)
            assertEquals(setOf(OTHER, COMPONENT), store.pinned.first())
        }

    private companion object {
        const val COMPONENT = "com.example.music/.MainActivity"
        const val OTHER = "com.example.maps/.MapsActivity"
    }
}
