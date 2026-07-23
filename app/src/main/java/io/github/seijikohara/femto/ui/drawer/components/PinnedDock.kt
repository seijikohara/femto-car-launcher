package io.github.seijikohara.femto.ui.drawer.components

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.ui.drawer.DrawerDimensions
import io.github.seijikohara.femto.ui.drawer.dimensions
import io.github.seijikohara.femto.ui.home.components.FemtoHorizontalDivider
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.tileLabel
import kotlin.math.abs

private val DockIconLabelGap = 4.dp
private val DockVerticalPadding = 8.dp
private val RemoveBadgeSize = 28.dp

/**
 * Pinned-apps dock fixed at the bottom of the drawer sheet: a horizontally
 * scrolling row of icon + label tiles in pin order, separated from the
 * scrolling app grid by a hairline seam over the panel glass. Tap launches.
 *
 * Reordering and unpinning live behind one explicit **edit mode** — the shared
 * interaction the dashboard dock uses too — rather than overloading the tap.
 * Long-press any tile enters edit mode: from that same gesture the held tile
 * drags to reorder (it follows the finger and swaps as it crosses neighbours,
 * committing through [onReorder] on lift), each tile shows a remove (×) badge
 * that unpins via [onUnpin], and a "Done" control exits. A normal tap launches
 * only while NOT editing. App info / uninstall are reached from the app grid's
 * long-press menu, not here — the dock's job is pin ordering.
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
    // The grid's tile metric, shared so the dock's columns ride the same left
    // line and pitch as the app grid and Recent row above.
    val dimensions = iconSize.dimensions()
    // Local working order: drag swaps mutate this list optimistically per
    // frame; the persisted order arrives back through [apps] and re-seeds it.
    val order = remember(apps) { apps.toMutableStateList() }
    var editing by remember { mutableStateOf(false) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragTravelled by remember { mutableStateOf(false) }
    val stepPx = with(LocalDensity.current) { (dimensions.tileWidth + FemtoDimens.GridGutter).toPx() }
    // The "Done" affordance sits above the tiles (always visible, never scrolled
    // off) so exiting edit mode is one reachable tap rather than a hunt.
    if (editing) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FemtoDimens.ScreenPadding),
            horizontalArrangement = Arrangement.End,
        ) {
            EditDoneButton(onClick = { editing = false })
        }
    }
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
                editing = editing,
                onLaunch = onLaunch,
                onUnpin = onUnpin,
                modifier =
                    Modifier
                        // The held tile rides above its neighbours and tracks
                        // the finger; everyone else stays put (the swap
                        // animation is the position change itself).
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationX = if (dragging) dragDelta else 0f }
                        .pointerInput(key, stepPx) {
                            detectDragGesturesAfterLongPress(
                                // The long-press itself enters edit mode; the same
                                // gesture then drags if the finger travels, so one
                                // press both reveals the × badges and reorders.
                                onDragStart = {
                                    editing = true
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
                                // Commit a travelled reorder; a no-travel press just
                                // leaves edit mode active (the × badges are now shown).
                                onDragEnd = {
                                    if (dragTravelled) {
                                        onReorder(order.map { it.componentName.flattenToString() })
                                    }
                                    draggingKey = null
                                    dragDelta = 0f
                                },
                                onDragCancel = {
                                    // A travelled drag cancelled mid-flight reverts to
                                    // the persisted order (nothing was committed).
                                    if (dragTravelled) {
                                        order.clear()
                                        order.addAll(apps)
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
    dimensions: DrawerDimensions,
    editing: Boolean,
    onLaunch: (ComponentName) -> Unit,
    onUnpin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier) {
    Column(
        modifier =
            Modifier
                .width(dimensions.tileWidth)
                .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
                // Tap launches only while NOT editing: in edit mode the tile is a
                // reorder handle and the × badge owns removal, so a stray tap must
                // not fire the app. The long-press (owned by the parent drag
                // detector) enters edit mode and reorders.
                .clickable(onClick = { if (!editing) onLaunch(entry.componentName) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = BitmapPainter(entry.icon.asImageBitmap()),
            contentDescription = entry.label,
            tint = Color.Unspecified,
            modifier = Modifier.size(dimensions.gridIconSize),
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
    if (editing) {
        RemoveBadge(
            label = stringResource(R.string.drawer_remove_pin),
            onClick = { onUnpin(entry.componentName) },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

// The edit-mode remove (×) badge: an error-tinted circle over the tile's
// top-end corner, tapped to unpin. Shown only in edit mode, so it never
// competes with a launch tap in normal use.
@Composable
private fun RemoveBadge(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier =
        modifier
            .size(RemoveBadgeSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
) {
    FemtoIcon(
        imageVector = Lucide.X,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.size(16.dp),
    )
}

// "Done" pill that leaves edit mode. A filled tonal chip so it reads as the
// primary way out; sized to the automotive touch floor.
@Composable
private fun EditDoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    FemtoIcon(
        imageVector = Lucide.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(18.dp),
    )
    Text(
        text = stringResource(R.string.drawer_edit_done),
        style = MaterialTheme.typography.cardCta(),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
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
