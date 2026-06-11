package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.system.SystemStatus

internal fun fakeSystemStatus(
    cellularConnected: Boolean? = null,
    cellularSignalLevel: Int? = null,
    wifiConnected: Boolean = true,
    wifiSignalLevel: Int = 4,
    bluetoothEnabled: Boolean = true,
    bluetoothConnected: Boolean = true,
    batteryPercent: Int? = 78,
    charging: Boolean = false,
    gpsFixed: Boolean = false,
    gpsSatelliteCount: Int = 0,
): SystemStatus =
    SystemStatus(
        cellularConnected = cellularConnected,
        cellularSignalLevel = cellularSignalLevel,
        wifiConnected = wifiConnected,
        wifiSignalLevel = wifiSignalLevel,
        bluetoothEnabled = bluetoothEnabled,
        bluetoothConnected = bluetoothConnected,
        batteryPercent = batteryPercent,
        charging = charging,
        gpsFixed = gpsFixed,
        gpsSatelliteCount = gpsSatelliteCount,
    )
