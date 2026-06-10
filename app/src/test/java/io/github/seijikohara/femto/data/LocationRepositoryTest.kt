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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

            val repository =
                LocationRepository(
                    application,
                    flowOf(LocationSettings.Default),
                    CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                )

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

            val repository =
                LocationRepository(
                    application,
                    flowOf(LocationSettings.Default),
                    CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                )

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

            val repository =
                LocationRepository(
                    application,
                    flowOf(LocationSettings.Default),
                    CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                )

            repository.locationFlow().filterNotNull().test {
                assertEquals(seedFix.latitude, awaitItem().latitude, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `keeps delivering live fixes after a settings change re-registers the listener`() =
        runTest {
            val settings = MutableStateFlow(LocationSettings.Default)
            val repository =
                LocationRepository(
                    application,
                    settings,
                    CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                )

            repository.locationFlow().filterNotNull().test {
                simulateFix(fakeLocation(latitude = 10.0))
                assertEquals(10.0, awaitItem().latitude, 0.0)

                // Swap the request parameters mid-collection: flatMapLatest must tear
                // down the old registration and bring up a new one without dropping
                // the subscriber.
                settings.value = LocationSettings.Default.copy(intervalMillis = 1_000L)

                simulateFix(fakeLocation(latitude = 20.0))
                // The swap re-runs the getLastKnownLocation seed, which may replay
                // the previous fix first; the live fix from the new registration
                // must follow it.
                var item = awaitItem()
                while (item.latitude != 20.0) item = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Dispatch a live fix to whatever listeners are currently registered for the
    // fix's provider (GPS here, matching the repository's GPS_PROVIDER registration).
    // The shadow posts the callback to the registration's main Looper, so idle it
    // to run the delivery (the coroutine Main override does not drive that Handler).
    private fun simulateFix(location: android.location.Location) {
        val locationManager = checkNotNull(application.getSystemService<LocationManager>())
        shadowOf(locationManager).simulateLocation(
            android.location.Location(location).apply { provider = LocationManager.GPS_PROVIDER },
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()
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
