@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data.location

import android.app.Application
import android.location.LocationManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The source hands the platform a direct executor, so a simulated fix resumes the request inside simulateLocation and
// the test scheduler only has to run the continuation.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformCurrentFixSourceTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val locationManager: LocationManager = checkNotNull(application.getSystemService())
    private val source = PlatformCurrentFixSource(application, timeLimitMs = TIME_LIMIT_MS)

    // The shadow starts with the network provider switched off, and a provider switched off answers at once.
    @Before
    fun setUp() {
        LocationProviders.forEach { shadowOf(locationManager).setProviderEnabled(it, true) }
    }

    @Test
    fun `the receiver's fix is the answer`() =
        runTest {
            val request = async { source.currentFixOrNull() }
            runCurrent()

            simulateFix(LocationManager.GPS_PROVIDER, latitude = 10.0, longitude = 20.0)

            assertEquals(CurrentFix(latitude = 10.0, longitude = 20.0), request.await())
        }

    @Test
    fun `the network's fix is the answer while the receiver has none`() =
        runTest {
            // Underground, or under a coarse-only grant, only the network provider answers.
            val request = async { source.currentFixOrNull() }
            runCurrent()

            simulateFix(LocationManager.NETWORK_PROVIDER, latitude = 10.0, longitude = 20.0)

            assertEquals(CurrentFix(latitude = 10.0, longitude = 20.0), request.await())
        }

    @Test
    fun `the first fix to arrive is the answer`() =
        runTest {
            val request = async { source.currentFixOrNull() }
            runCurrent()

            simulateFix(LocationManager.NETWORK_PROVIDER, latitude = 10.0, longitude = 20.0)
            simulateFix(LocationManager.GPS_PROVIDER, latitude = 11.0, longitude = 21.0)

            assertEquals(CurrentFix(latitude = 10.0, longitude = 20.0), request.await())
        }

    @Test
    fun `a fix without real coordinates is no answer`() =
        runTest {
            val request = async { source.currentFixOrNull() }
            runCurrent()

            simulateFix(LocationManager.GPS_PROVIDER, latitude = Double.NaN, longitude = 20.0)
            simulateFix(LocationManager.NETWORK_PROVIDER, latitude = 10.0, longitude = 20.0)

            assertEquals(CurrentFix(latitude = 10.0, longitude = 20.0), request.await())
        }

    @Test
    fun `no fix within the time limit is no answer`() =
        runTest {
            val request = async { source.currentFixOrNull() }

            advanceTimeBy(TIME_LIMIT_MS + 1)

            assertNull(request.await())
        }

    @Test
    fun `a request still waits just short of the time limit`() =
        runTest {
            val request = async { source.currentFixOrNull() }

            advanceTimeBy(TIME_LIMIT_MS - 1)

            assertEquals(true, request.isActive)
            request.cancel()
        }

    @Test
    fun `providers switched off are no answer at once`() =
        runTest {
            LocationProviders.forEach { shadowOf(locationManager).setProviderEnabled(it, false) }

            val request = async { source.currentFixOrNull() }
            runCurrent()

            assertEquals(true to null, request.isCompleted to request.await())
        }

    @Test
    fun `one provider switched off leaves the other to answer`() =
        runTest {
            shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
            val request = async { source.currentFixOrNull() }
            runCurrent()

            simulateFix(LocationManager.NETWORK_PROVIDER, latitude = 10.0, longitude = 20.0)

            assertEquals(CurrentFix(latitude = 10.0, longitude = 20.0), request.await())
        }

    @Test
    fun `a request asks every provider`() =
        runTest {
            val request = async { source.currentFixOrNull() }
            runCurrent()

            assertEquals(LocationProviders.map { 1 }, pendingRequests())
            request.cancel()
        }

    @Test
    fun `cancelling the caller withdraws the request from every provider`() =
        runTest {
            val request = async { source.currentFixOrNull() }
            runCurrent()

            request.cancel()
            runCurrent()

            assertEquals(LocationProviders.map { 0 }, pendingRequests())
        }

    @Test
    fun `the answer withdraws the request from the other provider`() =
        runTest {
            val request = async { source.currentFixOrNull() }
            runCurrent()

            simulateFix(LocationManager.NETWORK_PROVIDER, latitude = 10.0, longitude = 20.0)
            request.await()

            assertEquals(LocationProviders.map { 0 }, pendingRequests())
        }

    @Test
    fun `running out of time withdraws the request from every provider`() =
        runTest {
            val request = async { source.currentFixOrNull() }

            advanceTimeBy(TIME_LIMIT_MS + 1)
            request.await()

            assertEquals(LocationProviders.map { 0 }, pendingRequests())
        }

    // How many requests each provider still holds, in LocationProviders' order: the shadow keeps a request for the
    // current location among a provider's listeners until it is answered or cancelled.
    private fun pendingRequests(): List<Int> =
        LocationProviders.map { shadowOf(locationManager).getLocationUpdateListeners(it).size }

    private fun simulateFix(
        provider: String,
        latitude: Double,
        longitude: Double,
    ) = shadowOf(locationManager).simulateLocation(
        provider,
        fakeLocation(latitude = latitude, longitude = longitude, provider = provider),
    )

    private companion object {
        const val TIME_LIMIT_MS = 25_000L
    }
}
