@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data

import android.app.Application
import android.location.LocationManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// rawLocationFlow registers its listener on getMainLooper, so it runs on
// Dispatchers.Main.immediate. setUp redirects Main to an UnconfinedTestDispatcher;
// its eager execution runs the shareIn fan-out and the on-subscribe
// getLastKnownLocation seed synchronously, so the seeded fix is observable without
// advancing a clock and no explicit scheduler linkage is required.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationRepositoryTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits the seeded last-known fix before any live update`() =
        runTest {
            // Seed only the cached fix; no simulateLocation call, so the only fix the
            // flow can ever surface is the on-subscribe getLastKnownLocation seed.
            val seedFix = fakeLocation()
            seedLastKnown(LocationManager.GPS_PROVIDER, seedFix)

            val repository = LocationRepository(application, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            // The first non-null fix arrives before any live update exists, proving
            // the stale cache is forwarded on subscribe rather than the flow blocking
            // until a live fix arrives. (A provider with no cache yields a null, which
            // consumers treat as "no fix"; filterNotNull mirrors that contract.)
            val emitted = repository.locationFlow().filterNotNull().first()

            assertEquals(seedFix.latitude, emitted.latitude, 0.0)
            assertEquals(seedFix.longitude, emitted.longitude, 0.0)
        }

    @Test
    fun `seeds the network-provider last-known fix when only coarse is available`() =
        runTest {
            // A coarse-only ("Approximate") grant feeds the launcher through
            // NETWORK_PROVIDER; the seed path must honor it the same as GPS.
            val seedFix = fakeLocation()
            seedLastKnown(LocationManager.NETWORK_PROVIDER, seedFix)

            val repository = LocationRepository(application, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            val emitted = repository.locationFlow().filterNotNull().first()

            assertEquals(seedFix.latitude, emitted.latitude, 0.0)
        }

    @Test
    fun `replays the seeded fix to a late subscriber`() =
        runTest {
            // replay = 1 on the shared flow must hand the most recent (here, the
            // seeded) fix to a subscriber that arrives after the first one.
            val seedFix = fakeLocation()
            seedLastKnown(LocationManager.GPS_PROVIDER, seedFix)

            val repository = LocationRepository(application, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            repository.locationFlow().filterNotNull().test {
                assertEquals(seedFix.latitude, awaitItem().latitude, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ShadowLocationManager.setLastKnownLocation is the only seeding hook the shadow
    // exposes; its @Deprecated marker mirrors the platform setter and has no
    // non-deprecated replacement under Robolectric, so the suppression is local.
    @Suppress("DEPRECATION")
    private fun seedLastKnown(
        provider: String,
        location: android.location.Location,
    ) {
        val locationManager = checkNotNull(application.getSystemService<LocationManager>())
        shadowOf(locationManager).setLastKnownLocation(provider, location)
    }
}
