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
