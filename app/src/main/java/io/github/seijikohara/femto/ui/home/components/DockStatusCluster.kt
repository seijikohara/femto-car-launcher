package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Battery
import com.composables.icons.lucide.BatteryCharging
import com.composables.icons.lucide.BatteryFull
import com.composables.icons.lucide.BatteryLow
import com.composables.icons.lucide.BatteryMedium
import com.composables.icons.lucide.Bluetooth
import com.composables.icons.lucide.BluetoothConnected
import com.composables.icons.lucide.BluetoothOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Satellite
import com.composables.icons.lucide.Signal
import com.composables.icons.lucide.SignalHigh
import com.composables.icons.lucide.SignalLow
import com.composables.icons.lucide.SignalMedium
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.WifiHigh
import com.composables.icons.lucide.WifiLow
import com.composables.icons.lucide.WifiZero
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.TabularFigures

@Composable
internal fun StatusCluster(
    status: SystemStatus,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    // One content slot rendered into either axis container, so the indicator set
    // and order stay identical between the horizontal bar and the vertical rail.
    val indicators: @Composable () -> Unit = {
        // Cellular is hidden entirely on telephony-less units (null), rather than
        // shown permanently disconnected.
        status.cellularConnected?.let { connected ->
            val level = status.cellularSignalLevel
            // Drive the lit state off signal presence when the level is known: on a
            // Wi-Fi-primary head unit the cellular network repeatedly drops in and out
            // of NET_CAPABILITY_VALIDATED (Wi-Fi owns the default route), which flickered
            // the icon. A known SignalStrength.level tracks the radio directly and is
            // stable; fall back to the validated-connectivity flag only when the level is
            // unknown (READ_PHONE_STATE withheld).
            val active = level?.let { it > 0 } ?: connected
            StatusIcon(
                // A known level picks the graduated bars; a null level (READ_PHONE_STATE
                // withheld / no reading yet) degrades to the binary connected icon.
                icon = level?.let { cellularIconForLevel(it) } ?: Lucide.Signal,
                active = active,
                description =
                    stringResource(
                        if (active) R.string.status_cellular_connected else R.string.status_cellular_disconnected,
                    ),
            )
        }
        StatusIcon(
            // The Wi-Fi level is always known once connected; degrade to the binary
            // icon only when disconnected so a dimmed flat icon reads as "off".
            icon = if (status.wifiConnected) wifiIconForLevel(status.wifiSignalLevel) else Lucide.Wifi,
            active = status.wifiConnected,
            description =
                stringResource(
                    if (status.wifiConnected) R.string.status_wifi_connected else R.string.status_wifi_disconnected,
                ),
        )
        StatusIcon(
            // Off / on / connected each get a distinct glyph: a crossed icon when the
            // adapter is powered off, the plain glyph when on but unpaired, the linked
            // glyph when a device is connected.
            icon = bluetoothIconFor(enabled = status.bluetoothEnabled, connected = status.bluetoothConnected),
            active = status.bluetoothEnabled,
            description =
                stringResource(
                    when {
                        status.bluetoothConnected -> R.string.status_bluetooth_connected
                        status.bluetoothEnabled -> R.string.status_bluetooth_on
                        else -> R.string.status_bluetooth_off
                    },
                ),
        )
        GpsIndicator(fixed = status.gpsFixed, satelliteCount = status.gpsSatelliteCount)
        BatteryIndicator(percent = status.batteryPercent, charging = status.charging)
    }
    if (vertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            indicators()
        }
    } else {
        Row(
            modifier = modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            indicators()
        }
    }
}

// Graduated Wi-Fi glyph for a 0..4 level. Lucide ships four distinct Wi-Fi
// glyphs (zero / low / high / full arc); level 3 reuses the high arc and 4 the
// full Wifi arc so the ramp stays monotonic with the available icons.
internal fun wifiIconForLevel(level: Int): ImageVector =
    when (level) {
        0 -> Lucide.WifiZero
        1 -> Lucide.WifiLow
        2, 3 -> Lucide.WifiHigh
        else -> Lucide.Wifi
    }

