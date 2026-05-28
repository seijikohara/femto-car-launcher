package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.SystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * Bottom dock for the dashboard.
 *
 * Seven equally-spaced navigation buttons (Home / Phone / Apps / Music /
 * Navigation / Browser / Settings) on the left, then a vertical divider,
 * then a read-only status cluster (Wi-Fi / Bluetooth / Battery %) on the
 * right. The divider is the only visual cue separating "actionable" from
 * "informational" — kept deliberately quiet to avoid breaking the bar's
 * horizontal flow.
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
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavRow(
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
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

@Composable
private fun NavRow(
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    NavButton(
        icon = Icons.Outlined.Home,
        description = "Home",
        active = true,
        onClick = { /* already on home */ },
    )
    NavButton(
        icon = Icons.Outlined.Phone,
        description = "Phone",
        active = false,
        onClick = { onAction(HomeAction.Shortcut(AppsBarShortcut.Phone)) },
    )
    NavButton(
        icon = Icons.Outlined.Apps,
        description = "Apps",
        active = false,
        onClick = { onAction(HomeAction.OpenAppDrawer) },
    )
    NavButton(
        icon = Icons.Outlined.MusicNote,
        description = "Music",
        active = false,
        onClick = { onAction(HomeAction.Shortcut(AppsBarShortcut.Music)) },
    )
    NavButton(
        icon = Icons.Outlined.Navigation,
        description = "Navigation",
        active = false,
        onClick = { onAction(HomeAction.OpenMaps) },
    )
    NavButton(
        icon = Icons.Outlined.Public,
        description = "Browser",
        active = false,
        onClick = { onAction(HomeAction.OpenBrowser) },
    )
    NavButton(
        icon = Icons.Outlined.Settings,
        description = "Settings",
        active = false,
        onClick = { onAction(HomeAction.OpenSettings) },
    )
}

@Composable
private fun NavButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val background =
        if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val tint =
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .height(FemtoDimens.MinTouchTarget)
                .width(72.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .clickable(onClick = onClick)
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
        icon = Icons.Outlined.Wifi,
        active = status.wifiConnected,
        description = if (status.wifiConnected) "Wi-Fi connected" else "Wi-Fi disconnected",
    )
    StatusIcon(
        icon = Icons.Outlined.Bluetooth,
        active = status.bluetoothConnected,
        description = if (status.bluetoothConnected) "Bluetooth connected" else "Bluetooth disconnected",
    )
    BatteryIndicator(percent = status.batteryPercent, charging = status.charging)
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    active: Boolean,
    description: String,
) {
    val tint =
        if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun BatteryIndicator(
    percent: Int,
    charging: Boolean,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    Icon(
        imageVector = Icons.Outlined.BatteryStd,
        contentDescription = "Battery",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
    Text(
        text = if (charging) "$percent% ⚡" else "$percent%",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
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
