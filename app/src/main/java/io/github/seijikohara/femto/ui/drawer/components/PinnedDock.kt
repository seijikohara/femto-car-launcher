package io.github.seijikohara.femto.ui.drawer.components

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import io.github.seijikohara.femto.ui.home.components.EditDoneButton
import io.github.seijikohara.femto.ui.home.components.FemtoHorizontalDivider
import io.github.seijikohara.femto.ui.home.components.RemoveBadge
import io.github.seijikohara.femto.ui.home.components.reorderByDrag
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.tileLabel
import kotlin.math.abs

private val DockIconLabelGap = 4.dp
private val DockVerticalPadding = 8.dp

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
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(horizontal = FemtoDimens.ScreenPadding, vertical = DockVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
        // Centre the taller "Done" chip (added as a trailing item in edit mode)
        // against the icon+label tiles rather than top-aligning it.
        verticalAlignment = Alignment.CenterVertically,
        // No horizontal scroll while editing: the tiles take immediate drags to
        // reorder (see below), so the row must not consume them as a scroll —
        // the same reason the dashboard dock's edit strip does not scroll.
        userScrollEnabled = !editing,
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
                onEnterEdit = { editing = true },
                modifier =
                    Modifier
                        // The held tile rides above its neighbours and tracks
                        // the finger; everyone else stays put (the swap
                        // animation is the position change itself).
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationX = if (dragging) dragDelta else 0f }
                        // Immediate drag ONLY in edit mode (entered by the tile's
                        // long-press below), matching the dashboard dock: a plain
                        // press-drag on a tile reorders — no second long-press.
                        .then(
                            if (editing) {
                                Modifier.pointerInput(key, stepPx) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingKey = key
                                            dragDelta = 0f
                                            dragTravelled = false
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDelta += amount.x
                                            if (abs(dragDelta) > viewConfiguration.touchSlop) {
                                                dragTravelled = true
                                            }
                                            val index = order.indexOfFirst { it.componentName.flattenToString() == key }
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
                                                onReorder(order.map { it.componentName.flattenToString() })
                                            }
                                            draggingKey = null
                                            dragDelta = 0f
                                        },
                                        onDragCancel = {
                                            if (dragTravelled) {
                                                order.clear()
                                                order.addAll(apps)
                                            }
                                            draggingKey = null
                                            dragDelta = 0f
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
            )
        }
        // The "Done" chip trails the pins inline (matching the dashboard dock's
        // edit strip) rather than sitting in a separate row above them.
        if (editing) {
            item(key = "pinned-dock-done") {
                EditDoneButton(onClick = { editing = false })
            }
        }
    }
}

@Composable
private fun DockTile(
    entry: AppEntry,
    dimensions: DrawerDimensions,
    editing: Boolean,
    onLaunch: (ComponentName) -> Unit,
    onUnpin: (ComponentName) -> Unit,
    onEnterEdit: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier) {
    Column(
        modifier =
            Modifier
                .width(dimensions.tileWidth)
                .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
                // Normal mode: tap launches, long-press enters edit mode. Edit
                // mode has NO tile clickable — the × badge owns removal and the
                // parent modifier owns the reorder drag, so a stray tap does
                // nothing rather than launching.
                .then(
                    if (editing) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = { onLaunch(entry.componentName) },
                            onLongClick = onEnterEdit,
                        )
                    },
                ),
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
