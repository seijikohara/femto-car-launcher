package io.github.seijikohara.femto.data.routing

import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.assertEquals

// The `preferencesDataStore` delegate is a process-wide singleton bound to the first Application's filesDir, while
// Robolectric hands each test method a fresh Application, so the round trip lives in one test method (as in
// CalendarPreferencesTest).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoutePreferencesTest {
    @Test
    fun `conditions round-trip and start as none`() =
        runTest {
            val store = RoutePreferences(ApplicationProvider.getApplicationContext())
            val initial = store.conditions.first()

            store.setConditions(RouteConditions(avoidMotorways = true, avoidTolls = true))
            val both = store.conditions.first()
            store.setConditions(RouteConditions(avoidTolls = true))

            assertEquals(RouteConditions(), initial)
            assertEquals(RouteConditions(avoidMotorways = true, avoidTolls = true), both)
            assertEquals(RouteConditions(avoidTolls = true), store.conditions.first())
        }

    // Power lost mid-write leaves a file DataStore cannot read.
    @Test
    fun `a corrupt store reads as no conditions`() =
        runTest {
            val conditions = flow<Preferences> { throw IOException("corrupt") }.routeConditions().first()

            assertEquals(RouteConditions(), conditions)
        }
}
