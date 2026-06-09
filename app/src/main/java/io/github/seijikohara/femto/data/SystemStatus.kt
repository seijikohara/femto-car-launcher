package io.github.seijikohara.femto.data

import androidx.compose.runtime.Immutable

/**
 * Read-only system connectivity / power surface for the footer status cluster.
 *
 * All fields default to the "off / unknown" state in [Initial] so a fresh
 * subscriber renders a neutral cluster while the repository's first emit
 * is in flight.
 */
@Immutable
data class SystemStatus(
    // Null on a device with no telephony feature (e.g. a Wi-Fi-only AI box), so
    // the footer hides the cellular indicator entirely rather than showing a
    // permanently-disconnected one; true/false once telephony is present.
    val cellularConnected: Boolean?,
    // 0..4 graduated cellular signal level, or null when telephony is absent, the
    // READ_PHONE_STATE grant is withheld, or no reading has arrived. When null but
    // cellularConnected is non-null, the footer degrades to the binary icon.
    val cellularSignalLevel: Int?,
    val wifiConnected: Boolean,
    // 0..4 graduated Wi-Fi signal level. Defaults to 0 (no bars) until the first
    // capability reading arrives; the footer uses this for the graduated icon.
    val wifiSignalLevel: Int,
    // Whether the Bluetooth adapter is powered on. The footer lights the BT icon
    // on this (an enabled adapter should not read as off), separate from whether a
    // device is actually connected.
    val bluetoothEnabled: Boolean,
    // Whether a device is actively connected (HEADSET / A2DP / GATT). Drives the
    // "connected" glyph variant; unknowable without BLUETOOTH_CONNECT, where it
    // falls back to the enabled state.
    val bluetoothConnected: Boolean,
    // Null until the first battery reading arrives, or on battery-less units —
    // distinguishes "unknown" from a genuine 0% so the footer never reads as a
    // dead battery during cold start.
    val batteryPercent: Int?,
    val charging: Boolean,
    // True while a recent GPS_PROVIDER fix is fresh; flips back to false once the
    // last fix ages past the freshness window so the footer reads "searching"
    // when GPS reception drops (e.g. a tunnel or a parked cold start).
    val gpsFixed: Boolean,
    // Satellites currently used in the GPS fix (0 while searching / no GNSS read).
    // Shown under the footer's GPS icon as a coarse reception quality readout.
    val gpsSatelliteCount: Int,
) {
    companion object {
        val Initial: SystemStatus =
            SystemStatus(
                cellularConnected = null,
                cellularSignalLevel = null,
                wifiConnected = false,
                wifiSignalLevel = 0,
                bluetoothEnabled = false,
                bluetoothConnected = false,
                batteryPercent = null,
                charging = false,
                gpsFixed = false,
                gpsSatelliteCount = 0,
            )
    }
}
