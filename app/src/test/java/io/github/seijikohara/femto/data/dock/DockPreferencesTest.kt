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
