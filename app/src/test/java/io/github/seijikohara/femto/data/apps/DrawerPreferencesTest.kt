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
class DrawerPreferencesTest {
    // The `preferencesDataStore` delegate behind DrawerPreferences is a
    // process-wide singleton bound to the first Application's filesDir, while
    // Robolectric hands each test method a fresh Application and temp dir. All
    // round-trip steps therefore live in one test method, so the persisted file
    // and the singleton never disagree across tests.
    @Test
    fun `drawer preferences round-trip pins in order and persist the icon size`() =
        runTest {
            val store = DrawerPreferences(ApplicationProvider.getApplicationContext())

            // Pins keep insertion order, not label/alphabetical order.
            store.togglePinned(OTHER)
            store.togglePinned(COMPONENT)
            assertEquals(listOf(OTHER, COMPONENT), store.pinned.first())

            // A wholesale reorder (drag commit) replaces the order verbatim.
            store.setPinnedOrder(listOf(COMPONENT, OTHER))
            assertEquals(listOf(COMPONENT, OTHER), store.pinned.first())
            store.setPinnedOrder(listOf(OTHER, COMPONENT))
            assertEquals(listOf(OTHER, COMPONENT), store.pinned.first())

            store.togglePinned(COMPONENT)
            assertEquals(listOf(OTHER), store.pinned.first())

            // Re-pinning appends at the end.
            store.togglePinned(COMPONENT)
            assertEquals(listOf(OTHER, COMPONENT), store.pinned.first())

            assertEquals(DrawerIconSize.MEDIUM, store.iconSize.first())
            store.setIconSize(DrawerIconSize.LARGE)
            assertEquals(DrawerIconSize.LARGE, store.iconSize.first())
        }

    private companion object {
        const val COMPONENT = "com.example.music/.MainActivity"
        const val OTHER = "com.example.maps/.MapsActivity"
    }
}

class ResolvePinnedOrderTest {
    @Test
    fun `returns ordered entries from the order string`() =
        assertEquals(
            listOf("b/.B", "a/.A"),
            resolvePinnedOrder(order = "b/.B\na/.A", legacy = setOf("z/.Z")),
        )

    @Test
    fun `falls back to the legacy set sorted when no order exists`() =
        assertEquals(
            listOf("a/.A", "b/.B"),
            resolvePinnedOrder(order = null, legacy = setOf("b/.B", "a/.A")),
        )

    @Test
    fun `returns empty when neither key exists`() =
        assertEquals(emptyList(), resolvePinnedOrder(order = null, legacy = null))

    @Test
    fun `drops empty segments from a blank order string`() =
        assertEquals(emptyList(), resolvePinnedOrder(order = "", legacy = null))
}
