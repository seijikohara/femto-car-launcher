@file:OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest in cellularLevelFlow().

package io.github.seijikohara.femto.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Footer status cluster — Wi-Fi connectivity / signal strength, cellular
 * connectivity / signal strength, Bluetooth connectivity, battery percent
 * and charging state.
 *
 * Each sub-signal lives in its own callback flow and they are combined
 * into a single [SystemStatus]. Bluetooth read requires `BLUETOOTH_CONNECT`
 * on Android 12+; when denied the flow emits `bluetoothConnected = false`
 * rather than throwing, so a missing permission degrades gracefully into
 * a dimmed icon. Cellular signal strength requires `READ_PHONE_STATE`; when
 * denied the level flow emits null and the footer degrades to the binary
 * connected/disconnected icon.
 */
internal class SystemStatusRepository(
    private val context: Context,
) {
    private val connectivity: ConnectivityManager? = context.getSystemService()
    private val bluetoothManager: BluetoothManager? = context.getSystemService()
    private val telephonyManager: TelephonyManager? = context.getSystemService()

    fun statusFlow(): Flow<SystemStatus> =
        combine(
            connectivitySignals(),
            bluetoothFlow().onStart { emit(false) },
            batteryFlow().onStart { emit(BatteryReading(percent = null, charging = false)) },
            cellularLevelFlow().onStart { emit(null) },
        ) { connectivitySignals, bt, battery, cellularLevel ->
            SystemStatus(
                cellularConnected = connectivitySignals.cellularConnected,
                cellularSignalLevel = cellularLevel,
                wifiConnected = connectivitySignals.wifi.connected,
                wifiSignalLevel = connectivitySignals.wifi.level,
                bluetoothConnected = bt,
                batteryPercent = battery.percent,
                charging = battery.charging,
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    // Kotlin's typed combine overloads cover at most 5 flows; the cluster has
    // six sources once cellular signal strength joins. Stage the two reactive
    // ConnectivityManager-backed readings through a typed intermediate so the
    // compiler enforces arity and per-slot types — a future reorder fails to
    // compile instead of silently mismapping a positional values[i] cast.
    private fun connectivitySignals(): Flow<ConnectivitySignals> =
        combine(
            // cellularFlow seeds its own initial value (false, or null on a
            // telephony-less unit), so it needs no onStart — adding one would
            // emit a spurious false->null transition before the seed.
            cellularFlow(),
            wifiFlow().onStart { emit(WifiReading(connected = false, level = 0)) },
        ) { cellularConnected, wifi ->
            ConnectivitySignals(cellularConnected = cellularConnected, wifi = wifi)
        }

    private fun wifiFlow(): Flow<WifiReading> {
        val cm = connectivity ?: return flowOf(WifiReading(connected = false, level = 0))
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    val connected =
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    trySend(WifiReading(connected = connected, level = wifiLevelFrom(caps)))
                }

                override fun onLost(network: Network) {
                    trySend(WifiReading(connected = false, level = 0))
                }
            }
            val request = NetworkRequest
                .Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(request, callback)
            awaitClose { cm.unregisterNetworkCallback(callback) }
        }
    }

    // NetworkCapabilities.getSignalStrength() (API 30) returns the Wi-Fi RSSI in
    // dBm reactively via onCapabilitiesChanged, so no RSSI_CHANGED_ACTION poll is
    // needed. WifiManager.calculateSignalLevel(rssi, numLevels) maps it onto
    // 0..numLevels-1; SIGNAL_LEVEL_COUNT keeps that aligned with the shared
    // 0..MAX_SIGNAL_LEVEL graduated icon range so no rescale is required.
    @Suppress("DEPRECATION") // The numLevels overload is the only one that pins a fixed 0..4 range.
    private fun wifiLevelFrom(caps: NetworkCapabilities): Int {
        val rssi = caps.signalStrength
        if (rssi == Int.MIN_VALUE) return 0
        return WifiManager
            .calculateSignalLevel(rssi, SIGNAL_LEVEL_COUNT)
            .coerceIn(0, MAX_SIGNAL_LEVEL)
    }

    /**
     * Mobile-data connectivity, connectivity-only (no SIM / signal-strength
     * read, so no READ_PHONE_STATE). Emits null on a device with no telephony
     * feature so the footer hides the indicator rather than showing a
     * permanently-disconnected one. Mirrors [wifiFlow] for the validated check.
     */
    private fun cellularFlow(): Flow<Boolean?> {
        val cm = connectivity ?: return flowOf(null)
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return flowOf(null)
        }
        return callbackFlow {
            // Seed disconnected so combine has an initial cellular value even when
            // no cellular network is currently present to fire the callback.
            trySend(false)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    trySend(
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    )
                }

                override fun onLost(network: Network) {
                    trySend(false)
                }
            }
            val request = NetworkRequest
                .Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            cm.registerNetworkCallback(request, callback)
            awaitClose { cm.unregisterNetworkCallback(callback) }
        }
    }

    /**
     * Graduated cellular signal strength (0..4), or null when the device has no
     * telephony feature, the `READ_PHONE_STATE` grant is withheld, or no reading
     * has arrived. Uses [TelephonyCallback.SignalStrengthsListener] unconditionally
     * (minSdk 33), reading [SignalStrength.getLevel] reactively on a background
     * executor.
     *
     * Re-registers on a late permission grant, mirroring [bluetoothFlow]'s use of
     * [SystemPermissionSignals.refreshes]: that signal fires when a runtime
     * permission result lands. A `READ_PHONE_STATE` grant from the in-app dialog
     * leaves the activity PAUSED (not STOPPED), so the level would otherwise stay
     * null until the next signal change. Unlike Bluetooth the level only arrives
     * asynchronously via the callback, so each refresh restarts the underlying
     * callbackFlow (which re-checks the permission and registers when now granted)
     * rather than re-reading a synchronous value. The outer [statusFlow] keeps its
     * `distinctUntilChanged`, so an unchanged value is suppressed.
     */
    private fun cellularLevelFlow(): Flow<Int?> {
        val tm = telephonyManager
        if (tm == null ||
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        ) {
            return flowOf(null)
        }
        // onStart(Unit) seeds an initial registration; each later refresh restarts it.
        return SystemPermissionSignals.refreshes
            .onStart { emit(Unit) }
            .flatMapLatest { telephonySignalFlow(tm) }
    }

    @SuppressLint("MissingPermission") // Permission is checked via hasReadPhoneStatePermission().
    private fun telephonySignalFlow(tm: TelephonyManager): Flow<Int?> =
        callbackFlow {
            // Without READ_PHONE_STATE the listener cannot be registered, so emit
            // null and stay idle until a later permission grant re-runs this flow.
            if (!context.hasReadPhoneStatePermission()) {
                trySend(null)
                awaitClose { }
                return@callbackFlow
            }
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    trySend(signalStrength.level.coerceIn(0, MAX_SIGNAL_LEVEL))
                }
            }
            tm.registerTelephonyCallback(context.mainExecutor, callback)
            awaitClose { tm.unregisterTelephonyCallback(callback) }
        }

    /**
     * Connected-state stream merging two re-read triggers:
     * - [bluetoothBroadcastFlow] fires on BT adapter / connection broadcasts.
     * - [SystemPermissionSignals.refreshes] fires when a runtime permission
     *   result lands. A `BLUETOOTH_CONNECT` grant from the in-app dialog leaves
     *   the activity PAUSED (not STOPPED), so without this nudge the indicator
     *   would stay dimmed until the next BT broadcast. The outer
     *   [statusFlow] keeps its `distinctUntilChanged`, so an unchanged value is
     *   suppressed.
     */
    private fun bluetoothFlow(): Flow<Boolean> =
        merge(
            bluetoothBroadcastFlow(),
            SystemPermissionSignals.refreshes.map { readBluetoothConnected(bluetoothManager?.adapter) },
        )

    private fun bluetoothBroadcastFlow(): Flow<Boolean> =
        callbackFlow {
            val adapter: BluetoothAdapter? = bluetoothManager?.adapter
            trySend(readBluetoothConnected(adapter))

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    intent: Intent?,
                ) {
                    trySend(readBluetoothConnected(adapter))
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            awaitClose { context.unregisterReceiver(receiver) }
        }

    @SuppressLint("MissingPermission") // Permission is checked inside hasBluetoothConnect().
    private fun readBluetoothConnected(adapter: BluetoothAdapter?): Boolean {
        if (adapter == null || !adapter.isEnabled) return false
        if (!hasBluetoothConnect()) return false
        val devices = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT) ?: emptyList()
        return devices.isNotEmpty() ||
            adapter.getProfileConnectionState(BluetoothProfile.HEADSET) ==
            BluetoothAdapter.STATE_CONNECTED ||
            adapter.getProfileConnectionState(BluetoothProfile.A2DP) ==
            BluetoothAdapter.STATE_CONNECTED
    }

    private fun hasBluetoothConnect(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun batteryFlow(): Flow<BatteryReading> =
        callbackFlow {
            val emit: (Intent?) -> Unit = { intent ->
                if (intent != null) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    // Null when the reading is unavailable; clamp the valid value here
                    // so this SSOT is the only place the 0..100 range is enforced.
                    val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    trySend(BatteryReading(percent = percent, charging = plugged != 0))
                }
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    intent: Intent?,
                ) = emit(intent)
            }
            val sticky = ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            emit(sticky)
            awaitClose { context.unregisterReceiver(receiver) }
        }

    private data class BatteryReading(
        val percent: Int?,
        val charging: Boolean,
    )

    private data class WifiReading(
        val connected: Boolean,
        val level: Int,
    )

    // Typed intermediate for the first-stage combine of the two reactive
    // ConnectivityManager readings, mirroring HomeViewModel.CoreSignals.
    private data class ConnectivitySignals(
        val cellularConnected: Boolean?,
        val wifi: WifiReading,
    )

    private companion object {
        // Top index of the shared 0..4 graduated signal range used by both the
        // Wi-Fi level and the cellular SignalStrength.level (which already reports
        // 0 (NONE) .. 4 (GREAT)). The DashboardFooter icon ramp keys off the same
        // range.
        const val MAX_SIGNAL_LEVEL = 4

        // Number of buckets WifiManager.calculateSignalLevel(rssi, numLevels) maps
        // the RSSI onto; numLevels - 1 is the top index, so this pins the Wi-Fi
        // output to the same 0..MAX_SIGNAL_LEVEL range as cellular.
        const val SIGNAL_LEVEL_COUNT = MAX_SIGNAL_LEVEL + 1
    }
}
