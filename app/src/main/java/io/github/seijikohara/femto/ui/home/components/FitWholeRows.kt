package io.github.seijikohara.femto.ui.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vertical stack that places [content]'s children top to bottom, but drops any
 * trailing child whose full height would not fit inside the incoming height
 * constraint — the shared "clip on a whole-row boundary" primitive for the
 * calendar and weather cards. A `LazyColumn` (or a plain scrollable `Column`)
 * bounded by an ancestor's height cap still composes and lays out its next
 * item even when only a sliver of it is visible, which is how the calendar's
 * stray dash remnant, the weather's label-less trailing icon row, and the
 * Now Playing panel's half-cut queue row happened
 * (both cards share the same capped-height cluster; see
 * `DashboardScaffold`'s `CardClusterMaxHeight`). This layout instead measures
 * every child against an unbounded height first, then accepts children in
 * order while the running total — each child's height plus the
 * [verticalGap] before it — still fits the available height; the first
 * child that would overflow, and everything after it, is left out entirely.
 *
 * The first [mandatoryCount] children are always placed even if they
 * overflow (e.g. the weather card's head + metrics, or the calendar's today
 * row) so the card never renders completely empty; every child after that is
 * dropped once it no longer fits. Reports the full incoming height when it
 * is bounded (so the caller's `weight` / `fillMaxHeight` sizing holds), or
 * the content's natural height when unbounded.
 */
@Composable
internal fun FitWholeRows(
    modifier: Modifier = Modifier,
    verticalGap: Dp = 0.dp,
    mandatoryCount: Int = 0,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = verticalGap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val accepted = mutableListOf<Placeable>()
        var usedHeight = 0
        for (index in measurables.indices) {
            val placeable = measurables[index].measure(loose)
            val gapBefore = if (accepted.isEmpty()) 0 else gapPx
            val projectedHeight = usedHeight + gapBefore + placeable.height
            val fits = !constraints.hasBoundedHeight || projectedHeight <= constraints.maxHeight
            if (index >= mandatoryCount && !fits) break
            accepted += placeable
            usedHeight = projectedHeight
        }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else accepted.maxOfOrNull { it.width } ?: 0
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else usedHeight
        layout(width, height) {
            var y = 0
            accepted.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height + gapPx
            }
        }
    }
}
