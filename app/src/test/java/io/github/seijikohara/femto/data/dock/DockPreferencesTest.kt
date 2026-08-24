package io.github.seijikohara.femto.data.dock

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// The dockDataStore delegate is a process-wide singleton bound to the first
// Application's filesDir, while Robolectric hands each test method a fresh
// Application (mirrors DrawerPreferencesTest / DisplayPreferencesTest); every
// test below starts with resetToDefaults() so it never sees a prior test's
// writes.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DockPreferencesTest {
    @Test
    fun `an empty store reads every id in its enum's declared order with nothing hidden`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            assertEquals(DockNavId.entries, store.navOrder.first())
            assertEquals(emptySet(), store.navHidden.first())
            assertEquals(DockStatusId.entries, store.statusOrder.first())
            assertEquals(emptySet(), store.statusHidden.first())
        }

    @Test
    fun `setNavOrder replaces the persisted nav order wholesale`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            val reordered =
                listOf(DockNavId.SETTINGS, DockNavId.PHONE, DockNavId.APPS) +
                    DockNavId.entries.filterNot {
                        it == DockNavId.SETTINGS || it == DockNavId.PHONE ||
                            it == DockNavId.APPS
                    }
            store.setNavOrder(reordered)

            assertEquals(reordered, store.navOrder.first())
        }

    @Test
    fun `toggleNavHidden hides then restores an id`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            store.toggleNavHidden(DockNavId.MUSIC)
            assertEquals(setOf(DockNavId.MUSIC), store.navHidden.first())

            store.toggleNavHidden(DockNavId.MUSIC)
            assertEquals(emptySet(), store.navHidden.first())
        }

    @Test
    fun `toggleNavHidden refuses to hide the last visible id`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            // Hide every id but one; then attempt to hide the survivor.
            val survivor = DockNavId.APPS
            DockNavId.entries.filter { it != survivor }.forEach { store.toggleNavHidden(it) }
            store.toggleNavHidden(survivor)

            // The survivor stays visible — at least one id always remains.
            assertEquals(DockNavId.entries.toSet() - survivor, store.navHidden.first())
        }

    @Test
    fun `moveNav swaps an id with its right-hand visible neighbor`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            store.moveNav(DockNavId.MUSIC, 1)

            assertEquals(
                listOf(
                    DockNavId.PHONE,
                    DockNavId.APPS,
                    DockNavId.NAVIGATION,
                    DockNavId.MUSIC,
                    DockNavId.BROWSER,
                    DockNavId.ASSISTANT,
                    DockNavId.SETTINGS,
                ),
                store.navOrder.first(),
            )
        }

    @Test
    fun `moveNav is a no-op at the visible edge`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            store.moveNav(DockNavId.PHONE, -1)

            assertEquals(DockNavId.entries, store.navOrder.first())
        }

    @Test
    fun `moveNav skips over a hidden id, which keeps its own slot`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()
            store.toggleNavHidden(DockNavId.APPS)

            // Visible order is PHONE, MUSIC, NAVIGATION, ...; PHONE's right-hand
            // visible neighbor is MUSIC, not the hidden APPS between them.
            store.moveNav(DockNavId.PHONE, 1)

            assertEquals(
                listOf(
                    DockNavId.MUSIC,
                    DockNavId.APPS,
                    DockNavId.PHONE,
                    DockNavId.NAVIGATION,
                    DockNavId.BROWSER,
                    DockNavId.ASSISTANT,
                    DockNavId.SETTINGS,
                ),
                store.navOrder.first(),
            )
        }

    @Test
    fun `setStatusOrder replaces the persisted status order wholesale`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            val reordered = listOf(DockStatusId.BATTERY, DockStatusId.GPS) +
                DockStatusId.entries.filterNot { it == DockStatusId.BATTERY || it == DockStatusId.GPS }
            store.setStatusOrder(reordered)

            assertEquals(reordered, store.statusOrder.first())
        }

    @Test
    fun `toggleStatusHidden hides then restores an id`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            store.toggleStatusHidden(DockStatusId.BATTERY)
            assertEquals(setOf(DockStatusId.BATTERY), store.statusHidden.first())

            store.toggleStatusHidden(DockStatusId.BATTERY)
            assertEquals(emptySet(), store.statusHidden.first())
        }

    @Test
    fun `toggleStatusHidden hides the last visible indicator`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            DockStatusId.entries.forEach { store.toggleStatusHidden(it) }

            // No keep-one floor here: the cluster is read-only, so an empty one
            // strands no action. Contrast `toggleNavHidden refuses to hide the
            // last visible id`, where the floor keeps the dock actionable.
            assertEquals(DockStatusId.entries.toSet(), store.statusHidden.first())
        }

    @Test
    fun `setStatusClusterVisible flips the whole hidden set at once`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            store.setStatusClusterVisible(false)
            assertEquals(DockStatusId.entries.toSet(), store.statusHidden.first())

            store.setStatusClusterVisible(true)
            assertEquals(emptySet(), store.statusHidden.first())
        }

    @Test
    fun `setStatusClusterVisible true also restores indicators hidden one at a time`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()
            store.toggleStatusHidden(DockStatusId.GPS)

            store.setStatusClusterVisible(true)

            // Showing clears the set wholesale rather than restoring the pre-hide
            // selection: the hidden set is the only home for that fact.
            assertEquals(emptySet(), store.statusHidden.first())
        }

    @Test
    fun `moveStatus swaps an id with its right-hand visible neighbor`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            store.moveStatus(DockStatusId.WIFI, 1)

            assertEquals(
                listOf(
                    DockStatusId.CELLULAR,
                    DockStatusId.BLUETOOTH,
                    DockStatusId.WIFI,
                    DockStatusId.GPS,
                    DockStatusId.BATTERY,
                ),
                store.statusOrder.first(),
            )
        }

    @Test
    fun `moveStatus is a no-op at the visible edge`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            store.moveStatus(DockStatusId.BATTERY, 1)

            assertEquals(DockStatusId.entries, store.statusOrder.first())
        }

    @Test
    fun `resetToDefaults restores a mutated store to its factory defaults`() =
        runTest {
            val store = DockPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            store.toggleNavHidden(DockNavId.PHONE)
            store.setNavOrder(listOf(DockNavId.SETTINGS) + DockNavId.entries.filterNot { it == DockNavId.SETTINGS })
            store.toggleStatusHidden(DockStatusId.GPS)
            store.setStatusOrder(
                listOf(DockStatusId.BATTERY) + DockStatusId.entries.filterNot { it == DockStatusId.BATTERY },
            )

            store.resetToDefaults()

            assertEquals(DockNavId.entries, store.navOrder.first())
            assertEquals(emptySet(), store.navHidden.first())
            assertEquals(DockStatusId.entries, store.statusOrder.first())
            assertEquals(emptySet(), store.statusHidden.first())
        }
}

