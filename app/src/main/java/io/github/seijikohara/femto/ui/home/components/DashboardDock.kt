package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Battery
import com.composables.icons.lucide.BatteryCharging
import com.composables.icons.lucide.BatteryFull
import com.composables.icons.lucide.BatteryLow
import com.composables.icons.lucide.BatteryMedium
import com.composables.icons.lucide.Bluetooth
import com.composables.icons.lucide.BluetoothConnected
import com.composables.icons.lucide.BluetoothOff
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Satellite
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Signal
import com.composables.icons.lucide.SignalHigh
import com.composables.icons.lucide.SignalLow
import com.composables.icons.lucide.SignalMedium
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.WifiHigh
import com.composables.icons.lucide.WifiLow
import com.composables.icons.lucide.WifiZero
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures

// Below this dock extent (width for the horizontal bar, height for the
// vertical rail) the read-only status cluster is dropped so the seven nav
// buttons keep room to render at >= FemtoDimens.MinTouchTarget without
// clipping on portrait / narrow head units. Derived from the layout, not tuned
// to a device: 7 buttons * 64 dp floor (448 dp) + the status cluster (~140 dp)
// + dividers and padding (~70 dp) ~= 660 dp, rounded up for headroom.
// Adding an eighth button raises the button term by one MinTouchTarget.
private val CompactDockExtent: Dp = 700.dp

// The seven dock destinations in display order. This app IS the launcher, so no
// Home button. Shared by the horizontal bar and the vertical rail so the set
// and order stay identical.
private data class NavSpec(
    val icon: ImageVector,
    val labelRes: Int,
    val action: HomeAction,
)

private val NavSpecs =
    listOf(
        NavSpec(Lucide.Phone, R.string.nav_phone, HomeAction.Shortcut(AppsBarShortcut.Phone)),
        NavSpec(Lucide.LayoutGrid, R.string.nav_apps, HomeAction.OpenAppDrawer),
        NavSpec(Lucide.Music, R.string.nav_music, HomeAction.Shortcut(AppsBarShortcut.Music)),
        NavSpec(Lucide.Navigation, R.string.nav_navigation, HomeAction.OpenMaps),
        NavSpec(Lucide.Globe, R.string.nav_browser, HomeAction.OpenBrowser),
        NavSpec(Lucide.Mic, R.string.nav_assistant, HomeAction.OpenAssistant),
        NavSpec(Lucide.Settings, R.string.nav_settings, HomeAction.OpenSettings),
    )

/**
 * Dashboard dock. Originally derived from the `.footer` of the retired
 * dashboard-v2 design mockup, since evolved from on-device feedback (shorter
 * height, no Home button, added cellular indicator); this composable is the
 * authoritative dock spec.
 *
 * [position] picks the hosting edge: [DockPosition.BOTTOM] / [DockPosition.TOP]
 * render the classic horizontal bar (the 1 dp `outlineVariant` divider sits on
 * the edge facing the dashboard), [DockPosition.LEFT] / [DockPosition.RIGHT]
 * render the same content as a vertical rail of [FemtoDimens.DockThickness] width.
 *
 *  - Seven equal-weight nav buttons (Phone / Apps / Music / Navigation /
 *    Browser / Assistant / Settings).
 *  - A 1 dp divider separates the actionable nav from a read-only status
 *    cluster: cellular (hidden on telephony-less units), Wi-Fi, Bluetooth, GPS
 *    reception, and a battery indicator (icon over percent; charging reads from
 *    the bolt glyph and accent tint).
 *
 * Iconography is Lucide stroke-1.75 for parity with the design SSOT.
 */
@Composable
internal fun DashboardDock(
    systemStatus: SystemStatus,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    position: DockPosition = DockPosition.BOTTOM,
) = when (position) {
    DockPosition.BOTTOM, DockPosition.TOP -> {
        HorizontalDock(
            systemStatus = systemStatus,
            onAction = onAction,
            dividerAtBottom = position == DockPosition.TOP,
            modifier = modifier,
        )
    }

    DockPosition.LEFT, DockPosition.RIGHT -> {
        VerticalDock(
            systemStatus = systemStatus,
            onAction = onAction,
            dividerAtStart = position == DockPosition.RIGHT,
            modifier = modifier,
        )
    }
}

