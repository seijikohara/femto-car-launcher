package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.theme.FemtoDimens

// The gap between two columns of category rows.
private val CategoryColumnGap = 8.dp

/**
 * Render the System category's "Reset to defaults" row. A tap opens [ResetSettingsDialog] over [categories], the
 * categories Settings lists (visibleSettingsCategories), and [onReset] receives the reset the person chose.
 */
@Composable
internal fun ResetSettingsRow(
    categories: List<SettingsCategoryId>,
    onReset: (SettingsAction.ResetSettings) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    SettingsListItem(
        title = stringResource(R.string.settings_reset_to_defaults),
        shapes = shapes,
        modifier = modifier,
        onClick = { dialogOpen = true },
    ) {
        TrailingIcon(Lucide.RotateCcw)
    }
    if (dialogOpen) {
        ResetSettingsDialog(
            categories = categories,
            onReset = {
                onReset(it)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

/**
 * Return the reset the checklist asks for, given the [listed] sections and those the person [unticked]. With every
 * listed section ticked the reset covers every section, listed or not, so a category this build or map provider
 * does not list is reset too, as the full reset always did; otherwise it covers exactly the ticked ones.
 */
internal fun resetSettingsAction(
    listed: List<SettingsSectionId>,
    unticked: Set<SettingsSectionId>,
    deleteEnteredValues: Boolean,
): SettingsAction.ResetSettings =
    SettingsAction.ResetSettings(
        sections =
            if (listed.none { it in unticked }) {
                SettingsSectionId.entries.toSet()
            } else {
                listed.filterNot { it in unticked }.toSet()
            },
        deleteEnteredValues = deleteEnteredValues,
    )

/**
 * Render the checklist of what a reset restores: one cell per category of [categories] that has settings of its own,
 * under the category's own label and ticked, then the keys and addresses the person entered, not ticked. Reset is
 * enabled while any row is ticked and sends [resetSettingsAction]'s reset to [onReset].
 *
 * The category cells sit in as many equal columns as fit the dialog (CategoryGrid), so the whole list shows on a head
 * unit; each cell is one checkbox toggle target, like the visible-calendars picker's rows. Where even that does not
 * fit, the rows scroll, with a divider at each edge they can scroll past; the line naming what this reset leaves
 * alone stays under them.
 */
@Composable
internal fun ResetSettingsDialog(
    categories: List<SettingsCategoryId>,
    onReset: (SettingsAction.ResetSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The rows the person unticked, rather than those ticked, so a category that
    // joins the list while the dialog is open starts ticked like the others.
    // Plain remember: the dialog opens with the default ticks each time.
    var unticked by remember { mutableStateOf(emptySet<SettingsSectionId>()) }
    var deleteEnteredValues by remember { mutableStateOf(false) }
    val rows = categories.mapNotNull { category -> category.sectionId?.let { category to it } }
    val reset = resetSettingsAction(rows.map { (_, section) -> section }, unticked, deleteEnteredValues)
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.settings_reset_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    ScrollEdgeDivider(scroll = scroll, forward = false)
                    Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
                        CategoryGrid {
                            rows.forEach { (category, section) ->
                                ResetChoice(
                                    title = stringResource(category.titleRes),
                                    checked = section !in unticked,
                                    onCheckedChange = { ticked ->
                                        unticked = if (ticked) unticked - section else unticked + section
                                    },
                                )
                            }
                        }
                        ResetChoice(
                            title = stringResource(R.string.settings_reset_entered_values),
                            summary = stringResource(R.string.settings_reset_entered_values_desc),
                            checked = deleteEnteredValues,
                            onCheckedChange = { deleteEnteredValues = it },
                        )
                    }
                    ScrollEdgeDivider(scroll = scroll, forward = true)
                }
                Text(
                    text = stringResource(R.string.settings_reset_not_included),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = reset.sections.isNotEmpty() || reset.deleteEnteredValues,
                onClick = { onReset(reset) },
                modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
            ) {
                Text(text = stringResource(R.string.settings_reset_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget)) {
                Text(text = stringResource(R.string.settings_cancel))
            }
        },
    )
}

// The divider at one edge of the scrolling rows, shown only while the rows can
// scroll past that edge. It always takes its place, so showing it never moves
// the rows; the scroll position is read in the draw layer alone.
@Composable
private fun ScrollEdgeDivider(
    scroll: ScrollState,
    forward: Boolean,
    modifier: Modifier = Modifier,
) = HorizontalDivider(
    modifier =
        modifier.graphicsLayer {
            val canScrollPast = if (forward) scroll.canScrollForward else scroll.canScrollBackward
            alpha = if (canScrollPast) 1f else 0f
        },
)

// Lays its cells out in as many equal columns as fit the width at the widest
// cell's natural width (its label on one line), filling rows left to right; a
// narrow dialog, or a large font, gets fewer columns, down to one. Every cell in
// a row takes the row's height.
@Composable
private fun CategoryGrid(
    modifier: Modifier = Modifier,
    gap: Dp = CategoryColumnGap,
    content: @Composable () -> Unit,
) = Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
    val gapPx = gap.roundToPx()
    val widestCell = measurables.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
    val columns =
        if (constraints.hasBoundedWidth) {
            ((constraints.maxWidth + gapPx) / (widestCell + gapPx)).coerceIn(1, maxOf(1, measurables.size))
        } else {
            maxOf(1, measurables.size)
        }
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else columns * widestCell + (columns - 1) * gapPx
    val cellWidth = (width - (columns - 1) * gapPx) / columns
    val lines =
        measurables.chunked(columns).map { line ->
            val rowHeight = line.maxOf { it.minIntrinsicHeight(cellWidth) }
            line.map { it.measure(Constraints.fixed(cellWidth, rowHeight)) }
        }
    layout(width, lines.sumOf { line -> line.maxOf { it.height } }) {
        lines.fold(0) { y, line ->
            line.forEachIndexed { column, cell -> cell.placeRelative(column * (cellWidth + gapPx), y) }
            y + line.maxOf { it.height }
        }
    }
}

// One checklist row: the whole row is the toggle target (toggleable with
// Role.Checkbox), so the Checkbox is visual-only and a tap never double-fires.
@Composable
private fun ResetChoice(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) = Row(
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Checkbox(checked = checked, onCheckedChange = null)
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
