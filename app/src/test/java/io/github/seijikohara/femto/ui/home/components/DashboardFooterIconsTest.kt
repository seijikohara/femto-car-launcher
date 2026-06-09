package io.github.seijikohara.femto.ui.home.components

import com.composables.icons.lucide.Battery
import com.composables.icons.lucide.BatteryCharging
import com.composables.icons.lucide.BatteryFull
import com.composables.icons.lucide.BatteryLow
import com.composables.icons.lucide.BatteryMedium
import com.composables.icons.lucide.Bluetooth
import com.composables.icons.lucide.BluetoothConnected
import com.composables.icons.lucide.BluetoothOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Signal
import com.composables.icons.lucide.SignalHigh
import com.composables.icons.lucide.SignalLow
import com.composables.icons.lucide.SignalMedium
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.WifiHigh
import com.composables.icons.lucide.WifiLow
import com.composables.icons.lucide.WifiZero
import org.junit.Test
import kotlin.test.assertEquals

// Pure icon-mapping tests for the footer status cluster. Each indicator that
// exposes an intensity (cellular / Wi-Fi level, battery percent) or a multi-state
// axis (bluetooth) maps to a distinct Lucide glyph; these lock the ramps so a
// future glyph rename or off-by-one threshold is caught.
class DashboardFooterIconsTest {
    @Test
    fun `cellular steps across the signal levels`() {
        assertEquals(Lucide.SignalLow, cellularIconForLevel(0))
        assertEquals(Lucide.SignalLow, cellularIconForLevel(1))
        assertEquals(Lucide.SignalMedium, cellularIconForLevel(2))
        assertEquals(Lucide.SignalHigh, cellularIconForLevel(3))
        assertEquals(Lucide.Signal, cellularIconForLevel(4))
    }

    @Test
    fun `wifi steps across the signal levels`() {
        assertEquals(Lucide.WifiZero, wifiIconForLevel(0))
        assertEquals(Lucide.WifiLow, wifiIconForLevel(1))
        assertEquals(Lucide.WifiHigh, wifiIconForLevel(2))
        assertEquals(Lucide.WifiHigh, wifiIconForLevel(3))
        assertEquals(Lucide.Wifi, wifiIconForLevel(4))
    }

    @Test
    fun `battery charging shows the charging glyph regardless of percent`() {
        assertEquals(Lucide.BatteryCharging, batteryIconForLevel(percent = 5, charging = true))
    }

    @Test
    fun `battery steps from low to full with the percent`() {
        assertEquals(Lucide.BatteryLow, batteryIconForLevel(percent = 0, charging = false))
        assertEquals(Lucide.BatteryLow, batteryIconForLevel(percent = 20, charging = false))
        assertEquals(Lucide.BatteryMedium, batteryIconForLevel(percent = 21, charging = false))
        assertEquals(Lucide.BatteryMedium, batteryIconForLevel(percent = 60, charging = false))
        assertEquals(Lucide.BatteryFull, batteryIconForLevel(percent = 61, charging = false))
    }

    @Test
    fun `battery falls back to the neutral glyph when the percent is unknown`() {
        assertEquals(Lucide.Battery, batteryIconForLevel(percent = null, charging = false))
    }

    @Test
    fun `bluetooth reflects off, on, and connected`() {
        assertEquals(Lucide.BluetoothOff, bluetoothIconFor(enabled = false, connected = false))
        assertEquals(Lucide.Bluetooth, bluetoothIconFor(enabled = true, connected = false))
        assertEquals(Lucide.BluetoothConnected, bluetoothIconFor(enabled = true, connected = true))
    }
}
