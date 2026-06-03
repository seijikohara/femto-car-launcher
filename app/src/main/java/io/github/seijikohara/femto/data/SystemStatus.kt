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
    val wifiConnected: Boolean,
    val bluetoothConnected: Boolean,
    // Null until the first battery reading arrives, or on battery-less units —
    // distinguishes "unknown" from a genuine 0% so the footer never reads as a
    // dead battery during cold start.
    val batteryPercent: Int?,
    val charging: Boolean,
) {
    companion object {
        val Initial: SystemStatus =
            SystemStatus(
                wifiConnected = false,
                bluetoothConnected = false,
                batteryPercent = null,
                charging = false,
            )
    }
}
