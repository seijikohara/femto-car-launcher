package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import kotlin.math.roundToInt

// Shared back-arrow icon size: the screen-level Header (back to the
// dashboard) and SettingsCategoryDetail's narrow-mode back arrow (back to the
// category list) both use it, so the two back affordances read as the same
// control at a glance.
private val BackIconSize = 28.dp

@Composable
internal fun Header(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Box(
        modifier =
            Modifier
                .size(FemtoDimens.MinTouchTarget)
                .clipClickable(onBack),
        contentAlignment = Alignment.Center,
    ) {
        FemtoIcon(
            imageVector = Lucide.ArrowLeft,
            contentDescription = stringResource(R.string.settings_back),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(BackIconSize),
        )
    }
    Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

// The master-detail layout's right pane (wide) / detail view (narrow list-detail):
// a header row (an optional back arrow for the narrow flow's return-to-list,
// the category title, and an optional reset affordance) over a flat rounded
// card holding the category's rows, scrolling independently of the rail /
// list. Category SELECTION (the rail in wide, the list in narrow — see
// SettingsCategoryList) is what used to be the collapsible per-section toggle;
// this pane always shows its content, since only one category is ever mounted
// at a time.
@Composable
internal fun SettingsCategoryDetail(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var resetDialogOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = FemtoDimens.MinTouchTarget)
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier.size(FemtoDimens.MinTouchTarget).clipClickable(onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    FemtoIcon(
                        imageVector = Lucide.ArrowLeft,
                        contentDescription = stringResource(R.string.settings_category_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(BackIconSize),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (onReset != null) {
                SectionResetButton(onClick = { resetDialogOpen = true })
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
    if (onReset != null && resetDialogOpen) {
        ResetConfirmDialog(
            title = stringResource(R.string.settings_reset_section_confirm_title, title),
            message = stringResource(R.string.settings_reset_section_confirm_message),
            onConfirm = {
                onReset()
                resetDialogOpen = false
            },
            onDismiss = { resetDialogOpen = false },
        )
    }
}

// A compact >= MinTouchTarget tap target for the per-category reset
// affordance in SettingsCategoryDetail's header row.
@Composable
private fun SectionResetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.settings_reset_section)
    Box(
        modifier =
            modifier
                .size(FemtoDimens.MinTouchTarget)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        FemtoIcon(
            imageVector = Lucide.RotateCcw,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FemtoDimens.InlineIconSize),
        )
    }
}

// A sub-group label inside a section card, separating related rows under a
// section heading. Same 18sp as the section heading but muted (onSurfaceVariant
// vs the section's primary) and indented to align with row content, so it reads
// as a child cluster rather than a new section.
@Composable
internal fun SettingsSubheader(
    title: String,
    modifier: Modifier = Modifier,
) = Text(
    text = title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp),
)

// A single-choice row: shows the current value as its summary and opens a radio
// dialog on tap (the Android ListPreference pattern).
@Composable
internal fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    SettingRow(
        title = title,
        modifier = modifier.clickable { dialogOpen = true },
        summary = options.firstOrNull { it.first == selected }?.second,
    ) {
        TrailingIcon(Lucide.ChevronRight)
    }
    if (dialogOpen) {
        ChoiceDialog(
            title = title,
            options = options,
            selected = selected,
            onSelect = {
                onSelect(it)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            options.forEach { (value, label) ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = FemtoDimens.MinTouchTarget)
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = value == selected, onClick = null)
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    },
    // Tapping a radio option commits and dismisses (select-on-tap), so there is
    // no confirm action — only Cancel.
    confirmButton = {},
    dismissButton = {
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.settings_cancel))
        }
    },
)

// A boolean row: the whole row is the toggle (role = Switch), so the inline
// Switch is presentation-only (onCheckedChange = null) and never double-fires. An
// optional [summary] explains what the toggle does (e.g. attribution / cost notes).
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) = SettingRow(
    title = title,
    modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    summary = summary,
) {
    Switch(checked = checked, onCheckedChange = null)
}

