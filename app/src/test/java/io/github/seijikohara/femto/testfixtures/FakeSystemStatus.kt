package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.SystemStatus

internal fun fakeSystemStatus(
    cellularConnected: Boolean? = null,
    cellularSignalLevel: Int? = null,
    wifiConnected: Boolean = true,
    wifiSignalLevel: Int = 4,
    bluetoothConnected: Boolean = true,
    batteryPercent: Int? = 78,
    charging: Boolean = false,
): SystemStatus =
    SystemStatus(
        cellularConnected = cellularConnected,
        cellularSignalLevel = cellularSignalLevel,
        wifiConnected = wifiConnected,
        wifiSignalLevel = wifiSignalLevel,
        bluetoothConnected = bluetoothConnected,
        batteryPercent = batteryPercent,
        charging = charging,
    )
