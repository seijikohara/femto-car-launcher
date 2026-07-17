package io.github.seijikohara.femto.ui.drawer.components

import android.content.ComponentName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PinOff
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.ui.home.components.FemtoHorizontalDivider
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.tileLabel
import kotlin.math.abs

private val DockIconLabelGap = 4.dp
private val DockVerticalPadding = 8.dp

// Per-preset dock tile dimensions, scaled in step with the drawer grid's
// presets (the dock previously hardcoded the MEDIUM values and ignored the
// user's icon-size choice). Tile widths keep a readable one-line label and
// stay above the touch-target floor via the tile's min height. Internal (not
// private): the Recent row wants tiles that look identical to the Pinned
// dock's, so it reuses this same size mapping rather than duplicating it.
internal data class DockDimensions(
    val tileWidth: Dp,
    val iconSize: Dp,
)

internal fun DrawerIconSize.dockDimensions(): DockDimensions =
    when (this) {
        DrawerIconSize.SMALL -> DockDimensions(tileWidth = 80.dp, iconSize = 48.dp)
        DrawerIconSize.MEDIUM -> DockDimensions(tileWidth = 96.dp, iconSize = 64.dp)
        DrawerIconSize.LARGE -> DockDimensions(tileWidth = 128.dp, iconSize = 88.dp)
    }

/**
 * Pinned-apps dock fixed at the bottom of the drawer sheet: a horizontally
 * scrolling row of icon + label tiles in pin order, visually separated from the
 * scrolling app grid by a divider on a raised container colour. Tap launches.
 *
 * Long-press then drag reorders: the held tile follows the finger and swaps
 * places as it crosses its neighbours; lifting commits the new order through
 * [onReorder]. A long-press lifted WITHOUT travel opens the tile menu
 * (Unpin, plus Move left / Move right — the precision-free reorder path for
 * a bumpy cabin or accessibility services). The menu deliberately opens on
 * lift, not at the long-press timeout: a focusable popup appearing mid-press
 * cancels the pointer stream and would kill the drag.
 *
 * Callers skip composing the dock when [apps] is empty.
 */
@Composable
internal fun PinnedDock(
    apps: List<AppEntry>,
    iconSize: DrawerIconSize,
    onLaunch: (ComponentName) -> Unit,
    onUnpin: (ComponentName) -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxWidth()) {
    // The shared hairline recipe, not a bare M3 divider: this seam is the only
    // thing separating the dock from the app grid, and a full-alpha divider
    // reads twice as heavy as every other hairline on the dashboard chrome.
    FemtoHorizontalDivider()
    val dimensions = iconSize.dockDimensions()
    // Local working order: drag swaps mutate this list optimistically per
    // frame; the persisted order arrives back through [apps] and re-seeds it.
    val order = remember(apps) { apps.toMutableStateList() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragTravelled by remember { mutableStateOf(false) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    val stepPx = with(LocalDensity.current) { (dimensions.tileWidth + FemtoDimens.GridGutter).toPx() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(horizontal = FemtoDimens.ScreenPadding, vertical = DockVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
        ) {
            items(items = order, key = { it.componentName.flattenToString() }) { entry ->
                val key = entry.componentName.flattenToString()
                val dragging = draggingKey == key
                DockTile(
                    entry = entry,
                    dimensions = dimensions,
                    menuOpen = menuKey == key,
                    canMoveLeft = order.indexOf(entry) > 0,
                    canMoveRight = order.indexOf(entry) < order.lastIndex,
                    onLaunch = onLaunch,
                    onUnpin = onUnpin,
                    onDismissMenu = { menuKey = null },
                    onMove = { offset ->
                        val index = order.indexOf(entry)
                        val target = (index + offset).coerceIn(0, order.lastIndex)
                        if (target != index) {
                            order.add(target, order.removeAt(index))
                            onReorder(order.map { it.componentName.flattenToString() })
                        }
                    },
                    modifier =
                        Modifier
                            // The held tile rides above its neighbours and tracks
                            // the finger; everyone else stays put (the swap
                            // animation is the position change itself).
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationX = if (dragging) dragDelta else 0f }
                            .pointerInput(key, stepPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingKey = key
                                        dragDelta = 0f
                                        dragTravelled = false
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += amount.x
                                        if (abs(dragDelta) > viewConfiguration.touchSlop) dragTravelled = true
                                        val index = order.indexOfFirst { it.componentName.flattenToString() == key }
                                        val (reordered, residual) = reorderByDrag(
                                            order.toList(),
                                            index,
                                            dragDelta,
                                            stepPx,
                                        )
                                        if (reordered.size == order.size && reordered != order.toList()) {
                                            order.clear()
                                            order.addAll(reordered)
                                        }
                                        dragDelta = residual
                                    },
                                    onDragEnd = {
                                        if (dragTravelled) {
                                            onReorder(order.map { it.componentName.flattenToString() })
                                        } else {
                                            menuKey = key
                                        }
                                        draggingKey = null
                                        dragDelta = 0f
                                    },
                                    onDragCancel = {
                                        if (dragTravelled) {
                                            // Revert the optimistic swaps: nothing
                                            // was committed, so the dock falls back
                                            // to the persisted order.
                                            order.clear()
                                            order.addAll(apps)
                                        } else {
                                            // A no-travel lift lands HERE, not in
                                            // onDragEnd: the child clickable
                                            // consumes the up (its tap), which the
                                            // detector reports as a cancel. It is
                                            // the menu gesture.
                                            menuKey = key
                                        }
                                        draggingKey = null
                                        dragDelta = 0f
                                    },
                                )
                            },
                )
            }
        }
    }
}

