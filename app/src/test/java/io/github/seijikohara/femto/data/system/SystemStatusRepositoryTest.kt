@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data.system

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Looper
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
        // [registeredNetworkCallback]'s single() invariant holds.
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

            val status = firstStatusMatching(repository()) { it.bluetoothConnected }

            assertTrue(status.bluetoothConnected)
        }

    @Test
    fun `bluetoothEnabled tracks the adapter power state`() =
        runTest {
            // isEnabled() needs no permission, so the enabled flag is readable even
            // without BLUETOOTH_CONNECT; the dock lights the icon on it.
            setBluetoothEnabled(true)

            val status = firstStatusMatching(repository()) { it.bluetoothEnabled }

            assertTrue(status.bluetoothEnabled)
        }

    @Test
    fun `batteryPercent is the level over scale ratio from the battery broadcast`() =
        runTest {
            val status =
                firstStatusAfterBatteryBroadcast(batteryIntent(level = 50, scale = 100)) {
                    it.batteryPercent != null
                }

            assertEquals(50, status.batteryPercent)
        }

    @Test
    fun `batteryPercent clamps a level above scale to 100`() =
        runTest {
            // A malformed reading where level exceeds scale must not exceed 100;
            // the coerceIn(0, 100) in batteryFlow is the single clamp SSOT.
            val status =
                firstStatusAfterBatteryBroadcast(batteryIntent(level = 150, scale = 100)) {
                    it.batteryPercent != null
                }

            assertEquals(100, status.batteryPercent)
        }

    @Test
    fun `batteryPercent is null when the broadcast reports an unknown level`() =
        runTest {
            // level = -1 / scale = -1 is the framework's "unknown" sentinel; the
            // flow maps it to null so the dock never reads it as a dead 0%. The
            // AC plugged source marks the broadcast-derived emission, so waiting
            // on charging asserts against that emission rather than the null seed.
            val status =
                firstStatusAfterBatteryBroadcast(
                    batteryIntent(level = -1, scale = -1, plugged = BatteryManager.BATTERY_PLUGGED_AC),
                ) { it.charging }

            assertNull(status.batteryPercent)
        }

    @Test
    fun `charging is true when the broadcast reports a non-zero plugged source`() =
        runTest {
            val status =
                firstStatusAfterBatteryBroadcast(
                    batteryIntent(level = 50, scale = 100, plugged = BatteryManager.BATTERY_PLUGGED_AC),
                ) { it.charging }

            assertTrue(status.charging)
        }

    @Test
    fun `wifiConnected becomes true when the registered callback reports a validated wifi network`() =
        runTest {
            val connectivity = application.getSystemService<ConnectivityManager>()!!
            val shadowConnectivity = shadowOf(connectivity)

            repository().statusFlow().test {
                // The combine seeds wifi via onStart(false), so the first emission
                // is the default before any network capability arrives.
                assertFalse(awaitItem().wifiConnected)

                // Drive the repository's own NetworkCallback the way the framework
                // would, with a real validated WIFI capability set.
                val callback = registeredNetworkCallback(shadowConnectivity)
                callback.onCapabilitiesChanged(WIFI_NETWORK, validatedWifiCapabilities())

                assertTrue(awaitItem().wifiConnected)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `wifiSignalLevel maps the capability RSSI onto a graduated level`() =
        runTest {
            // wifiLevelFrom maps the RSSI linearly over the -100..-55 dBm window
            // onto 0..4: -66 dBm sits at (34 * 4) / 45 = 3. The capability must
            // carry a real (non-unspecified) RSSI for the level to be read at all.
            val connectivity = application.getSystemService<ConnectivityManager>()!!
            val shadowConnectivity = shadowOf(connectivity)

            repository().statusFlow().test {
                assertEquals(0, awaitItem().wifiSignalLevel)

                val callback = registeredNetworkCallback(shadowConnectivity)
                callback.onCapabilitiesChanged(WIFI_NETWORK, validatedWifiCapabilities(rssiDbm = -66))

                assertEquals(3, awaitItem().wifiSignalLevel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onlineFlow reports online when a validated network becomes available`() =
        runTest {
            val connectivity = application.getSystemService<ConnectivityManager>()!!
            val shadowConnectivity = shadowOf(connectivity)
            // No validated network present, so the synchronous seed reads offline; the
            // callback below drives the offline->online edge the map reloads on.
            shadowConnectivity.clearAllNetworks()

            repository().onlineFlow().test {
                assertFalse(awaitItem())

                val callback = registeredNetworkCallback(shadowConnectivity)
                callback.onAvailable(WIFI_NETWORK)

                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onlineFlow returns to offline when the last validated network is lost`() =
        runTest {
            val connectivity = application.getSystemService<ConnectivityManager>()!!
            val shadowConnectivity = shadowOf(connectivity)
            shadowConnectivity.clearAllNetworks()

            repository().onlineFlow().test {
                assertFalse(awaitItem())

                val callback = registeredNetworkCallback(shadowConnectivity)
                callback.onAvailable(WIFI_NETWORK)
                assertTrue(awaitItem())

                // Losing the only validated network empties the tracked set, so the
                // flow returns offline and a later reconnect is a fresh recovery edge.
                callback.onLost(WIFI_NETWORK)
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onlineFlow seeds true when a validated network is already active`() =
        runTest {
            val connectivity = application.getSystemService<ConnectivityManager>()!!
            val shadowConnectivity = shadowOf(connectivity)
            // Attach validated internet to the active network BEFORE subscribing, so the
            // synchronous seed reads online. This is the spurious-edge-avoidance property:
            // an online cold start must emit true first, not a false the map would treat
            // as an offline->online recovery and needlessly reload on.
            val activeNetwork = connectivity.activeNetwork!!
            shadowConnectivity.setNetworkCapabilities(activeNetwork, validatedWifiCapabilities())

            repository().onlineFlow().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cellularSignalLevel is null on a telephony-less unit`() =
        runTest {
            // setUp forces FEATURE_TELEPHONY off, so cellularLevelFlow never
            // registers a TelephonyCallback and stays null.
            val status = repository().statusFlow().first()

            assertNull(status.cellularSignalLevel)
        }

    @Test
    fun `cellularSignalLevel is null when READ_PHONE_STATE is not granted`() =
        runTest {
            // Telephony is present, but Robolectric grants no runtime permissions on
            // sdk 33, so telephonySignalFlow cannot register the callback and emits
            // null — the dock degrades to the binary cellular icon.
            shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY, true)

            val status = repository().statusFlow().first()

            assertNull(status.cellularSignalLevel)
        }

    @Test
    fun `gpsFixed becomes true on a GPS provider fix`() =
        runTest {
            val locations = MutableSharedFlow<Location?>(extraBufferCapacity = 1)

            repository(locations).statusFlow().test {
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

            repository(locations).statusFlow().test {
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

            repository(locations).statusFlow().test {
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

            val status = repository(locations).statusFlow().first()

            assertEquals(0, status.gpsSatelliteCount)
        }

    @Test
    fun `gpsSatelliteCount recovers after a fine-location grant refresh`() =
        runTest {
            // Denied at start, so gnssSatelliteFlow registers no callback and the
            // count stays 0. A fine-location grant landing while the launcher stays
            // foreground nudges refreshes; the flow must re-run its permission gate,
            // register the GnssStatus callback, and surface the satellite count.
            val locations = MutableSharedFlow<Location?>(extraBufferCapacity = 1)
            val locationManager = application.getSystemService<LocationManager>()!!

            repository(locations).statusFlow().test {
                assertEquals(0, awaitItem().gpsSatelliteCount)

                shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                SystemPermissionSignals.refreshes.emit(Unit)
                advanceUntilIdle()

                // simulateGnssStatus delivers to the now-registered callback via the
                // main executor, so idle the main looper to run the delivery.
                shadowOf(locationManager).simulateGnssStatus(gnssStatusWithUsedInFix(2))
                shadowOf(Looper.getMainLooper()).idle()

                assertEquals(2, awaitItem().gpsSatelliteCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Build a GnssStatus reporting [usedInFixCount] satellites used in the current
    // fix — the value gnssSatelliteFlow counts. The platform GnssStatus.Builder is
    // the supported construction path (GnssStatus has no public test constructor);
    // Robolectric's own GnssStatusBuilder is deprecated in favour of it.
    private fun gnssStatusWithUsedInFix(usedInFixCount: Int): GnssStatus =
        GnssStatus
            .Builder()
            .apply {
                repeat(usedInFixCount) { index ->
                    // addSatellite(constellationType, svid, cn0DbHz, elevation, azimuth,
                    // hasEphemeris, hasAlmanac, usedInFix, hasCarrierFrequency,
                    // carrierFrequencyHz, hasBasebandCn0DbHz, basebandCn0DbHz). Java
                    // method exposes no parameter names, so the arguments stay positional.
                    addSatellite(
                        GnssStatus.CONSTELLATION_GPS,
                        index + 1,
                        30f,
                        45f,
                        90f,
                        true,
                        true,
                        true,
                        false,
                        0f,
                        false,
                        0f,
                    )
                }
            }.build()

    /**
     * Build the repository with every flow on the test's own dispatcher. This is the
     * class-wide contract: an [UnconfinedTestDispatcher] backed by runTest's scheduler
     * runs each `callbackFlow` body eagerly on the test thread, so a receiver or
     * [ConnectivityManager.NetworkCallback] is registered — and visible — before the
     * collector observes the first emission, and `advanceTimeBy` still drives gpsFlow's
     * freshness delay.
     *
     * The alternative, letting the flows run on Dispatchers.Default and waiting for the
     * registration to appear, is what made this class flaky: the wait blocked a worker
     * of the very pool the registration needed, so on a loaded two-core CI runner it
     * could time out (`NoSuchElementException` from the fallback `single()`), and it
     * read Robolectric's non-synchronized shadow collections across threads.
     */
    private fun TestScope.repository(locationFlow: Flow<Location?> = emptyFlow()): SystemStatusRepository =
        SystemStatusRepository(
            context = application,
            locationFlow = locationFlow,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

    /**
     * Collect the first [SystemStatus] satisfying [predicate]. The meaningful reading
     * arrives on an emission after the onStart-seeded default; [Flow.first] with a
     * predicate skips the seed without weakening the assertion that follows.
     */
    private suspend fun firstStatusMatching(
        repository: SystemStatusRepository,
        predicate: (SystemStatus) -> Boolean,
    ): SystemStatus = repository.statusFlow().first(predicate)

    /**
     * Deliver [intent] as a regular ACTION_BATTERY_CHANGED broadcast once
     * statusFlow's battery receiver is registered, then collect the first
     * [SystemStatus] satisfying [predicate]. Production reads the system's
     * sticky broadcast replayed at registration, but Robolectric's only
     * sticky-seeding API is the deprecated Context.sendStickyBroadcast; a
     * post-registration broadcast exercises the same receiver path without
     * the deprecated call.
     */
    private suspend fun TestScope.firstStatusAfterBatteryBroadcast(
        intent: Intent,
        predicate: (SystemStatus) -> Boolean,
    ): SystemStatus {
        val status = async { repository().statusFlow().first(predicate) }
        // Run the collector far enough to register the receiver. Deterministic on the
        // test scheduler, where the old wall-clock poll raced an off-thread registration.
        runCurrent()
        application.sendBroadcast(intent)
        // The receiver was registered without a handler, so delivery is posted
        // to the paused main looper; idle() runs it.
        shadowOf(Looper.getMainLooper()).idle()
        return status.await()
    }

    /**
     * The single [ConnectivityManager.NetworkCallback] statusFlow / onlineFlow has
     * registered by the time the collector sees its first emission. [setUp] forces
     * telephony off so the Wi-Fi callback is the only one, making `single()` an
     * invariant rather than a race.
     */
    private fun registeredNetworkCallback(
        shadowConnectivity: ShadowConnectivityManager,
    ): ConnectivityManager.NetworkCallback = shadowConnectivity.networkCallbacks.single()

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
            // INTERNET + VALIDATED = real reachable internet: wifiFlow's VALIDATED check
            // and onlineFlow's INTERNET+VALIDATED seed both read this as online.
            shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
    }
}
