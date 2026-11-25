package io.github.seijikohara.femto.ui.settings.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.theme.FemtoDimens

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
 * Render the checklist of what a reset restores: one row per category of [categories] that has settings of its own,
 * under the category's own label and ticked, then the keys and addresses the person entered, not ticked. Reset is
 * enabled while any row is ticked and sends exactly the ticked rows to [onReset].
 *
 * Each row is one checkbox toggle target, like the visible-calendars picker's rows. The rows scroll inside the dialog
 * on a short display; the line naming what this reset leaves alone stays under them.
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
    val chosen = rows.map { (_, section) -> section }.filterNot { it in unticked }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.settings_reset_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    rows.forEach { (category, section) ->
                        ResetChoice(
                            title = stringResource(category.titleRes),
                            checked = section !in unticked,
                            onCheckedChange = { ticked ->
                                unticked =
                                    if (ticked) unticked - section else unticked + section
                            },
                        )
                    }
                    ResetChoice(
                        title = stringResource(R.string.settings_reset_entered_values),
                        summary = stringResource(R.string.settings_reset_entered_values_desc),
                        checked = deleteEnteredValues,
                        onCheckedChange = { deleteEnteredValues = it },
                    )
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
                enabled = chosen.isNotEmpty() || deleteEnteredValues,
                onClick = { onReset(SettingsAction.ResetSettings(chosen, deleteEnteredValues = deleteEnteredValues)) },
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
