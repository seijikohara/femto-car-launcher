package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.cardCta

// The shared edit-mode primitives for reorderable icon collections — the drawer's
// pinned dock and the dashboard dock's nav row. One long-press enters edit mode,
// drag reorders, a × badge removes (unpin / hide), and a Done pill exits, so the
// two surfaces read as one interaction rather than two divergent gestures.

// A deliberate sub-floor tap target (< FemtoDimens.MinTouchTarget), sanctioned
// like the map controls' 48 dp exception (AGENTS.md#automotive-overrides). The
// badge is a corner affordance on a tile whose BODY is the >= 64 dp drag
// handle; sizing the × to the floor would blanket the tile and make an
// accidental unpin/hide during a drag-to-arrange more likely, not less. Edit
// mode is a deliberate, stationary action, not a glance-and-tap.
private val RemoveBadgeSize = 28.dp

/**
 * Advance a horizontal/vertical drag-reorder by one gesture frame: while the
 * accumulated [dragDelta] has crossed more than half a slot ([stepPx]) the
 * dragged item at [fromIndex] swaps one position in that direction and the
 * delta is rebased on the new slot. Returns the (possibly) reordered list and
 * the residual delta. Pure, so the swap math is JVM-unit-testable and shared by
 * both docks regardless of axis (the caller feeds the along-axis delta).
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

/**
 * The edit-mode remove (×) badge: an error-tinted circle tapped to drop the
 * item (unpin in the drawer, hide on the dashboard dock). Shown only in edit
 * mode, so it never competes with a launch tap in normal use. [label] is the
 * content description (surface-specific: "Remove from dock" vs "Hide").
 */
@Composable
internal fun RemoveBadge(
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

/**
 * "Done" pill that leaves edit mode. A filled tonal chip so it reads as the
 * primary way out; sized to the automotive touch floor. [label] is passed so
 * the string stays each surface's own (both currently share
 * [R.string.edit_done]).
 */
@Composable
internal fun EditDoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.edit_done),
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
        text = label,
        style = MaterialTheme.typography.cardCta(),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
