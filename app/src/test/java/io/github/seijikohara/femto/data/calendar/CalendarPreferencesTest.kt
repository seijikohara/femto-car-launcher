package io.github.seijikohara.femto.data.calendar

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// The `preferencesDataStore` delegate behind CalendarPreferences is a
// process-wide singleton bound to the first Application's filesDir, while
// Robolectric hands each test method a fresh Application and temp dir. All
// round-trip steps therefore live in one test method, so the persisted file
// and the singleton never disagree across tests (mirrors DrawerPreferencesTest).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarPreferencesTest {
    private fun newStore() = CalendarPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun hidden_set_round_trips_add_and_remove() =
        runTest {
            val store = newStore()
            assertEquals(emptySet(), store.hiddenCalendarIds.first())

            store.setCalendarHidden(7L, hidden = true)
            store.setCalendarHidden(9L, hidden = true)
            assertEquals(setOf(7L, 9L), store.hiddenCalendarIds.first())

            store.setCalendarHidden(7L, hidden = false)
            assertEquals(setOf(9L), store.hiddenCalendarIds.first())
        }
}
