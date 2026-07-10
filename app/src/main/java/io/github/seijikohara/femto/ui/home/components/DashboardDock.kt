package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bluetooth
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Settings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * The dock's data-driven layout: which nav buttons and status indicators show,
 * and in what order. Sourced from `DockPreferences`
 * [io.github.seijikohara.femto.data.dock.DockPreferences] and threaded down
 * like [io.github.seijikohara.femto.ui.home.components.MapConfig] /
 * [PanelVisibility]. The defaults reproduce today's fixed dock exactly — every
 * id in its enum's declared order, nothing hidden — so a caller that omits
 * this parameter renders byte-identical to before this type existed.
 */
internal data class DockConfig(
    val navOrder: List<DockNavId> = DockNavId.entries,
    val navHidden: Set<DockNavId> = emptySet(),
    val statusOrder: List<DockStatusId> = DockStatusId.entries,
    val statusHidden: Set<DockStatusId> = emptySet(),
) {
    /** [navOrder] with [navHidden] filtered out — the buttons the dock actually renders. */
    val visibleNav: List<DockNavId> get() = navOrder.filterNot { it in navHidden }

    /** [statusOrder] with [statusHidden] filtered out — the indicators the cluster actually renders. */
    val visibleStatus: List<DockStatusId> get() = statusOrder.filterNot { it in statusHidden }
}

// Below this dock extent (width for the horizontal bar, height for the
// vertical rail) the read-only status cluster is dropped so the actionable
// nav — the visible buttons — keeps room to render at >=
// FemtoDimens.MinTouchTarget without clipping on portrait / narrow head
// units. Derived from the layout, not tuned to a device: [TUNED_VISIBLE_NAV_COUNT]
// buttons * 64 dp floor (448 dp) + the status cluster (~140 dp) + dividers and
// padding (~70 dp) ~= 660 dp, rounded up to [CompactDockExtentBase] for
// headroom. Each button hidden below (or restored) below that tuned count
// shifts the threshold by one MinTouchTarget, since fewer buttons need less
// width before the cluster must yield — see [compactDockExtent] and the
// per-orientation `showStatusCluster` computation below.
private val CompactDockExtentBase: Dp = 700.dp

// The visible nav-button count CompactDockExtentBase above was tuned for
// (today's full seven-button dock, nothing hidden).
private const val TUNED_VISIBLE_NAV_COUNT = 7

private fun compactDockExtent(visibleNavCount: Int): Dp =
    CompactDockExtentBase + FemtoDimens.MinTouchTarget * (visibleNavCount - TUNED_VISIBLE_NAV_COUNT)

// One dock nav button's icon / label / action. Shared by the horizontal bar and
// the vertical rail so the set and order stay identical; [navSpecFor] is the
// exhaustive DockNavId -> NavSpec mapping (a `when` so the compiler catches a
// destination the mapping forgets). Internal (not private) so NavSpecForTest
// can assert the mapping directly.
internal data class NavSpec(
    val icon: ImageVector,
    val labelRes: Int,
    val action: HomeAction,
)

internal fun navSpecFor(id: DockNavId): NavSpec =
    when (id) {
        DockNavId.PHONE -> NavSpec(Lucide.Phone, R.string.nav_phone, HomeAction.Shortcut(AppsBarShortcut.Phone))
        DockNavId.APPS -> NavSpec(Lucide.LayoutGrid, R.string.nav_apps, HomeAction.OpenAppDrawer)
        DockNavId.MUSIC -> NavSpec(Lucide.Music, R.string.nav_music, HomeAction.Shortcut(AppsBarShortcut.Music))
        DockNavId.NAVIGATION -> NavSpec(Lucide.Navigation, R.string.nav_navigation, HomeAction.OpenMaps)
        DockNavId.BROWSER -> NavSpec(Lucide.Globe, R.string.nav_browser, HomeAction.OpenBrowser)
        DockNavId.ASSISTANT -> NavSpec(Lucide.Mic, R.string.nav_assistant, HomeAction.OpenAssistant)
        DockNavId.SETTINGS -> NavSpec(Lucide.Settings, R.string.nav_settings, HomeAction.OpenSettings)
    }

