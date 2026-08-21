package io.github.seijikohara.femto.ui.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.composables.icons.lucide.Bluetooth
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Settings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DockWidth
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.cardCta
import kotlin.math.abs

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

// Width the horizontal bar's fixed-margin (pill) fit test reserves for the status
// side when the status cluster shows: the nav/status divider plus the read-only
// [StatusCluster]. The cluster is five 20 dp indicator icons — two of them (GPS,
// battery) captioned a touch wider than the icon — joined by 18 dp gaps (~180 dp),
// plus the cluster's own DockButtonMargin padding and the divider (~40 dp). The
// rendered cluster still measures itself; this figure only decides whether the
// pill fits, and is rounded up so an underestimate can never let the pill overflow
// — the bias is always toward the weight-shared fallback, which shrinks to fit and
// never clips (see HorizontalDock).
private val DockStatusSideReserve: Dp = 240.dp

// Whether the dock renders the read-only status cluster along [extent] (the
// horizontal bar's width, the vertical rail's height): it needs an indicator left
// to show, and room to spare once the actionable nav has its own (see
// compactDockExtent). The user can hide every indicator, which drops the cluster
// and its divider at any extent — and, on the horizontal bar, releases the width
// the fit test below reserves for it.
private fun dockShowsStatus(
    extent: Dp,
    navCount: Int,
    statusCount: Int,
): Boolean = statusCount > 0 && extent >= compactDockExtent(navCount)

// Whether the fixed-margin (pill) horizontal dock fits [availableWidth]: each nav
// button is MinTouchTarget + two DockButtonMargins wide, plus DockStatusSideReserve
// when the status cluster shows.
private fun horizontalDockPillFits(
    availableWidth: Dp,
    navCount: Int,
    statusCount: Int,
): Boolean {
    val pillButtonWidth = FemtoDimens.MinTouchTarget + FemtoDimens.DockButtonMargin * 2
    val statusReserve = if (dockShowsStatus(availableWidth, navCount, statusCount)) DockStatusSideReserve else 0.dp
    return pillButtonWidth * navCount + statusReserve <= availableWidth
}

// Whether the horizontal bar draws as the centred pill: the user's [dockWidth]
// choice first, then the fit test. Only [DockWidth.COMPACT] can reach the pill, and
// even then only where it fits — the pill is a fixed footprint, so forcing it onto
// a narrow bar clips the leading / trailing buttons below FemtoDimens.MinTouchTarget
// (AGENTS.md#automotive-overrides), while [DockWidth.EXTENDED]'s weight-shared bar
// shrinks toward that floor and never clips. So the preference can turn the pill
// OFF anywhere, but never on where the geometry says no.
//
// Called from two places: HorizontalDock picks its layout from it, and
// DashboardScaffold asks it again (through mapCreditClearsDock) to decide whether
// the freed bottom-left corner lets the map attribution sit flush there instead of
// clearing the dock. [availableWidth] is the BAR's width, never the viewport's —
// the bar floats inset by dockFloatPadding, and the scaffold subtracts that before
// calling. Feeding the two calls different widths is what drops the credit under a
// full-width bar, so both must stay on this one signature.
internal fun horizontalDockUsesPill(
    dockWidth: DockWidth,
    availableWidth: Dp,
    navCount: Int,
    statusCount: Int,
): Boolean = dockWidth == DockWidth.COMPACT && horizontalDockPillFits(availableWidth, navCount, statusCount)

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
    dockWidth: DockWidth = DockWidth.COMPACT,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    dockConfig: DockConfig = DockConfig(),
    motionTier: MotionTier = MotionTier.STANDARD,
) = when (position) {
    DockPosition.BOTTOM, DockPosition.TOP -> {
        HorizontalDock(
            systemStatus = systemStatus,
            onAction = onAction,
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = modifier,
            dockWidth = dockWidth,
            dockConfig = dockConfig,
            motionTier = motionTier,
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
            motionTier = motionTier,
        )
    }
}

