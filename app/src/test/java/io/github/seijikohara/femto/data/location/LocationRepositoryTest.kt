@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data.location

import android.app.Application
import android.location.LocationManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.seijikohara.femto.data.system.SystemPermissionSignals
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.test.assertTrue

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

            // The first fix arrives before any live update exists, proving the stale
            // cache is forwarded on subscribe rather than the flow blocking until a
            // live fix arrives. (A provider with no cache yields a null from
            // getLastKnownLocation, which the repository swallows rather than
            // forwarding; filterNotNull here only satisfies the nullable signature.)
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

    @Test
    fun `re-registers the location listener when a permission-grant refresh lands`() =
        runTest {
            // A registration that fails while the grant is withheld leaves the shared
            // flow with a dead listener; a grant landing while the launcher stays
            // foreground must revive it. Seed the cached fix so every successful
            // registration forwards it, making each (re-)registration observable.
            val seedFix = fakeLocation()
            seedLastKnown(LocationManager.GPS_PROVIDER, seedFix)

            val repository =
                LocationRepository(
                    application,
                    flowOf(LocationSettings.Default),
                    CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                )

            val fixes = mutableListOf<android.location.Location>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    repository.locationFlow().filterNotNull().collect { fixes.add(it) }
                }
            advanceUntilIdle()

            // The initial registration forwarded the cached fix once.
            assertEquals(1, fixes.size)

            // A runtime grant landing while foreground nudges refreshes; the raw flow
            // must re-run its per-provider registration, re-seeding getLastKnownLocation.
            SystemPermissionSignals.refreshes.emit(Unit)
            advanceUntilIdle()

            // The re-registration forwards the cached fix a second time. Without the
            // refresh trigger the raw flow never re-runs, so the count stays at 1.
            assertEquals(2, fixes.size)

            collectJob.cancel()
        }

    @Test
    fun `emits a null no-fix signal when no provider has a cache`() =
        runTest {
            // Cold start on a device that has never fixed: both getLastKnownLocation
            // calls return null. The flow must still emit SOMETHING — HomeViewModel
            // combines nine sources and emits only once every one of them has spoken,
            // so a silent location source starves the whole dashboard into its
            // Initial state (no cards, music stuck on the connect CTA, map
            // unavailable) until the first live fix arrives. Reproduced on the
            // TBox-Mock-Play AVD against 6cc0bf71.
            val repository =
                LocationRepository(
                    application,
                    flowOf(LocationSettings.Default),
                    CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                )

            val emissions = mutableListOf<android.location.Location?>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    repository.locationFlow().collect { emissions.add(it) }
                }
            advanceUntilIdle()

            assertEquals(listOf<android.location.Location?>(null), emissions)

            collectJob.cancel()
        }

    @Test
    fun `does not emit the no-fix signal when a provider cache seeded a fix`() =
        runTest {
            // The null is a cold-start signal only: once anything seeded, a null
            // would blank the fix the other provider just delivered — the
            // "never emits null once seeded" contract LocationFreshness states.
            seedLastKnown(LocationManager.GPS_PROVIDER, fakeLocation(latitude = 10.0))

            val repository =
                LocationRepository(
                    application,
                    flowOf(LocationSettings.Default),
                    CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                )

            val emissions = mutableListOf<android.location.Location?>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    repository.locationFlow().collect { emissions.add(it) }
                }
            advanceUntilIdle()

            assertEquals(1, emissions.size)
            assertEquals(10.0, emissions.single()?.latitude)

            collectJob.cancel()
        }

    @Test
    fun `drops a fix whose boot clock sits behind the newest forwarded one`() =
        runTest {
            val settings = MutableStateFlow(LocationSettings.Default)
            val repository = repositoryFor(settings)

            val fixes = mutableListOf<android.location.Location>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    repository.locationFlow().filterNotNull().collect { fixes.add(it) }
                }

            simulateFix(fakeLocation(latitude = 20.0, elapsedRealtimeNanos = 10_000_000_000L))
            advanceUntilIdle()
            assertEquals(20.0, fixes.last().latitude, 0.0)

            // The replayed cached fix a re-subscribe seeds: the NETWORK cache is
            // routinely older than the GPS one. Forwarding it would teleport the map
            // camera back and drive the speed smoother's dt negative, which diverges
            // the hero numeral (issue #351).
            reRegister(settings)
            simulateFix(fakeLocation(latitude = 10.0, elapsedRealtimeNanos = 1_000_000_000L))
            advanceUntilIdle()

            assertTrue(fixes.none { it.latitude == 10.0 }, "the replayed older fix reached consumers")
            assertEquals(20.0, fixes.last().latitude, 0.0)

            collectJob.cancel()
        }

    @Test
    fun `a fix stamped from the future never pins the recency baseline`() =
        runTest {
            // A mock-location app (routine on AI boxes) stamping epoch nanos where
            // boot nanos belong. The baseline only ratchets forward and outlives
            // every collector, so anchoring on it would starve the whole location
            // stack — map, geocoding, weather, trip — until the process restarts.
            val settings = MutableStateFlow(LocationSettings.Default)
            val repository = repositoryFor(settings)

            val fixes = mutableListOf<android.location.Location>()
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    repository.locationFlow().filterNotNull().collect { fixes.add(it) }
                }

            simulateFix(fakeLocation(latitude = 30.0, elapsedRealtimeNanos = EPOCH_STAMP_NANOS))
            advanceUntilIdle()
            // Forwarded: a broken clock says nothing about where the vehicle is.
            assertEquals(30.0, fixes.last().latitude, 0.0)

            reRegister(settings)
            simulateFix(fakeLocation(latitude = 40.0, elapsedRealtimeNanos = 20_000_000_000L))
            advanceUntilIdle()

            assertEquals(40.0, fixes.last().latitude, 0.0, "a genuine fix was starved by the bogus stamp")

            collectJob.cancel()
        }

    private fun TestScope.repositoryFor(settings: MutableStateFlow<LocationSettings>): LocationRepository =
        LocationRepository(
            application,
            settings,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            nowElapsedRealtimeNanos = { UPTIME_NANOS },
        )

    // ShadowLocationManager drops a fix whose elapsedRealtimeNanos regresses below the
    // last one IT delivered, so an out-of-order fix cannot reach the repository through
    // a live registration at all. A settings change tears the registration down and
    // brings up a fresh one with no delivery history — which is also when production
    // replays the cached seed, i.e. the case under test.
    private fun reRegister(settings: MutableStateFlow<LocationSettings>) {
        settings.value = settings.value.copy(intervalMillis = settings.value.intervalMillis + 1)
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

    private companion object {
        // A plausible head-unit uptime: every test stamp below sits under it, so
        // the recency baseline advances the way it does on a real boot clock.
        const val UPTIME_NANOS = 60_000_000_000L

        // Wall-clock epoch nanos, the value a mis-stamping provider supplies where
        // boot nanos belong — five orders of magnitude past any real uptime.
        const val EPOCH_STAMP_NANOS = 1_700_000_000_000_000_000L
    }
}