// Pure list-permutation logic, independent of DataStore / Robolectric — the
// DockPreferencesTest.moveNav / moveStatus cases above cover the same
// behavior through the atomic read-modify-write.
class MoveWithinVisibleTest {
    private val order = listOf("a", "b", "c", "d")

    @Test
    fun `swaps an id with its right-hand visible neighbor`() =
        assertEquals(listOf("a", "c", "b", "d"), moveWithinVisible(order, emptySet(), "b", 1))

    @Test
    fun `swaps an id with its left-hand visible neighbor`() =
        assertEquals(listOf("a", "c", "b", "d"), moveWithinVisible(order, emptySet(), "c", -1))

    @Test
    fun `is a no-op when the id is already the first visible entry`() =
        assertEquals(order, moveWithinVisible(order, emptySet(), "a", -1))

    @Test
    fun `is a no-op when the id is already the last visible entry`() =
        assertEquals(order, moveWithinVisible(order, emptySet(), "d", 1))

    @Test
    fun `is a no-op when the id is missing from the order`() =
        assertEquals(order, moveWithinVisible(order, emptySet(), "z", 1))

    @Test
    fun `a hidden id between two visible ids keeps its own slot`() =
        // "b" is hidden; "a" moving right swaps with its next VISIBLE neighbor
        // "c", not the hidden "b" sitting between them.
        assertEquals(listOf("c", "b", "a", "d"), moveWithinVisible(order, setOf("b"), "a", 1))
}

class ResolveDockOrderTest {
    @Test
    fun `returns every enum entry in its declared order when nothing is persisted`() =
        assertEquals(DockNavId.entries, resolveDockOrder<DockNavId>(null))

    @Test
    fun `returns every enum entry in its declared order for a blank string`() =
        assertEquals(DockNavId.entries, resolveDockOrder<DockNavId>(""))

    @Test
    fun `keeps the persisted order and appends ids missing from it in enum order`() =
        assertEquals(
            listOf(
                DockNavId.SETTINGS,
                DockNavId.PHONE,
                DockNavId.APPS,
                DockNavId.MUSIC,
                DockNavId.NAVIGATION,
                DockNavId.BROWSER,
                DockNavId.ASSISTANT,
            ),
            resolveDockOrder<DockNavId>("SETTINGS\nPHONE"),
        )

    @Test
    fun `drops a persisted name no longer in the enum`() =
        assertEquals(
            DockNavId.entries,
            resolveDockOrder<DockNavId>(DockNavId.entries.joinToString("\n") { it.name } + "\nGHOST"),
        )

    @Test
    fun `drops a duplicate persisted name instead of repeating the entry`() =
        assertEquals(
            DockNavId.entries,
            resolveDockOrder<DockNavId>("PHONE\nPHONE\n" + DockNavId.entries.joinToString("\n") { it.name }),
        )
}

class ResolveDockHiddenTest {
    @Test
    fun `returns empty when nothing is persisted`() = assertEquals(emptySet(), resolveDockHidden<DockStatusId>(null))

    @Test
    fun `keeps every persisted name that still matches an enum entry`() =
        assertEquals(
            setOf(DockStatusId.GPS, DockStatusId.BATTERY),
            resolveDockHidden<DockStatusId>(setOf("GPS", "BATTERY")),
        )

    @Test
    fun `drops a persisted name no longer in the enum`() =
        assertEquals(setOf(DockStatusId.GPS), resolveDockHidden<DockStatusId>(setOf("GPS", "GHOST")))
}
