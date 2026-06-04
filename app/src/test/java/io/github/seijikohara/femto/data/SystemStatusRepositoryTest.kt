package io.github.seijikohara.femto.data

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SystemStatusRepositoryTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // statusFlow's cellularFlow registers no NetworkCallback when the device
        // reports no telephony feature, so forcing it off keeps these tests
        // deterministic — the only registered callback is the Wi-Fi one, and
        // [awaitRegisteredNetworkCallback]'s single() invariant holds.
        shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, false)
    }

    @Test
    fun `bluetoothConnected is false when BLUETOOTH_CONNECT is not granted`() =
        runTest {
            // Robolectric grants no runtime permissions by default on sdk 33, so
            // readBluetoothConnected short-circuits to false rather than touching
            // the adapter. The footer renders a dimmed BT icon instead of throwing.
            val status = SystemStatusRepository(application).statusFlow().first()

            assertFalse(status.bluetoothConnected)
        }

    @Test
    fun `batteryPercent is the level over scale ratio from the sticky broadcast`() =
        runTest {
            // The sticky ACTION_BATTERY_CHANGED is the only place a battery reading
            // arrives; registerReceiver replays it. statusFlow seeds a null default
            // via onStart and runs the receiver on Dispatchers.Default, so the
            // sticky-derived percent lands on the emission after the seed.
            application.sendStickyBroadcast(batteryIntent(level = 50, scale = 100))

            val status = firstStatusMatching(SystemStatusRepository(application)) { it.batteryPercent != null }

            assertEquals(50, status.batteryPercent)
        }

    @Test
    fun `batteryPercent clamps a level above scale to 100`() =
        runTest {
            // A malformed reading where level exceeds scale must not exceed 100;
            // the coerceIn(0, 100) in batteryFlow is the single clamp SSOT.
            application.sendStickyBroadcast(batteryIntent(level = 150, scale = 100))

            val status = firstStatusMatching(SystemStatusRepository(application)) { it.batteryPercent != null }

            assertEquals(100, status.batteryPercent)
        }

    @Test
    fun `batteryPercent is null when the sticky reports an unknown level`() =
        runTest {
            // level = -1 / scale = -1 is the framework's "unknown" sentinel; the
            // flow maps it to null so the footer never reads it as a dead 0%. The
            // seeded default is also null, so collecting the first emission is
            // sufficient and there is no later non-null reading to wait for.
            application.sendStickyBroadcast(batteryIntent(level = -1, scale = -1))

            val status = SystemStatusRepository(application).statusFlow().first()

            assertNull(status.batteryPercent)
        }

    @Test
    fun `charging is true when the sticky reports a non-zero plugged source`() =
        runTest {
            application.sendStickyBroadcast(
                batteryIntent(level = 50, scale = 100, plugged = BatteryManager.BATTERY_PLUGGED_AC),
            )

            val status = firstStatusMatching(SystemStatusRepository(application)) { it.charging }

            assertTrue(status.charging)
        }

    @Test
    fun `wifiConnected becomes true when the registered callback reports a validated wifi network`() =
        runTest {
            val connectivity = application.getSystemService<ConnectivityManager>()!!
            val shadowConnectivity = shadowOf(connectivity)

            SystemStatusRepository(application).statusFlow().test {
                // The combine seeds wifi via onStart(false), so the first emission
                // is the default before any network capability arrives.
                assertFalse(awaitItem().wifiConnected)

                // statusFlow runs on Dispatchers.Default, so the repository's own
                // NetworkCallback is registered on another thread; wait for it to
                // appear before driving it the way the framework would, with a real
                // validated WIFI capability set.
                val callback = awaitRegisteredNetworkCallback(shadowConnectivity)
                callback.onCapabilitiesChanged(WIFI_NETWORK, validatedWifiCapabilities())

                assertTrue(awaitItem().wifiConnected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Collect the first [SystemStatus] satisfying [predicate]. statusFlow runs its
     * callback flows on Dispatchers.Default, so the meaningful reading arrives on an
     * emission after the onStart-seeded default; [Flow.first] with a predicate skips
     * the seed without weakening the assertion that follows.
     */
    private suspend fun firstStatusMatching(
        repository: SystemStatusRepository,
        predicate: (SystemStatus) -> Boolean,
    ): SystemStatus = repository.statusFlow().first(predicate)

    /**
     * Wait until statusFlow has registered its [ConnectivityManager.NetworkCallback]
     * on the Default dispatcher. The poll uses real wall-clock time on a real
     * dispatcher so it advances independently of the runTest virtual scheduler, which
     * skips delays. Polling avoids a flaky [List.single] race against the off-thread
     * registration.
     */
    private suspend fun awaitRegisteredNetworkCallback(
        shadowConnectivity: ShadowConnectivityManager,
    ): ConnectivityManager.NetworkCallback =
        withContext(Dispatchers.Default) {
            val deadline = System.nanoTime() + CALLBACK_POLL_TIMEOUT_NANOS
            while (System.nanoTime() < deadline) {
                shadowConnectivity.networkCallbacks.firstOrNull()?.let { return@withContext it }
                Thread.sleep(CALLBACK_POLL_INTERVAL_MS)
            }
            shadowConnectivity.networkCallbacks.single()
        }

    private fun batteryIntent(
        level: Int,
        scale: Int,
        plugged: Int = 0,
    ): Intent =
        Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, level)
            putExtra(BatteryManager.EXTRA_SCALE, scale)
            putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
        }

    private fun validatedWifiCapabilities(): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().apply {
            shadowOf(this).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

    private companion object {
        val WIFI_NETWORK: Network = ShadowNetwork.newInstance(1)
        const val CALLBACK_POLL_INTERVAL_MS = 5L
        val CALLBACK_POLL_TIMEOUT_NANOS = 2_000_000_000L // 2 s real-time ceiling.
    }
}