@Composable
private fun HorizontalDock(
    systemStatus: SystemStatus,
    onAction: (HomeAction) -> Unit,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    dockWidth: DockWidth,
    modifier: Modifier = Modifier,
    dockConfig: DockConfig = DockConfig(),
    motionTier: MotionTier = MotionTier.STANDARD,
) {
    val visibleNav = dockConfig.visibleNav
    // Read the available width before committing to a layout. The fixed-margin
    // pill is a fixed footprint: on a wide dock it fits and reads as a compact,
    // centred glass pill, but on the reference 853 dp head unit or a portrait
    // phone the nav buttons + status cluster overflow it and the glass clips the
    // leading / trailing items. When the pill would overflow — or the user asked
    // for DockWidth.EXTENDED — take the weight-shared layout that shrinks the nav
    // toward FemtoDimens.MinTouchTarget so every button stays reachable and nothing
    // clips (AGENTS.md#automotive-overrides, AGENTS.md#launcher-behavior); see
    // horizontalDockUsesPill. The same choice sizes the glass: the pill wraps its
    // content (DashboardScaffold centres it), the fallback fills the width so the
    // weight distribution has room.
    BoxWithConstraints(modifier = modifier) {
        // Below the threshold the read-only status cluster yields so the actionable
        // nav keeps room, and an emptied cluster drops at any width — see
        // dockShowsStatus. Applied in both layouts.
        val showStatusCluster = dockShowsStatus(maxWidth, visibleNav.size, dockConfig.visibleStatus.size)
        val usesPill = horizontalDockUsesPill(dockWidth, maxWidth, visibleNav.size, dockConfig.visibleStatus.size)
        // Long-pressing any nav button flips the bar into edit mode (see
        // DockNavEditStrip). The strip wraps its content just like the pill, so
        // the bar keeps its footprint — same width class (pill wraps / fallback
        // fills), no widening or button shift on entering edit. The back gesture
        // exits (no room for a Done chip without widening past the pill).
        var editing by remember { mutableStateOf(false) }
        BackHandler(enabled = editing) { editing = false }

        Surface(
            // Floating rounded glass bar: transparent + glassChrome (rounded clip +
            // the frosted backdrop) so it reads as a panel over the full-bleed map
            // like the cards. The pill wraps its content; the fallback fills the
            // width. On the Live backend the blur falls back to the tint.
            modifier =
                Modifier
                    .height(FemtoDimens.DockThickness)
                    .then(if (usesPill) Modifier else Modifier.fillMaxWidth())
                    .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            if (editing) {
                DockNavEditStrip(
                    navOrder = dockConfig.navOrder,
                    navHidden = dockConfig.navHidden,
                    vertical = false,
                    systemStatus = systemStatus,
                    visibleStatus = dockConfig.visibleStatus,
                    showStatusCluster = showStatusCluster,
                    motionTier = motionTier,
                    onAction = onAction,
                    modifier = Modifier.fillMaxHeight().padding(horizontal = FemtoDimens.DockButtonMargin),
                )
            } else if (usesPill) {
                // Fixed-margin pill: each button reserves a DockButtonMargin on both
                // sides, so adjacent buttons sit two margins apart and the first /
                // last button sits one margin from the bar edge. The row wraps its
                // content; DashboardScaffold centres the pill. fillMaxHeight centres
                // the row vertically in the bar's fixed height.
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    visibleNav.forEach { id ->
                        key(id) {
                            EditableNavButton(
                                id = id,
                                onAction = onAction,
                                onEnterEdit = { editing = true },
                                modifier = Modifier.padding(horizontal = FemtoDimens.DockButtonMargin),
                            )
                        }
                    }
                    if (showStatusCluster) {
                        HorizontalDockDivider()
                        StatusCluster(
                            status = systemStatus,
                            vertical = false,
                            order = dockConfig.visibleStatus,
                            onAction = onAction,
                            motionTier = motionTier,
                            modifier = Modifier.padding(horizontal = FemtoDimens.DockButtonMargin),
                        )
                    }
                }
            } else {
                // Weight-shared fallback: the bar fills the width and the nav buttons
                // share it equally, shrinking toward MinTouchTarget rather than
                // clipping. The inner 24 dp padding keeps the end buttons off the
                // glass's rounded corners.
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
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
                            visibleNav.forEach { id ->
                                key(id) {
                                    EditableNavButton(
                                        id = id,
                                        onAction = onAction,
                                        onEnterEdit = { editing = true },
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
                            motionTier = motionTier,
                            modifier = Modifier.padding(start = 20.dp),
                        )
                    }
                }
            }
        }
        // Floating edit-mode toolbar above the bar (Reset + Done) as a Popup, so
        // it never widens the bar or shifts the buttons. focusable=false so the
        // drag / badges below keep receiving input.
        if (editing) {
            val toolbarOffsetY = with(LocalDensity.current) { -(FemtoDimens.MinTouchTarget + 24.dp).roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, toolbarOffsetY),
                properties = PopupProperties(focusable = false),
            ) {
                DockEditToolbar(
                    onReset = { onAction(HomeAction.ResetDock) },
                    onDone = { editing = false },
                )
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
    motionTier: MotionTier = MotionTier.STANDARD,
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
    // Long-press a nav button to edit; the rail swaps to the vertical reorder
    // strip (drag + ×), keeping the status cluster in place. Back exits.
    var editing by remember { mutableStateOf(false) }
    BackHandler(enabled = editing) { editing = false }
    // Both branches take the cluster gate from this one measured height, so edit
    // mode cannot grow a cluster the rail drops at rest: below compactDockExtent
    // the nav keeps the height either way (see dockShowsStatus). The rail's inner
    // margin sits here rather than on each branch for the same reason — the height
    // measured is the height the content gets.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(vertical = 24.dp)) {
        val showStatusCluster = dockShowsStatus(maxHeight, visibleNav.size, dockConfig.visibleStatus.size)
        if (editing) {
            DockNavEditStrip(
                navOrder = dockConfig.navOrder,
                navHidden = dockConfig.navHidden,
                vertical = true,
                systemStatus = systemStatus,
                visibleStatus = dockConfig.visibleStatus,
                showStatusCluster = showStatusCluster,
                motionTier = motionTier,
                onAction = onAction,
                modifier = Modifier.fillMaxSize(),
            )
            // Floating Reset + Done toolbar beside the rail (a Popup, so the rail
            // width is untouched). Back also exits.
            val toolbarOffsetX = with(LocalDensity.current) { FemtoDimens.MinTouchTarget.roundToPx() }
            Popup(
                alignment = Alignment.CenterEnd,
                offset = IntOffset(toolbarOffsetX, 0),
                properties = PopupProperties(focusable = false),
            ) {
                DockEditToolbar(
                    onReset = { onAction(HomeAction.ResetDock) },
                    onDone = { editing = false },
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The slot competing with the status cluster for height; centred so
                // the capped cluster below sits in the middle of its share instead
                // of hugging the leading edge on a tall rail — mirrors the
                // horizontal bar's weight-shared fallback, turned 90 degrees.
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
                        // The same equal-weight sharing as the horizontal bar's fallback,
                        // on the height instead of the width.
                        visibleNav.forEach { id ->
                            key(id) {
                                EditableNavButton(
                                    id = id,
                                    onAction = onAction,
                                    onEnterEdit = { editing = true },
                                    modifier = Modifier.weight(1f),
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
                        motionTier = motionTier,
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
 * A dock nav button in normal mode: a tap dispatches [id]'s launch action
 * ([navSpecFor]); a long press enters the dock's edit mode ([onEnterEdit]),
 * where drag reorders and a × badge hides — the same edit-mode interaction the
 * drawer's pinned dock uses (see [DockNavEditStrip]). This replaces the former
 * long-press dropdown. `combinedClickable` (not a second gesture detector
 * layered over the tap) is the reliable tap-vs-long-press split here — see
 * [NavButton] — so the gesture never leaks through to the map behind or fires a
 * spurious launch; it also exposes the long click to accessibility services for
 * free, which [NavButton]'s `onLongClickLabel` then names.
 */
@Composable
private fun EditableNavButton(
    id: DockNavId,
    onAction: (HomeAction) -> Unit,
    onEnterEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = navSpecFor(id)
    NavButton(
        icon = spec.icon,
        description = stringResource(spec.labelRes),
        onClick = { onAction(spec.action) },
        modifier = modifier,
        onLongClickLabel = stringResource(R.string.dock_edit),
        onLongClick = onEnterEdit,
    )
}

// The tile size for the dock's edit strip: the tap-target floor, so the fixed
// drag step ([reorderByDrag]) is one whole tile plus its gap. The gap matches
// the normal pill's per-button spacing (a DockButtonMargin on each side) so
// entering edit mode neither shifts the buttons nor tightens their gaps.
private val DockEditTileSize: Dp = FemtoDimens.MinTouchTarget
private val DockEditTileGap: Dp = FemtoDimens.DockButtonMargin * 2

/**
 * The dock nav row's edit mode: fixed-width reorderable icon tiles with × (hide)
 * badges, replacing the normal nav content while keeping the read-only status
 * cluster exactly where it was so the bar's footprint — and every button's
 * position — stays put on entering edit. Long-pressing a nav button enters it
 * (see [EditableNavButton]); dragging a tile reorders (committed as a new full
 * nav order via [HomeAction.SetDockNavOrder], hidden ids kept at their slots),
 * the × hides ([HomeAction.HideDockNav]); the back gesture exits (the bar has no
 * room for a Done chip without widening — see the BackHandler in the caller).
 * The tile pitch matches the pill's per-button pitch so nothing shifts. The
 * drag / × primitives ([reorderByDrag] / [RemoveBadge]) are the drawer pinned
 * dock's. [vertical] lays it out down the rail instead of across the bar.
 */
@Composable
private fun DockNavEditStrip(
    navOrder: List<DockNavId>,
    navHidden: Set<DockNavId>,
    vertical: Boolean,
    systemStatus: SystemStatus,
    visibleStatus: List<DockStatusId>,
    showStatusCluster: Boolean,
    motionTier: MotionTier,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = remember(navOrder, navHidden) { navOrder.filterNot { it in navHidden } }
    val order = remember(visible) { visible.toMutableStateList() }
    var draggingKey by remember { mutableStateOf<DockNavId?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragTravelled by remember { mutableStateOf(false) }
    val stepPx = with(LocalDensity.current) { (DockEditTileSize + DockEditTileGap).toPx() }
    val removeLabel = stringResource(R.string.dock_hide)
    val tiles: @Composable () -> Unit = {
        order.forEach { id ->
            // key(id), not positional: without it a mid-drag reorder rebinds each
            // tile's pointerInput to a new id, restarting the gesture coroutine so
            // the drag ends without onDragEnd — the reorder is never committed and
            // reverts on exit. Keying tracks the dragged tile across swaps.
            key(id) {
                val dragging = draggingKey == id
                DockNavEditTile(
                    id = id,
                    removeLabel = removeLabel,
                    onHide = { onAction(HomeAction.HideDockNav(id)) },
                    modifier =
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                val offset = if (dragging) dragDelta else 0f
                                if (vertical) translationY = offset else translationX = offset
                            }.pointerInput(id, stepPx, vertical) {
                                // Immediate drag (not after-long-press): the bar is
                                // already in edit mode, so a plain press-drag on a tile
                                // reorders — no second long-press. There is no scroll to
                                // compete with (the strip wraps its content).
                                detectDragGestures(
                                    onDragStart = {
                                        draggingKey = id
                                        dragDelta = 0f
                                        dragTravelled = false
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += if (vertical) amount.y else amount.x
                                        if (abs(dragDelta) > viewConfiguration.touchSlop) dragTravelled = true
                                        val index = order.indexOf(id)
                                        val (reordered, residual) =
                                            reorderByDrag(order.toList(), index, dragDelta, stepPx)
                                        if (reordered.size == order.size && reordered != order.toList()) {
                                            order.clear()
                                            order.addAll(reordered)
                                        }
                                        dragDelta = residual
                                    },
                                    onDragEnd = {
                                        if (dragTravelled) {
                                            onAction(
                                                HomeAction.SetDockNavOrder(
                                                    mergeNavOrder(navOrder, navHidden, order.toList()),
                                                ),
                                            )
                                        }
                                        draggingKey = null
                                        dragDelta = 0f
                                    },
                                    onDragCancel = {
                                        if (dragTravelled) {
                                            order.clear()
                                            order.addAll(visible)
                                        }
                                        draggingKey = null
                                        dragDelta = 0f
                                    },
                                )
                            },
                )
            }
        }
        // The read-only status cluster stays put (same divider + cluster as the
        // normal bar) so entering edit mode neither hides the status icons nor
        // shifts the nav buttons. It keeps its own long-press DockEditMenu.
        if (showStatusCluster) {
            if (vertical) VerticalDockDivider() else HorizontalDockDivider()
            StatusCluster(
                status = systemStatus,
                vertical = vertical,
                order = visibleStatus,
                onAction = onAction,
                motionTier = motionTier,
            )
        }
    }
    if (vertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DockEditTileGap, Alignment.CenterVertically),
        ) { tiles() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DockEditTileGap, Alignment.CenterHorizontally),
        ) { tiles() }
    }
}

// Merge a reordered VISIBLE nav list back into the full order, keeping hidden
// ids at their original absolute slots (so an unhide later restores them where
// they were) — the same invariant DockPreferences.moveWithinVisible enforces for
// step-moves, here for a whole-list drag commit.
private fun mergeNavOrder(
    fullOrder: List<DockNavId>,
    hidden: Set<DockNavId>,
    reorderedVisible: List<DockNavId>,
): List<DockNavId> {
    val next = reorderedVisible.iterator()
    return fullOrder.map { id -> if (id in hidden) id else next.next() }
}

// One edit-strip tile: the nav icon under a × (hide) badge, sized to the
// tap-target floor. Drag handling lives on the caller's [modifier].
@Composable
private fun DockNavEditTile(
    id: DockNavId,
    removeLabel: String,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier.size(DockEditTileSize)) {
    val spec = navSpecFor(id)
    Box(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        FemtoIcon(
            imageVector = spec.icon,
            contentDescription = stringResource(spec.labelRes),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
    RemoveBadge(label = removeLabel, onClick = onHide, modifier = Modifier.align(Alignment.TopEnd))
}

// Floating edit-mode toolbar, shown as a Popup just outside the dock so it never
// widens the bar or pushes the buttons: Reset dock — restore hidden buttons and
// the default order / visibility (nav AND status) via HomeAction.ResetDock — and
// Done to leave edit mode. Once the status cluster stays put in edit mode, the
// head-unit bar has no inline room for these, so they float (and the back
// gesture still exits).
@Composable
private fun DockEditToolbar(
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Row(
        modifier = Modifier.padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockEditChip(
            icon = Lucide.RotateCcw,
            label = stringResource(R.string.settings_reset_dock),
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurface,
            onClick = onReset,
        )
        DockEditChip(
            icon = Lucide.Check,
            label = stringResource(R.string.edit_done),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onDone,
        )
    }
}

@Composable
private fun DockEditChip(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    FemtoIcon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
    Text(text = label, style = MaterialTheme.typography.cardCta(), color = content)
}

// Wide dock: the fixed-margin pill fits, so the bar wraps its content to a compact
// centred pill (the host centres it) with the full status cluster.
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

// The reference 853 dp 5:3 head unit: the fixed pill would overflow (the seven
// buttons + status cluster exceed the width — the regression that clipped the
// battery indicator), so the bar falls back to the weight-shared layout and keeps
// every button and the whole status cluster visible.
@PreviewLightDark
@Preview(name = "Dashboard dock (head unit)", widthDp = 853, heightDp = 64)
@Composable
private fun DashboardDockHeadUnitPreview() {
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

// Narrow portrait head unit: the fixed pill does not fit, so the bar falls back to
// the weight-shared layout; the status cluster also drops (below compactDockExtent)
// and the nav buttons share the width down toward FemtoDimens.MinTouchTarget rather
// than clipping.
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

// Portrait phone mount (~400 dp): the tightest fallback — the status cluster drops
// and the seven nav buttons share the width, shrinking to keep every button visible
// (they cross below MinTouchTarget here, the sanctioned narrow-width trade-off over
// clipping — AGENTS.md#launcher-behavior).
@PreviewLightDark
@Preview(name = "Dashboard dock (portrait phone)", widthDp = 400, heightDp = 64)
@Composable
private fun DashboardDockCompactPreview() {
    FemtoTheme {
        DashboardDock(
            systemStatus =
                SystemStatus(
                    cellularConnected = true,
                    cellularSignalLevel = 2,
                    wifiConnected = true,
                    wifiSignalLevel = 3,
                    bluetoothEnabled = true,
                    bluetoothConnected = true,
                    batteryPercent = 55,
                    charging = false,
                    gpsFixed = true,
                    gpsSatelliteCount = 7,
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