/**
 * Dashboard dock. Originally derived from the `.footer` of the retired
 * dashboard-v2 design mockup, since evolved from on-device feedback (shorter
 * height, no Home button, added cellular indicator); this composable is the
 * authoritative dock spec.
 *
 * [position] picks the hosting edge: [DockPosition.BOTTOM] / [DockPosition.TOP]
 * render the horizontal bar, [DockPosition.LEFT] / [DockPosition.RIGHT] render the
 * same content as a vertical rail of [FemtoDimens.DockThickness] width — both as a
 * floating, rounded glass panel over the map (the host insets the dashboard
 * overlays to clear it).
 *
 *  - Equal-weight nav buttons — [DockConfig.visibleNav] (today's factory order:
 *    Phone / Apps / Music / Navigation / Browser / Assistant / Settings, nothing
 *    hidden).
 *  - A 1 dp divider separates the actionable nav from a read-only status
 *    cluster — [DockConfig.visibleStatus] (today's factory order: cellular,
 *    hidden on telephony-less units; Wi-Fi; Bluetooth; GPS reception; and a
 *    battery indicator with icon over percent, charging read from the bolt
 *    glyph and accent tint).
 *
 * Iconography is the Lucide set; its stroke is lightened app-wide via FemtoIcon.
 */
@Composable
internal fun DashboardDock(
    systemStatus: SystemStatus,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    position: DockPosition = DockPosition.BOTTOM,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    dockConfig: DockConfig = DockConfig(),
) = when (position) {
    DockPosition.BOTTOM, DockPosition.TOP -> {
        HorizontalDock(
            systemStatus = systemStatus,
            onAction = onAction,
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = modifier,
            dockConfig = dockConfig,
        )
    }

    DockPosition.LEFT, DockPosition.RIGHT -> {
        VerticalDock(
            systemStatus = systemStatus,
            onAction = onAction,
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = modifier,
            dockConfig = dockConfig,
        )
    }
}

@Composable
private fun HorizontalDock(
    systemStatus: SystemStatus,
    onAction: (HomeAction) -> Unit,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    modifier: Modifier = Modifier,
    dockConfig: DockConfig = DockConfig(),
) = Surface(
    // Floating rounded glass bar: transparent + glassChrome (rounded clip + the
    // frosted backdrop) so it reads as a panel over the full-bleed map like the
    // cards; the host insets the dashboard overlays + the dock's own margins to
    // float it. On the Live backend the blur falls back to the tint.
    modifier =
        modifier
            .height(FemtoDimens.DockThickness)
            .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
) {
    val visibleNav = dockConfig.visibleNav
    Column {
        BoxWithConstraints(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
        ) {
            // The nav row and the status cluster cannot both fit on a narrow
            // portrait head unit; below the threshold the read-only status
            // cluster yields so the actionable nav stays uncut — see compactDockExtent.
            val showStatusCluster = maxWidth >= compactDockExtent(visibleNav.size)
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The slot competing with the status cluster for width; centred so
                // the capped cluster below sits in the middle of its share instead
                // of hugging the leading edge on a wide dock.
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        // Fill the slot up to the cap: a normal / narrow dock's slot is
                        // already under the cap (no-op, buttons still fill it via
                        // weight); an ultrawide dock's slot is clamped so the buttons
                        // stay a comfortable cluster instead of spreading edge to edge.
                        modifier =
                            Modifier
                                .widthIn(max = FemtoDimens.DockNavClusterMaxWidth)
                                .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Each button takes an equal weight so the nav row shares the
                        // width and shrinks toward FemtoDimens.MinTouchTarget instead of
                        // clipping when the row is narrow.
                        visibleNav.forEachIndexed { index, id ->
                            key(id) {
                                EditableNavButton(
                                    id = id,
                                    canMoveLeft = index > 0,
                                    canMoveRight = index < visibleNav.lastIndex,
                                    canHide = visibleNav.size > 1,
                                    onAction = onAction,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                if (showStatusCluster) {
                    HorizontalDockDivider()
                    StatusCluster(
                        status = systemStatus,
                        vertical = false,
                        order = dockConfig.visibleStatus,
                        onAction = onAction,
                        modifier = Modifier.padding(start = 20.dp),
                    )
                }
            }
        }
    }
}

// The same dock content turned 90 degrees: nav buttons share the height, the
// status cluster stacks beneath them, and the divider faces the dashboard.
@Composable
private fun VerticalDock(
    systemStatus: SystemStatus,
    onAction: (HomeAction) -> Unit,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    modifier: Modifier = Modifier,
    dockConfig: DockConfig = DockConfig(),
) = Surface(
    // Floating rounded glass rail, mirroring HorizontalDock on the width; on the
    // Live backend the blur falls back to the tint.
    modifier =
        modifier
            .width(FemtoDimens.DockThickness)
            .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
) {
    val visibleNav = dockConfig.visibleNav
    Row {
        BoxWithConstraints(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 24.dp),
        ) {
            val showStatusCluster = maxHeight >= compactDockExtent(visibleNav.size)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The slot competing with the status cluster for height; centred so
                // the capped cluster below sits in the middle of its share instead
                // of hugging the leading edge on a tall rail — mirrors the
                // horizontal bar's Box wrapper, turned 90 degrees.
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        // Fill the slot up to the cap — see the horizontal bar's
                        // matching comment above for the rationale.
                        modifier =
                            Modifier
                                .heightIn(max = FemtoDimens.DockNavClusterMaxWidth)
                                .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // The same equal-weight sharing as the horizontal bar, on the
                        // height instead of the width.
                        visibleNav.forEachIndexed { index, id ->
                            key(id) {
                                EditableNavButton(
                                    id = id,
                                    canMoveLeft = index > 0,
                                    canMoveRight = index < visibleNav.lastIndex,
                                    canHide = visibleNav.size > 1,
                                    onAction = onAction,
                                    modifier = Modifier.weight(1f),
                                    vertical = true,
                                )
                            }
                        }
                    }
                }
                if (showStatusCluster) {
                    VerticalDockDivider()
                    StatusCluster(
                        status = systemStatus,
                        vertical = true,
                        order = dockConfig.visibleStatus,
                        onAction = onAction,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
        }
    }
}

// The seam divider between the horizontal bar's actionable nav and the read-only
// status cluster. One thin, low-alpha rule; the color + thickness recipe itself
// lives in FemtoVerticalDivider so it is not re-copied here. Its length is
// [FemtoDividerLength].
@Composable
private fun HorizontalDockDivider(modifier: Modifier = Modifier) =
    FemtoVerticalDivider(
        modifier =
            modifier
                .padding(start = 4.dp)
                .height(FemtoDividerLength),
    )

// [HorizontalDockDivider]'s counterpart for the vertical rail, turned 90 degrees.
@Composable
private fun VerticalDockDivider(modifier: Modifier = Modifier) =
    FemtoHorizontalDivider(
        modifier =
            modifier
                .padding(top = 4.dp)
                .width(FemtoDividerLength),
    )

@Composable
private fun NavButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Accessibility-service description of the long-click action (e.g. "double-tap
    // and hold to Edit dock"); combinedClickable already exposes the action itself,
    // this just names it instead of leaving TalkBack to announce a generic "long click".
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    // A long-press-triggered popup (the dock's edit menu) anchored to this same Box.
    menu: @Composable () -> Unit = {},
) = Box(
    // The minimum floor keeps every tap target legal in both orientations; the
    // hosting bar / rail stretches the free axis through the weight.
    modifier =
        modifier
            .defaultMinSize(minWidth = FemtoDimens.MinTouchTarget, minHeight = FemtoDimens.MinTouchTarget)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onLongClick = onLongClick, onLongClickLabel = onLongClickLabel, onClick = onClick)
            .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(26.dp),
    )
    menu()
}