// Graduated cellular glyph for a 0..4 level. Lucide ships ascending Signal bars
// (low / medium / high) plus the full Signal glyph; level 0/1 map to the lowest
// bars and 4 to the full glyph so the ramp tracks SignalStrength.level.
internal fun cellularIconForLevel(level: Int): ImageVector =
    when (level) {
        0, 1 -> Lucide.SignalLow
        2 -> Lucide.SignalMedium
        3 -> Lucide.SignalHigh
        else -> Lucide.Signal
    }

// Graduated battery glyph. Charging shows the bolt; otherwise the fill steps with
// the percent across rough thirds — 20 is the conventional low-battery warning
// point, 60 the medium/full divide. A null percent (cold start / battery-less
// unit) falls back to the neutral battery outline.
internal fun batteryIconForLevel(
    percent: Int?,
    charging: Boolean,
): ImageVector =
    when {
        charging -> Lucide.BatteryCharging
        percent == null -> Lucide.Battery
        percent <= 20 -> Lucide.BatteryLow
        percent <= 60 -> Lucide.BatteryMedium
        else -> Lucide.BatteryFull
    }

// Bluetooth glyph across the three states the adapter exposes: crossed when
// powered off, the plain glyph when on but unpaired, and the linked glyph when a
// device is connected. A connected device implies the adapter is on, so connected
// is checked first. Android exposes no Bluetooth RSSI, so off / on / connected is
// the meaningful axis rather than a signal ramp.
internal fun bluetoothIconFor(
    enabled: Boolean,
    connected: Boolean,
): ImageVector =
    when {
        connected -> Lucide.BluetoothConnected
        enabled -> Lucide.Bluetooth
        else -> Lucide.BluetoothOff
    }

@Composable
private fun StatusIcon(
    icon: ImageVector,
    active: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val tint =
        if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    FemtoIcon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(20.dp),
    )
}

// The 13sp metric readout under the GPS / battery icons. It sits below the
// dashboard's 18sp body floor under the dock glance-metadata allowance
// (CLAUDE.md#automotive-overrides); tabular figures keep the digits steady.
@Composable
private fun statusMetricStyle() =
    MaterialTheme.typography.labelLarge.copy(
        fontSize = FemtoDimens.GlanceTextSize,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TabularFigures,
    )

// Satellite icon stacked over the count of satellites used in the current fix,
// mirroring BatteryIndicator. The icon and count dim together while searching
// (no fresh GPS fix) so a parked / tunnelled cold start reads as "0 locked".
@Composable
private fun GpsIndicator(
    fixed: Boolean,
    satelliteCount: Int,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(1.dp),
) {
    val tint = if (fixed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    FemtoIcon(
        imageVector = Lucide.Satellite,
        contentDescription =
            stringResource(
                if (fixed) R.string.status_gps_fixed else R.string.status_gps_searching,
            ),
        tint = tint,
        modifier = Modifier.size(20.dp),
    )
    Text(
        text = stringResource(R.string.status_gps_satellites, satelliteCount),
        style = statusMetricStyle(),
        color = tint,
        maxLines = 1,
    )
}

// Battery icon stacked over its percent. Charging is conveyed by the bolt glyph
// and the accent tint alone — no caption.
@Composable
private fun BatteryIndicator(
    percent: Int?,
    charging: Boolean,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(1.dp),
) {
    FemtoIcon(
        imageVector = batteryIconForLevel(percent = percent, charging = charging),
        contentDescription = stringResource(R.string.status_battery),
        tint = if (charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
    Text(
        // Render an em-dash while the percent is unknown (cold start / battery-less
        // unit) so the cluster never reads as a dead 0% battery.
        text = if (percent == null) "—" else stringResource(R.string.battery_percent, percent),
        style = statusMetricStyle(),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}
