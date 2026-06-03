package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
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
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.House
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Wifi
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.SystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures

// Below this footer width the read-only status cluster is dropped so the seven
// nav buttons keep room to render at >= FemtoDimens.MinTouchTarget without
// clipping on portrait / narrow head units.
private val CompactFooterWidth: Dp = 640.dp

/**
 * Bottom dock per `docs/design/dashboard-v2-mockup.html` `.footer`:
 *
 *  - 80 dp surface with a 1 dp top divider (`outlineVariant`).
 *  - Seven 72 × 64 dp nav buttons (Home / Phone / Apps / Music /
 *    Navigation / Browser / Settings) in a 3 + 1 + 3 grouping, evenly
 *    distributed across the left flex region.
 *  - The Home button is the only active state — primaryContainer
 *    background with a 20 × 3 dp underline 4 dp from the bottom.
 *  - A 1 dp vertical divider separates the actionable nav from a
 *    read-only Wi-Fi / Bluetooth / battery cluster at 20 dp icon size,
 *    with the battery percent rendered at 13sp / 700.
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
            // Seven 64 dp buttons plus the cluster need ~707 dp with zero slack;
            // narrow portrait head units cannot fit both. Below the threshold the
            // read-only status cluster yields so the actionable nav stays uncut.
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
    // Each button takes an equal weight so seven buttons share the width and
    // shrink toward FemtoDimens.MinTouchTarget instead of clipping when the row
    // is narrow. The widthIn floor in NavButton keeps every tap target legal.
    NavButton(
        icon = Lucide.House,
        description = stringResource(R.string.nav_home),
        active = true,
        onClick = { /* already on home */ },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Phone,
        description = stringResource(R.string.nav_phone),
        active = false,
        onClick = { onAction(HomeAction.Shortcut(AppsBarShortcut.Phone)) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.LayoutGrid,
        description = stringResource(R.string.nav_apps),
        active = false,
        onClick = { onAction(HomeAction.OpenAppDrawer) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Music,
        description = stringResource(R.string.nav_music),
        active = false,
        onClick = { onAction(HomeAction.Shortcut(AppsBarShortcut.Music)) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Navigation,
        description = stringResource(R.string.nav_navigation),
        active = false,
        onClick = { onAction(HomeAction.OpenMaps) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Globe,
        description = stringResource(R.string.nav_browser),
        active = false,
        onClick = { onAction(HomeAction.OpenBrowser) },
        modifier = Modifier.weight(1f),
    )
    NavButton(
        icon = Lucide.Settings,
        description = stringResource(R.string.nav_settings),
        active = false,
        onClick = { onAction(HomeAction.OpenSettings) },
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun NavButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background =
        if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val tint =
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            modifier
                .height(FemtoDimens.MinTouchTarget)
                .widthIn(min = FemtoDimens.MinTouchTarget)
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                // The active Home button is the current surface — skip clickable so
                // it shows no dead-tap ripple.
                .then(if (active) Modifier else Modifier.clickable(onClick = onClick))
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
        if (active) {
            ActiveIndicator(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ActiveIndicator(modifier: Modifier = Modifier) =
    Box(
        modifier =
            modifier
                .padding(bottom = 4.dp)
                .height(3.dp)
                .width(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
    )

@Composable
private fun StatusCluster(
    status: SystemStatus,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxHeight(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(18.dp),
) {
    StatusIcon(
        icon = Lucide.Wifi,
        active = status.wifiConnected,
        description =
            stringResource(
                if (status.wifiConnected) R.string.status_wifi_connected else R.string.status_wifi_disconnected,
            ),
    )
    StatusIcon(
        icon = Lucide.Bluetooth,
        active = status.bluetoothConnected,
        description =
            stringResource(
                if (status.bluetoothConnected) {
                    R.string.status_bluetooth_connected
                } else {
                    R.string.status_bluetooth_disconnected
                },
            ),
    )
    BatteryIndicator(percent = status.batteryPercent, charging = status.charging)
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

@Composable
private fun BatteryIndicator(
    percent: Int?,
    charging: Boolean,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    Icon(
        imageVector = Lucide.Battery,
        contentDescription = stringResource(R.string.status_battery),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
    Text(
        // Render an em-dash while the percent is unknown (cold start / battery-less
        // unit) so the cluster never reads as a dead 0% battery.
        text =
            if (percent == null) {
                "—"
            } else {
                stringResource(
                    if (charging) R.string.battery_percent_charging else R.string.battery_percent,
                    percent,
                )
            },
        style =
            MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TabularFigures,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

@PreviewLightDark
@Preview(name = "Dashboard footer", widthDp = 1280, heightDp = 80)
@Composable
private fun DashboardFooterPreview() {
    FemtoTheme {
        DashboardFooter(
            systemStatus =
                SystemStatus(
                    wifiConnected = true,
                    bluetoothConnected = true,
                    batteryPercent = 78,
                    charging = false,
                ),
            onAction = {},
        )
    }
}

// Narrow portrait head unit: the status cluster drops and the nav buttons share
// the width down toward FemtoDimens.MinTouchTarget rather than clipping.
@Preview(name = "Dashboard footer (narrow)", widthDp = 520, heightDp = 80)
@Composable
private fun DashboardFooterNarrowPreview() {
    FemtoTheme {
        DashboardFooter(
            systemStatus =
                SystemStatus(
                    wifiConnected = true,
                    bluetoothConnected = false,
                    batteryPercent = null,
                    charging = false,
                ),
            onAction = {},
        )
    }
}