// A numeric row: an inline slider under the title / current-value line, with an
// optional [description] caption beneath that explains what the value trades off.
@Composable
internal fun SliderRow(
    title: String,
    valueLabel: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) = Column(
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            // A long localized title shares the row with the value via SpaceBetween;
            // weight(fill = false) keeps short titles in place but caps a long one so
            // it ellipsizes instead of wrapping and shoving the value off the row.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (description != null) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Slider(
        value = value.coerceIn(range.first, range.last).toFloat(),
        onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
    )
}

// A navigation row: links out to a system screen, marked with an external glyph.
@Composable
internal fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryLiveRegion: Boolean = false,
) = SettingRow(
    title = title,
    summary = summary,
    summaryLiveRegion = summaryLiveRegion,
    modifier = modifier.clickable(onClick = onClick),
) {
    TrailingIcon(Lucide.ExternalLink)
}

// A destructive row: resetting a group of settings to their defaults. Tapping
// opens a confirm dialog — the only destructive action in Settings — so a
// stray tap on the head unit never wipes the user's configuration. The three
// text parameters default to the global "reset every setting" copy (this
// row's original, single-purpose shape); a caller resetting a narrower group
// (e.g. the dock) supplies its own row title and confirm-dialog copy.
@Composable
internal fun ResetRow(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.settings_reset_to_defaults),
    confirmTitle: String = stringResource(R.string.settings_reset_confirm_title),
    confirmMessage: String = stringResource(R.string.settings_reset_confirm_message),
    // Reset-shaped by default; a delete-shaped caller (track history) passes
    // the trash glyph so the row reads as removal, not restoration.
    icon: ImageVector = Lucide.RotateCcw,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    SettingRow(
        title = title,
        modifier = modifier.clickable { dialogOpen = true },
    ) {
        TrailingIcon(icon)
    }
    if (dialogOpen) {
        ResetConfirmDialog(
            title = confirmTitle,
            message = confirmMessage,
            onConfirm = {
                onConfirm()
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

// Shared confirm dialog for both the global reset (ResetRow) and the
// per-category reset (SettingsCategoryDetail's SectionResetButton) — title and
// message vary by caller, the Reset / Cancel actions do not.
@Composable
private fun ResetConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = { Text(text = message) },
    confirmButton = {
        TextButton(onClick = onConfirm) {
            Text(text = stringResource(R.string.settings_reset_confirm))
        }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.settings_cancel))
        }
    },
)

// Shared row scaffold: a tap target ≥ MinTouchTarget with a title, optional
// summary, and a trailing slot. The caller supplies the interaction (clickable /
// toggleable) through [modifier] so each row keeps the right accessibility role.
@Composable
internal fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    // Announce summary changes to accessibility services (for a status line that
    // updates in place, e.g. the export progress/outcome).
    summaryLiveRegion: Boolean = false,
    trailing: @Composable () -> Unit = {},
) = Row(
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    if (summaryLiveRegion) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier
                    },
            )
        }
    }
    trailing()
}

@Composable
internal fun TrailingIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
) = FemtoIcon(
    imageVector = imageVector,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.size(FemtoDimens.InlineIconSize),
)

// A font-slot row: the current family (or "System default") under the title,
// opening the full Google Fonts picker on tap.
@Composable
internal fun FontRow(
    title: String,
    family: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingRow(
    title = title,
    modifier = modifier.clickable(onClick = onClick),
    summary = family ?: stringResource(R.string.settings_font_system),
) {
    TrailingIcon(Lucide.ChevronRight)
}

// Small modifier helper: clip to a circle and make clickable, for the back box.
private fun Modifier.clipClickable(onClick: () -> Unit): Modifier =
    this
        .clip(RoundedCornerShape(percent = 50))
        .clickable(onClick = onClick)
