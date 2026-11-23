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
    fun `each update changes the stored conditions it names, starting from none`() =
        runTest {
            val store = RoutePreferences(ApplicationProvider.getApplicationContext())
            val initial = store.conditions.first()

            store.update { it.copy(avoidMotorways = true) }
            store.update { it.copy(avoidTolls = true) }
            val both = store.conditions.first()
            store.update { it.copy(avoidMotorways = false) }

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
