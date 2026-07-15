package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
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
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.glanceCaption

@Composable
internal fun StatusCluster(
    status: SystemStatus,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    // The visible indicators in render order; defaults to every DockStatusId in
    // its declared order (today's factory cluster) so an omitted argument
    // renders byte-identical to before this parameter existed.
    order: List<DockStatusId> = DockStatusId.entries,
    motionTier: MotionTier = MotionTier.STANDARD,
    // Dispatches the long-press edit menu's Move/Hide/Reset actions; the
    // default no-op keeps every caller that has nothing to wire (there is
    // none left in production, but a stray preview or test) compiling.
    onAction: (HomeAction) -> Unit = {},
) {
    // One content slot rendered into either axis container, so the indicator set
    // and order stay identical between the horizontal bar and the vertical rail.
    val indicators: @Composable () -> Unit = {
        order.forEachIndexed { index, id ->
            key(id) {
                EditableStatusIndicator(
                    id = id,
                    status = status,
                    vertical = vertical,
                    canMoveLeft = index > 0,
                    canMoveRight = index < order.lastIndex,
                    canHide = order.size > 1,
                    tier = motionTier,
                    onAction = onAction,
                )
            }
        }
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

/**
 * Wraps one status indicator's rendered content with the long-press dock-edit
 * menu ([DockEditMenu], shared with the dock's nav buttons — Move left/right,
 * or up/down when [vertical] / Hide / Reset dock). Status icons are read-only
 * at rest — a tap does nothing — so the long press is wired via a raw
 * [detectTapGestures] rather than `combinedClickable`: the latter would add a
 * "double-tap to activate" semantics action for a control with no click
 * behavior, a dead affordance for a screen reader. A `Modifier.semantics {
 * onLongClick }` alongside that gesture detector bridges the same action to
 * accessibility services, which drive the semantics tree rather than raw
 * pointer input — without it, TalkBack had no way to reach this menu at all.
 * `mergeDescendants = true` folds the indicator's contentDescription into the
 * same node as the action; unmerged, TalkBack lands on an unlabeled actionable
 * stop while the label sits on a separate child node (`combinedClickable`
 * merges implicitly, so the nav buttons never had this split). CELLULAR
 * renders nothing when hidden on a telephony-less unit (see
 * [StatusIndicator]), leaving a zero-size Box with nothing to long-press —
 * consistent with today's absence, not a new empty tap target.
 */
@Composable
private fun EditableStatusIndicator(
    id: DockStatusId,
    status: SystemStatus,
    // True on the vertical rail (DockPosition.LEFT / RIGHT) — see DockEditMenu.
    vertical: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canHide: Boolean,
    tier: MotionTier,
    onAction: (HomeAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val openMenuLabel = stringResource(R.string.dock_edit)
    Box(
        modifier =
            Modifier
                .semantics(mergeDescendants = true) {
                    onLongClick(label = openMenuLabel) {
                        menuOpen = true
                        true
                    }
                }.pointerInput(id) {
                    detectTapGestures(onLongPress = { menuOpen = true })
                },
    ) {
        StatusIndicator(id, status, tier)
        DockEditMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            canMoveLeft = canMoveLeft,
            canMoveRight = canMoveRight,
            canHide = canHide,
            onMoveLeft = { onAction(HomeAction.MoveDockStatus(id, -1)) },
            onMoveRight = { onAction(HomeAction.MoveDockStatus(id, 1)) },
            onHide = { onAction(HomeAction.HideDockStatus(id)) },
            onResetDock = { onAction(HomeAction.ResetDock) },
            vertical = vertical,
        )
    }
}

// One indicator's content for [id], keyed by DockStatusId so the caller
// ([StatusCluster]'s order parameter) can reorder or hide indicators
// independently of this dispatch. CELLULAR renders nothing when the signal is
// null — hidden entirely on telephony-less units, rather than shown
// permanently disconnected — mirroring the cluster's original inline check.
@Composable
private fun StatusIndicator(
    id: DockStatusId,
    status: SystemStatus,
    tier: MotionTier,
) {
    when (id) {
        DockStatusId.CELLULAR -> {
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
                    tier = tier,
                )
            }
        }

        DockStatusId.WIFI -> {
            StatusIcon(
                // The Wi-Fi level is always known once connected; degrade to the binary
                // icon only when disconnected so a dimmed flat icon reads as "off".
                icon = if (status.wifiConnected) wifiIconForLevel(status.wifiSignalLevel) else Lucide.Wifi,
                active = status.wifiConnected,
                description =
                    stringResource(
                        if (status.wifiConnected) R.string.status_wifi_connected else R.string.status_wifi_disconnected,
                    ),
                tier = tier,
            )
        }

        DockStatusId.BLUETOOTH -> {
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
                tier = tier,
            )
        }

        DockStatusId.GPS -> {
            GpsIndicator(fixed = status.gpsFixed, satelliteCount = status.gpsSatelliteCount, tier = tier)
        }

        DockStatusId.BATTERY -> {
            BatteryIndicator(percent = status.batteryPercent, charging = status.charging, tier = tier)
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
    tier: MotionTier,
    modifier: Modifier = Modifier,
) = Motion.ContentCrossfade(
    // Key on the (glyph, lit-state) pair so a signal-ramp glyph swap or a
    // connect/disconnect tint flip dissolves; the fixed 20 dp icon slot keeps the
    // cluster from reflowing. Both values ride the target so the outgoing layer
    // fades the old glyph out rather than snapping straight to the new one.
    targetState = Pair(icon, active),
    tier = tier,
    label = "statusIcon",
    modifier = modifier,
) { (glyph, lit) ->
    FemtoIcon(
        imageVector = glyph,
        contentDescription = description,
        tint = if (lit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(20.dp),
    )
}

// Satellite icon stacked over the count of satellites used in the current fix,
// mirroring BatteryIndicator. The icon and count dim together while searching
// (no fresh GPS fix) so a parked / tunnelled cold start reads as "0 locked".
@Composable
private fun GpsIndicator(
    fixed: Boolean,
    satelliteCount: Int,
    tier: MotionTier,
    modifier: Modifier = Modifier,
) = Motion.ContentCrossfade(
    // The whole indicator (satellite glyph + count) dissolves together on a
    // fix/lost flip or a satellite-count change, keyed on that discrete pair.
    targetState = Pair(fixed, satelliteCount),
    tier = tier,
    label = "gps",
    modifier = modifier,
) { (isFixed, count) ->
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        val tint = if (isFixed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        FemtoIcon(
            imageVector = Lucide.Satellite,
            contentDescription =
                stringResource(
                    if (isFixed) R.string.status_gps_fixed else R.string.status_gps_searching,
                ),
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.status_gps_satellites, count),
            style = MaterialTheme.typography.glanceCaption(),
            color = tint,
            maxLines = 1,
        )
    }
}

// Battery icon stacked over its percent. Charging is conveyed by the bolt glyph
// and the accent tint alone — no caption.
@Composable
private fun BatteryIndicator(
    percent: Int?,
    charging: Boolean,
    tier: MotionTier,
    modifier: Modifier = Modifier,
) = Motion.ContentCrossfade(
    // The whole indicator (battery glyph + percent) dissolves together on a
    // level-bucket, charging, or percent change, keyed on that discrete pair.
    targetState = Pair(percent, charging),
    tier = tier,
    label = "battery",
    modifier = modifier,
) { (pct, isCharging) ->
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        FemtoIcon(
            imageVector = batteryIconForLevel(percent = pct, charging = isCharging),
            contentDescription = stringResource(R.string.status_battery),
            tint = if (isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            // Render an em-dash while the percent is unknown (cold start / battery-less
            // unit) so the cluster never reads as a dead 0% battery.
            text = if (pct == null) "—" else stringResource(R.string.battery_percent, pct),
            style = MaterialTheme.typography.glanceCaption(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
