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
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Footer status cluster — Wi-Fi connectivity, Bluetooth connectivity,
 * battery percent and charging state.
 *
 * Each sub-signal lives in its own callback flow and they are combined
 * into a single [SystemStatus]. Bluetooth read requires `BLUETOOTH_CONNECT`
 * on Android 12+; when denied the flow emits `bluetoothConnected = false`
 * rather than throwing, so a missing permission degrades gracefully into
 * a dimmed icon.
 */
internal class SystemStatusRepository(
    private val context: Context,
) {
    private val connectivity: ConnectivityManager? = context.getSystemService()
    private val bluetoothManager: BluetoothManager? = context.getSystemService()

    fun statusFlow(): Flow<SystemStatus> =
        combine(
            // cellularFlow seeds its own initial value (false, or null on a
            // telephony-less unit), so it needs no onStart — adding one would
            // emit a spurious false->null transition before the seed.
            cellularFlow(),
            wifiFlow().onStart { emit(false) },
            bluetoothFlow().onStart { emit(false) },
            batteryFlow().onStart { emit(BatteryReading(percent = null, charging = false)) },
        ) { cellular, wifi, bt, battery ->
            SystemStatus(
                cellularConnected = cellular,
                wifiConnected = wifi,
                bluetoothConnected = bt,
                batteryPercent = battery.percent,
                charging = battery.charging,
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private fun wifiFlow(): Flow<Boolean> {
        val cm = connectivity ?: return flowOf(false)
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    trySend(
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    )
                }

                override fun onLost(network: Network) {
                    trySend(false)
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
}
