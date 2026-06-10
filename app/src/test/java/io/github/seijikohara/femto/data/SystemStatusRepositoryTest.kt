@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import org.robolectric.shadows.ShadowWifiManager
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
    fun `bluetoothConnected falls back to the adapter power state when BLUETOOTH_CONNECT is not granted`() =
        runTest {
            // Robolectric grants no runtime permissions on sdk 33, so the
            // connected-device APIs are unreadable. isEnabled() needs no permission,
            // so with the adapter powered on readBluetooth reports BT-on rather than a
            // misleading "disconnected". The bluetoothFlow seeds false via onStart, so
            // wait past the seed for the broadcast-driven read; a regressed impl that
            // returns false here would hang the predicate.
            setBluetoothEnabled(true)

            val status = firstStatusMatching(SystemStatusRepository(application)) { it.bluetoothConnected }

            assertTrue(status.bluetoothConnected)
        }

    @Test
    fun `bluetoothEnabled tracks the adapter power state`() =
        runTest {
            // isEnabled() needs no permission, so the enabled flag is readable even
            // without BLUETOOTH_CONNECT; the dock lights the icon on it.
            setBluetoothEnabled(true)

            val status = firstStatusMatching(SystemStatusRepository(application)) { it.bluetoothEnabled }

            assertTrue(status.bluetoothEnabled)
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
            // flow maps it to null so the dock never reads it as a dead 0%. The
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

    @Test
    fun `wifiSignalLevel maps the capability RSSI onto a graduated level`() =
        runTest {
            // ShadowWifiManager.calculateSignalLevel(rssi, numLevels) returns
            // floor(percent * (numLevels - 1)); 0.75 over the 5-bucket range pins
            // the top index to 3. The capability must carry a real (non-unspecified)
            // RSSI for wifiLevelFrom to consult the level at all.
            ShadowWifiManager.setSignalLevelInPercent(0.75f)
            val connectivity = application.getSystemService<ConnectivityManager>()!!
            val shadowConnectivity = shadowOf(connectivity)

            SystemStatusRepository(application).statusFlow().test {
                assertEquals(0, awaitItem().wifiSignalLevel)

                val callback = awaitRegisteredNetworkCallback(shadowConnectivity)
                callback.onCapabilitiesChanged(WIFI_NETWORK, validatedWifiCapabilities(rssiDbm = -55))

                assertEquals(3, awaitItem().wifiSignalLevel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cellularSignalLevel is null on a telephony-less unit`() =
        runTest {
            // setUp forces FEATURE_TELEPHONY off, so cellularLevelFlow never
            // registers a TelephonyCallback and stays null.
            val status = SystemStatusRepository(application).statusFlow().first()

            assertNull(status.cellularSignalLevel)
        }

    @Test
    fun `cellularSignalLevel is null when READ_PHONE_STATE is not granted`() =
        runTest {
            // Telephony is present, but Robolectric grants no runtime permissions on
            // sdk 33, so telephonySignalFlow cannot register the callback and emits
            // null — the dock degrades to the binary cellular icon.
            shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)

            val status = SystemStatusRepository(application).statusFlow().first()

            assertNull(status.cellularSignalLevel)
        }

    @Test
    fun `gpsFixed becomes true on a GPS provider fix`() =
        runTest {
            val locations = MutableSharedFlow<Location?>(extraBufferCapacity = 1)

            gpsRepository(locations).statusFlow().test {
                // gpsFlow seeds false via onStart before any fix arrives.
                assertFalse(awaitItem().gpsFixed)

                locations.emit(fakeLocation(provider = LocationManager.GPS_PROVIDER))

                assertTrue(awaitItem().gpsFixed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `gpsFixed flips back to false after the freshness window`() =
        runTest {
            val locations = MutableSharedFlow<Location?>(extraBufferCapacity = 1)

            gpsRepository(locations).statusFlow().test {
                assertFalse(awaitItem().gpsFixed)

                locations.emit(fakeLocation(provider = LocationManager.GPS_PROVIDER))
                assertTrue(awaitItem().gpsFixed)

                // No follow-up fix: once the 30 s freshness window elapses the
                // indicator returns to searching.
                advanceTimeBy(SystemStatusRepository.GPS_FIX_FRESHNESS_MS + 1L)
                assertFalse(awaitItem().gpsFixed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `gpsFixed stays false for a NETWORK provider fix`() =
        runTest {
            val locations = MutableSharedFlow<Location?>(extraBufferCapacity = 1)

            gpsRepository(locations).statusFlow().test {
                assertFalse(awaitItem().gpsFixed)

                // A cell-tower / Wi-Fi fix centres the map but is not a GPS lock, so
                // gpsFlow's provider gate drops it and the indicator stays searching.
                locations.emit(fakeLocation(provider = LocationManager.NETWORK_PROVIDER))
                advanceTimeBy(SystemStatusRepository.GPS_FIX_FRESHNESS_MS + 1L)

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `gpsSatelliteCount is zero without the fine location grant`() =
        runTest {
            // gnssSatelliteFlow gates on ACCESS_FINE_LOCATION; Robolectric grants no
            // runtime permissions on sdk 33, so it stays 0 (the searching readout)
            // rather than registering a GnssStatus callback.
            val locations = MutableSharedFlow<Location?>(extraBufferCapacity = 1)

            val status = gpsRepository(locations).statusFlow().first()

            assertEquals(0, status.gpsSatelliteCount)
        }

    // statusFlow runs its combine on the injected dispatcher; an UnconfinedTestDispatcher
    // backed by runTest's scheduler lets advanceTimeBy drive gpsFlow's freshness delay.
    private fun TestScope.gpsRepository(locationFlow: MutableSharedFlow<Location?>): SystemStatusRepository =
        SystemStatusRepository(
            context = application,
            locationFlow = locationFlow,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

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

    // Drive the default adapter's power state through its shadow. isEnabled() needs
    // no permission, so this is the lever readBluetooth falls back to when
    // BLUETOOTH_CONNECT is withheld.
    private fun setBluetoothEnabled(enabled: Boolean) {
        val adapter = application.getSystemService<BluetoothManager>()!!.adapter
        shadowOf(adapter).setEnabled(enabled)
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

    private fun validatedWifiCapabilities(rssiDbm: Int? = null): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().apply {
            shadowOf(this).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            // NetworkCapabilities.setSignalStrength(int) is hidden in the public SDK
            // but present at runtime under Robolectric; reflection is the only way to
            // seed a non-unspecified RSSI so wifiLevelFrom reads it.
            rssiDbm?.let { rssi ->
                NetworkCapabilities::class.java
                    .getMethod("setSignalStrength", Int::class.javaPrimitiveType)
                    .invoke(this, rssi)
            }
        }

    private companion object {
        val WIFI_NETWORK: Network = ShadowNetwork.newInstance(1)
        const val CALLBACK_POLL_INTERVAL_MS = 5L
        val CALLBACK_POLL_TIMEOUT_NANOS = 2_000_000_000L // 2 s real-time ceiling.
    }
}