@Composable
private fun HorizontalDock(
    systemStatus: SystemStatus,
    onAction: (HomeAction) -> Unit,
    dividerAtBottom: Boolean,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.height(FemtoDimens.DockThickness),
    color = MaterialTheme.colorScheme.surface,
) {
    Column {
        if (!dividerAtBottom) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
        ) {
            // The seven nav buttons plus the cluster cannot both fit on a narrow
            // portrait head unit; below the threshold the read-only status
            // cluster yields so the actionable nav stays uncut.
            val showStatusCluster = maxWidth >= CompactDockExtent
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Each button takes an equal weight so the seven buttons share the
                    // width and shrink toward FemtoDimens.MinTouchTarget instead of
                    // clipping when the row is narrow.
                    NavSpecs.forEach { spec ->
                        NavButton(
                            icon = spec.icon,
                            description = stringResource(spec.labelRes),
                            onClick = { onAction(spec.action) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (showStatusCluster) {
                    VerticalDivider(
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .height(48.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    StatusCluster(
                        status = systemStatus,
                        vertical = false,
                        modifier = Modifier.padding(start = 20.dp),
                    )
                }
            }
        }
        if (dividerAtBottom) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// The same dock content turned 90 degrees: nav buttons share the height, the
// status cluster stacks beneath them, and the divider faces the dashboard.
@Composable
private fun VerticalDock(
    systemStatus: SystemStatus,
    onAction: (HomeAction) -> Unit,
    dividerAtStart: Boolean,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.width(FemtoDimens.DockThickness),
    color = MaterialTheme.colorScheme.surface,
) {
    Row {
        if (dividerAtStart) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 24.dp),
        ) {
            val showStatusCluster = maxHeight >= CompactDockExtent
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The same equal-weight sharing as the horizontal bar, on the
                    // height instead of the width.
                    NavSpecs.forEach { spec ->
                        NavButton(
                            icon = spec.icon,
                            description = stringResource(spec.labelRes),
                            onClick = { onAction(spec.action) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (showStatusCluster) {
                    HorizontalDivider(
                        modifier =
                            Modifier
                                .padding(top = 4.dp)
                                .width(48.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    StatusCluster(
                        status = systemStatus,
                        vertical = true,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
        }
        if (!dividerAtStart) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun NavButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    // The minimum floor keeps every tap target legal in both orientations; the
    // hosting bar / rail stretches the free axis through the weight.
    modifier =
        modifier
            .defaultMinSize(minWidth = FemtoDimens.MinTouchTarget, minHeight = FemtoDimens.MinTouchTarget)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(26.dp),
    )
}

@Composable
private fun StatusCluster(
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
    Icon(
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
        fontSize = 13.sp,
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
    Icon(
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
    Icon(
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

@PreviewLightDark
@Preview(name = "Dashboard dock", widthDp = 1280, heightDp = 64)
@Composable
private fun DashboardDockPreview() {
    FemtoTheme {
        DashboardDock(
            systemStatus =
                SystemStatus(
                    cellularConnected = true,
                    cellularSignalLevel = 3,
                    wifiConnected = true,
                    wifiSignalLevel = 4,
                    bluetoothEnabled = true,
                    bluetoothConnected = true,
                    batteryPercent = 78,
                    charging = true,
                    gpsFixed = true,
                    gpsSatelliteCount = 9,
                ),
            onAction = {},
        )
    }
}

// Narrow portrait head unit: the status cluster drops and the nav buttons share
// the width down toward FemtoDimens.MinTouchTarget rather than clipping.
@PreviewLightDark
@Preview(name = "Dashboard dock (narrow)", widthDp = 520, heightDp = 64)
@Composable
private fun DashboardDockNarrowPreview() {
    FemtoTheme {
        DashboardDock(
            systemStatus =
                SystemStatus(
                    cellularConnected = null,
                    cellularSignalLevel = null,
                    wifiConnected = true,
                    wifiSignalLevel = 2,
                    bluetoothEnabled = true,
                    bluetoothConnected = false,
                    batteryPercent = null,
                    charging = false,
                    gpsFixed = false,
                    gpsSatelliteCount = 0,
                ),
            onAction = {},
        )
    }
}

// Vertical rail on a tall edge: the nav buttons share the height and the status
// cluster stacks beneath them.
@PreviewLightDark
@Preview(name = "Dashboard dock (left rail)", widthDp = 64, heightDp = 800)
@Composable
private fun DashboardDockRailPreview() {
    FemtoTheme {
        DashboardDock(
            systemStatus =
                SystemStatus(
                    cellularConnected = true,
                    cellularSignalLevel = 3,
                    wifiConnected = true,
                    wifiSignalLevel = 4,
                    bluetoothEnabled = true,
                    bluetoothConnected = true,
                    batteryPercent = 78,
                    charging = true,
                    gpsFixed = true,
                    gpsSatelliteCount = 9,
                ),
            onAction = {},
            position = DockPosition.LEFT,
        )
    }
}
