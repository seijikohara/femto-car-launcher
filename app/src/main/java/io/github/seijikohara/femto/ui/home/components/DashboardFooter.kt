package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import com.composables.icons.lucide.Bluetooth
import com.composables.icons.lucide.BluetoothConnected
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
import io.github.seijikohara.femto.data.SystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures

// Below this footer width the read-only status cluster is dropped so the seven
// nav buttons keep room to render at >= FemtoDimens.MinTouchTarget without
// clipping on portrait / narrow head units. Derived from the layout, not tuned
// to a device: 7 buttons * 64 dp floor (448 dp) + the status cluster (~140 dp)
// + dividers and horizontal padding (~70 dp) ~= 660 dp, rounded up for headroom.
// Adding an eighth button raises the button term by one MinTouchTarget.
private val CompactFooterWidth: Dp = 700.dp

/**
 * Bottom dock. Originally derived from `docs/design/dashboard-v2-mockup.html`
 * `.footer`, since evolved from on-device feedback (shorter height, no Home
 * button, added cellular indicator and charging caption); this composable, not
 * the mockup, is now the authoritative footer spec.
 *
 *  - [FemtoDimens.FooterHeight] surface with a 1 dp top divider (`outlineVariant`).
 *  - Seven equal-weight nav buttons (Phone / Apps / Music / Navigation /
 *    Browser / Assistant / Settings). This app IS the launcher, so no Home button.
 *  - A 1 dp vertical divider separates the actionable nav from a read-only
 *    status cluster: cellular (hidden on telephony-less units), Wi-Fi,
 *    Bluetooth, GPS reception, and a battery indicator (icon over percent, with
 *    a "Charging" caption while plugged in).
 *
 * Iconography is Lucide stroke-1.75 for parity with the design SSOT.
 */
@Composable
internal fun DashboardFooter(
    systemStatus: SystemStatus,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.height(FemtoDimens.FooterHeight),
    color = MaterialTheme.colorScheme.surface,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
        ) {
            // The seven nav buttons plus the cluster cannot both fit on a narrow
            // portrait head unit; below the threshold the read-only status
            // cluster yields so the actionable nav stays uncut.
            val showStatusCluster = maxWidth >= CompactFooterWidth
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavRow(
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                )
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
                        modifier = Modifier.padding(start = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavRow(
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    // Each button takes an equal weight so the seven buttons share the width and
    // shrink toward FemtoDimens.MinTouchTarget instead of clipping when the row
    // is narrow. The widthIn floor in NavButton keeps every tap target legal.
    NavButton(
        icon = Lucide.Phone,
        description = stringResource(R.string.nav_phone),
        onClick = { onAction(HomeAction.Shortcut(AppsBarShortcut.Phone)) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.LayoutGrid,
        description = stringResource(R.string.nav_apps),
        onClick = { onAction(HomeAction.OpenAppDrawer) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Music,
        description = stringResource(R.string.nav_music),
        onClick = { onAction(HomeAction.Shortcut(AppsBarShortcut.Music)) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Navigation,
        description = stringResource(R.string.nav_navigation),
        onClick = { onAction(HomeAction.OpenMaps) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Globe,
        description = stringResource(R.string.nav_browser),
        onClick = { onAction(HomeAction.OpenBrowser) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Mic,
        description = stringResource(R.string.nav_assistant),
        onClick = { onAction(HomeAction.OpenAssistant) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Settings,
        description = stringResource(R.string.nav_settings),
        onClick = { onAction(HomeAction.OpenSettings) },
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun NavButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier =
        modifier
            .height(FemtoDimens.MinTouchTarget)
            .widthIn(min = FemtoDimens.MinTouchTarget)
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
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxHeight(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(18.dp),
) {
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
        // Light the icon whenever the adapter is ON (an enabled adapter should not
        // read as off); switch to the connected glyph only when a device is linked.
        icon = if (status.bluetoothConnected) Lucide.BluetoothConnected else Lucide.Bluetooth,
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

// Graduated Wi-Fi glyph for a 0..4 level. Lucide ships four distinct Wi-Fi
// glyphs (zero / low / high / full arc); level 3 reuses the high arc and 4 the
// full Wifi arc so the ramp stays monotonic with the available icons.
private fun wifiIconForLevel(level: Int): ImageVector =
    when (level) {
        0 -> Lucide.WifiZero
        1 -> Lucide.WifiLow
        2, 3 -> Lucide.WifiHigh
        else -> Lucide.Wifi
    }

// Graduated cellular glyph for a 0..4 level. Lucide ships ascending Signal bars
// (low / medium / high) plus the full Signal glyph; level 0/1 map to the lowest
// bars and 4 to the full glyph so the ramp tracks SignalStrength.level.
private fun cellularIconForLevel(level: Int): ImageVector =
    when (level) {
        0, 1 -> Lucide.SignalLow
        2 -> Lucide.SignalMedium
        3 -> Lucide.SignalHigh
        else -> Lucide.Signal
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

// Satellite icon stacked over the count of satellites used in the current fix,
// mirroring BatteryIndicator. The icon and count dim together while searching
// (no fresh GPS fix) so a parked / tunnelled cold start reads as "0 locked".
// The 13sp count uses the same footer glance-metadata allowance as the battery
// percent (CLAUDE.md#automotive-overrides).
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
        style =
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TabularFigures,
            ),
        color = tint,
        maxLines = 1,
    )
}

// Battery icon stacked over its percent, with a "Charging" caption beneath while
// plugged in. The 13sp percent and 10sp caption sit below the dashboard's 18sp
// body floor under the same footer glance-metadata allowance the cluster already
// uses (CLAUDE.md#automotive-overrides).
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
        imageVector = Lucide.Battery,
        contentDescription = stringResource(R.string.status_battery),
        tint = if (charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
    Text(
        // Render an em-dash while the percent is unknown (cold start / battery-less
        // unit) so the cluster never reads as a dead 0% battery.
        text = if (percent == null) "—" else stringResource(R.string.battery_percent, percent),
        style =
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TabularFigures,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
    if (charging) {
        Text(
            text = stringResource(R.string.status_charging),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@PreviewLightDark
@Preview(name = "Dashboard footer", widthDp = 1280, heightDp = 64)
@Composable
private fun DashboardFooterPreview() {
    FemtoTheme {
        DashboardFooter(
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
@Preview(name = "Dashboard footer (narrow)", widthDp = 520, heightDp = 64)
@Composable
private fun DashboardFooterNarrowPreview() {
    FemtoTheme {
        DashboardFooter(
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
