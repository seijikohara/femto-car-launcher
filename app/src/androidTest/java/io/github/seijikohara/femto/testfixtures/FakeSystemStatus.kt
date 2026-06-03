package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.SystemStatus

internal fun fakeSystemStatus(
    wifiConnected: Boolean = true,
    bluetoothConnected: Boolean = true,
    batteryPercent: Int = 78,
    charging: Boolean = false,
): SystemStatus =
    SystemStatus(
        wifiConnected = wifiConnected,
        bluetoothConnected = bluetoothConnected,
        batteryPercent = batteryPercent,
        charging = charging,
    )
