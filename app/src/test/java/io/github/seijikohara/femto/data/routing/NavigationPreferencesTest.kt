package io.github.seijikohara.femto.data.routing

import android.content.Context
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

// The navigationDataStore delegate is a process-wide singleton bound to the first Application's filesDir, while
// Robolectric hands each test method a fresh Application (mirrors VideoPreferencesTest), so every test clears the raw
// store first.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NavigationPreferencesTest {
    @Test
    fun `an empty store reads the defaults`() =
        runTest {
            val store = clearedStore()

            assertEquals(NavigationSettings.Default, store.settings.first())
        }

    // The route preview did all of this before the settings existed; a driver who never opens them sees no change.
    // Drawing the vehicle on the line and the turn card came after them: both on, the card with the turn after next and
    // without "continue straight".
    @Test
    fun `the defaults keep every part of the route preview on and show the arrival time`() {
        assertEquals(
            NavigationSettings(
                rerouteAutomatically = true,
                suggestOtherRoutes = true,
                restoreRouteAfterRestart = true,
                routeRowTime = RouteRowTime.ARRIVAL,
                vehicleOnRouteLine = true,
                turnCard = true,
                turnCardNextAfter = true,
                turnCardStraight = false,
                spokenPrompts = true,
            ),
            NavigationSettings.Default,
        )
    }

    @Test
    fun `every setting round-trips`() =
        runTest {
            val store = clearedStore()

            store.setRerouteAutomatically(false)
            store.setSuggestOtherRoutes(false)
            store.setRestoreRouteAfterRestart(false)
            store.setRouteRowTime(RouteRowTime.REMAINING)
            store.setVehicleOnRouteLine(false)
            store.setTurnCard(false)
            store.setTurnCardNextAfter(false)
            store.setTurnCardStraight(true)
            store.setSpokenPrompts(false)

            assertEquals(
                NavigationSettings(false, false, false, RouteRowTime.REMAINING, false, false, false, true, false),
                store.settings.first(),
            )
        }

    // A later build may add a choice; an older one that reads it shows the arrival time rather than fail.
    @Test
    fun `a route row time this build does not know reads as the arrival time`() =
        runTest {
            val store = clearedStore()

            ApplicationProvider
                .getApplicationContext<Context>()
                .navigationDataStore
                .edit { it[stringPreferencesKey("route_row_time")] = "DISTANCE_ONLY" }

            assertEquals(RouteRowTime.ARRIVAL, store.settings.first().routeRowTime)
        }

    @Test
    fun `a reset restores every setting`() =
        runTest {
            val store = clearedStore()
            store.setRerouteAutomatically(false)
            store.setSuggestOtherRoutes(false)
            store.setRestoreRouteAfterRestart(false)
            store.setRouteRowTime(RouteRowTime.REMAINING)
            store.setVehicleOnRouteLine(false)
            store.setTurnCard(false)
            store.setTurnCardNextAfter(false)
            store.setTurnCardStraight(true)
            store.setSpokenPrompts(false)

            store.resetToDefaults()

            assertEquals(NavigationSettings.Default, store.settings.first())
        }

    private suspend fun clearedStore(): NavigationPreferences {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.navigationDataStore.edit { it.clear() }
        return NavigationPreferences(context)
    }
}