/**
 * A dock nav button wired for in-place editing: a normal tap still dispatches
 * [id]'s launch action ([navSpecFor]); a long press opens [DockEditMenu] —
 * Move left/right (Move up/down when [vertical], swap [id] one step within
 * the visible nav order) and Hide (drop it), governed by [canMoveLeft] /
 * [canMoveRight] / [canHide] from the caller's position in the visible list,
 * plus the always-present Reset dock. `combinedClickable` (not a second
 * gesture detector layered over the tap) is the reliable tap-vs-long-press
 * split here — see [NavButton] — so the gesture never leaks through to the
 * map behind or fires a spurious launch; it also exposes the long click to
 * accessibility services for free, which [NavButton]'s `onLongClickLabel`
 * then names.
 */
@Composable
private fun EditableNavButton(
    id: DockNavId,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canHide: Boolean,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    // True on the vertical rail (DockPosition.LEFT / RIGHT), where a -1/+1
    // move is up/down rather than left/right — see DockEditMenu.
    vertical: Boolean = false,
) {
    val spec = navSpecFor(id)
    var menuOpen by remember { mutableStateOf(false) }
    NavButton(
        icon = spec.icon,
        description = stringResource(spec.labelRes),
        onClick = { onAction(spec.action) },
        modifier = modifier,
        onLongClickLabel = stringResource(R.string.dock_edit),
        onLongClick = { menuOpen = true },
        menu = {
            DockEditMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                canMoveLeft = canMoveLeft,
                canMoveRight = canMoveRight,
                canHide = canHide,
                onMoveLeft = { onAction(HomeAction.MoveDockNav(id, -1)) },
                onMoveRight = { onAction(HomeAction.MoveDockNav(id, 1)) },
                onHide = { onAction(HomeAction.HideDockNav(id)) },
                onResetDock = { onAction(HomeAction.ResetDock) },
                vertical = vertical,
            )
        },
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