/**
 * Advance a horizontal drag-reorder by one gesture frame: while the
 * accumulated [dragDelta] has crossed more than half a slot ([stepPx]) the
 * dragged item at [fromIndex] swaps one position in that direction and the
 * delta is rebased on the new slot. Returns the (possibly) reordered list and
 * the residual delta. Pure, so the swap math is JVM-unit-testable.
 */
internal fun <T> reorderByDrag(
    items: List<T>,
    fromIndex: Int,
    dragDelta: Float,
    stepPx: Float,
): Pair<List<T>, Float> {
    if (fromIndex !in items.indices || stepPx <= 0f) return items to dragDelta
    val reordered = items.toMutableList()
    var index = fromIndex
    var delta = dragDelta
    while (delta > stepPx / 2 && index < reordered.lastIndex) {
        reordered.add(index + 1, reordered.removeAt(index))
        index++
        delta -= stepPx
    }
    while (delta < -stepPx / 2 && index > 0) {
        reordered.add(index - 1, reordered.removeAt(index))
        index--
        delta += stepPx
    }
    return reordered to delta
}

@Composable
private fun DockTile(
    entry: AppEntry,
    dimensions: DockDimensions,
    menuOpen: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onLaunch: (ComponentName) -> Unit,
    onUnpin: (ComponentName) -> Unit,
    onDismissMenu: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier) {
    Column(
        modifier =
            Modifier
                .width(dimensions.tileWidth)
                .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
                // Plain clickable launches on tap. The menu must NOT open at
                // the long-press timeout: a focusable popup appearing mid-press
                // cancels the original pointer stream and would make the
                // reorder drag impossible — so the dock's drag detector opens
                // it on lift instead.
                .clickable(onClick = { onLaunch(entry.componentName) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = BitmapPainter(entry.icon.asImageBitmap()),
            contentDescription = entry.label,
            tint = Color.Unspecified,
            modifier = Modifier.size(dimensions.iconSize),
        )
        Spacer(Modifier.height(DockIconLabelGap))
        // Same deterministic line box as the grid tiles (see tileLabel), so
        // dock tiles measure identically across scripts and fallbacks.
        Text(
            text = entry.label,
            style = MaterialTheme.typography.tileLabel(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
        if (canMoveLeft) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drawer_move_left)) },
                // M3's default menu-item height (48 dp) sits below the automotive floor.
                modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
                leadingIcon = { FemtoIcon(imageVector = Lucide.ArrowLeft, contentDescription = null) },
                onClick = {
                    onMove(-1)
                    onDismissMenu()
                },
            )
        }
        if (canMoveRight) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drawer_move_right)) },
                modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
                leadingIcon = { FemtoIcon(imageVector = Lucide.ArrowRight, contentDescription = null) },
                onClick = {
                    onMove(1)
                    onDismissMenu()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.drawer_unpin)) },
            modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
            leadingIcon = { FemtoIcon(imageVector = Lucide.PinOff, contentDescription = null) },
            onClick = {
                onUnpin(entry.componentName)
                onDismissMenu()
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun PinnedDockPreview() {
    val icon = createBitmap(1, 1)
    FemtoTheme {
        PinnedDock(
            apps =
                listOf(
                    AppEntry(ComponentName("com.maps", ".Main"), "Maps", icon),
                    AppEntry(ComponentName("com.music", ".Main"), "Music", icon),
                    AppEntry(ComponentName("com.phone", ".Main"), "Phone", icon),
                ),
            iconSize = DrawerIconSize.MEDIUM,
            onLaunch = {},
            onUnpin = {},
            onReorder = {},
        )
    }
}
