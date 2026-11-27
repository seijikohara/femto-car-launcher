package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FitText
import kotlin.math.roundToInt

// Shared back-arrow icon size: every BackButton (the settings, diagnostics,
// licenses and font-picker headers, SettingsCategoryDetail's narrow-mode back
// arrow, the saved-regions picker) uses it, so the back affordances read as
// the same control at a glance.
private val BackIconSize = 28.dp

/**
 * Render the shared screen-header back arrow: a [FemtoDimens.MinTouchTarget]
 * round icon button that morphs to the Material icon button's default pressed
 * shape on press. [tint] defaults to `onBackground` for headers drawn on the
 * screen background; pass `onSurfaceVariant` inside a surface.
 */
@Composable
internal fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = stringResource(R.string.settings_back),
    tint: Color = MaterialTheme.colorScheme.onBackground,
) = IconButton(
    onClick = onClick,
    shapes = IconButtonDefaults.shapes(),
    modifier = modifier.size(FemtoDimens.MinTouchTarget),
) {
    FemtoIcon(
        imageVector = Lucide.ArrowLeft,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(BackIconSize),
    )
}

// The flat SettingRow's inset from its container's edge. Internal, so that
// content a screen draws beside the flat rows (the saved-regions storage line)
// lines up with the rows' text.
internal val SettingRowHorizontalInset = 20.dp
internal val SettingRowVerticalInset = 12.dp

// The padding inside every settings segment, list item or custom: the list
// item's own default, so a custom segment (a slider, a connected choice) lines
// its text up with the list-item segments around it.
private val SegmentContentPadding: PaddingValues = ListItemDefaults.ContentPadding

// The gap between the lines a custom segment stacks (title, value, control).
private val SegmentContentSpacing = 8.dp

// The most options a single-choice row shows inline as connected toggle
// buttons; a longer list opens a radio dialog instead, since more than three
// buttons no longer fit a phone-width row at the automotive tap-target size.
private const val MAX_CONNECTED_CHOICES = 3

@Composable
internal fun Header(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    BackButton(onClick = onBack)
    Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

// The master-detail layout's right pane (wide) / detail view (narrow list-detail):
// a header row (an optional back arrow for the narrow flow's return-to-list,
// the category title, and an optional reset affordance) over the category's
// segmented groups, scrolling independently of the rail / list. Category
// SELECTION (the rail in wide, the list in narrow — see SettingsCategoryList)
// is what used to be the collapsible per-section toggle; this pane always shows
// its content, since only one category is ever mounted at a time.
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
                BackButton(
                    onClick = onBack,
                    description = stringResource(R.string.settings_category_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        // No card behind the rows any more: each group's segments carry their
        // own container, so the pane is just the scroll.
        Box(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            content()
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
// affordance in SettingsCategoryDetail's header row; round with the same press
// morph as BackButton.
@Composable
private fun SectionResetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = IconButton(
    onClick = onClick,
    shapes = IconButtonDefaults.shapes(),
    modifier = modifier.size(FemtoDimens.MinTouchTarget),
) {
    FemtoIcon(
        imageVector = Lucide.RotateCcw,
        contentDescription = stringResource(R.string.settings_reset_section),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
}

// A sub-group label, separating related rows under a section heading: muted
// (onSurfaceVariant vs the section's primary) so it reads as a child cluster
// rather than a new section. [inset] aligns it with the text of the rows below
// it — the flat SettingRow's by default, the segment's inside a SettingsGroup.
@Composable
internal fun SettingsSubheader(
    title: String,
    modifier: Modifier = Modifier,
    inset: Dp = SettingRowHorizontalInset,
) = Text(
    text = title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(start = inset, top = 14.dp, bottom = 2.dp),
)

/**
 * Where a segment sits in its group: the [index]-th of [count] rows that are
 * showing. It picks the segment's shape — the group's outer corners on the
 * first and last segment, the inner corners between them.
 */
@Immutable
internal data class SegmentPosition(
    val index: Int,
    val count: Int,
)

/**
 * Return the [SegmentPosition] of each row in a group whose rows show or hide
 * per [visible]; a hidden row gets `null` and leaves no gap in the numbering,
 * so the rows that show always form one correctly shaped group.
 */
internal fun segmentPositions(visible: List<Boolean>): List<SegmentPosition?> =
    visible.count { it }.let { count ->
        visible
            .scan(0) { shownBefore, shown -> if (shown) shownBefore + 1 else shownBefore }
            .zip(visible) { shownBefore, shown -> if (shown) SegmentPosition(shownBefore, count) else null }
    }

/**
 * The rows of one [SettingsGroup], registered in display order. Each row's
 * content receives the [ListItemShapes] for its place among the rows that are
 * showing, so no call site numbers its rows by hand.
 */
internal class SettingsGroupScope {
    internal val rows = mutableListOf<SettingsGroupRow>()

    /**
     * Register a row that always shows. A row registered only under a condition
     * passes a [key], so the rows after it keep their state when it comes and goes.
     */
    fun item(
        key: Any? = null,
        content: @Composable (ListItemShapes) -> Unit,
    ) {
        rows += SettingsGroupRow(key ?: rows.size, visible = true, animated = false, content = content)
    }

    /** Register a row that expands in while [visible] and collapses out otherwise. */
    fun animatedItem(
        visible: Boolean,
        key: Any? = null,
        content: @Composable (ListItemShapes) -> Unit,
    ) {
        rows += SettingsGroupRow(key ?: rows.size, visible = visible, animated = true, content = content)
    }
}

internal class SettingsGroupRow(
    val key: Any,
    val visible: Boolean,
    val animated: Boolean,
    val content: @Composable (ListItemShapes) -> Unit,
)

/**
 * Render one segmented group of settings rows (the Material 3 Expressive
 * segmented list): the rows that are showing stack with the segmented gap
 * between them, the first and last carrying the group's rounded outer corners.
 * An optional [header] (a sub-group label) sits above the group; a new header
 * always starts a new group.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    rows: SettingsGroupScope.() -> Unit,
) {
    val group = SettingsGroupScope().apply(rows)
    val positions = segmentPositions(group.rows.map { it.visible })
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            SettingsSubheader(
                title = header,
                inset = SegmentContentPadding.calculateStartPadding(LocalLayoutDirection.current),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            group.rows.zip(positions).forEach { (row, position) ->
                key(row.key) {
                    if (row.animated) {
                        AnimatedSegment(visible = row.visible, position = position, content = row.content)
                    } else if (position != null) {
                        row.content(position.shapes())
                    }
                }
            }
        }
    }
}

// A footnote on a whole group, set under it and aligned with its rows' text.
@Composable
internal fun SettingsGroupNote(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
        modifier.padding(
            horizontal = SegmentContentPadding.calculateStartPadding(LocalLayoutDirection.current),
            vertical = SegmentContentSpacing,
        ),
)

@Composable
private fun SegmentPosition.shapes(): ListItemShapes = ListItemDefaults.segmentedShapes(index = index, count = count)

// A row animating out is no longer among the showing rows, so it has no
// position; it keeps the shape it last showed until it has collapsed.
@Composable
private fun ColumnScope.AnimatedSegment(
    visible: Boolean,
    position: SegmentPosition?,
    content: @Composable (ListItemShapes) -> Unit,
) {
    val last = remember { LastSegmentPosition() }
    position?.let { last.value = it }
    AnimatedVisibility(visible = visible) { content(last.value.shapes()) }
}

private class LastSegmentPosition(
    var value: SegmentPosition = SegmentPosition(index = 0, count = 1),
)

/**
 * Render a settings row as a segmented list item ([SegmentedListItem]): a
 * title, an optional [summary], and a [trailing] slot, at least
 * [FemtoDimens.MinTouchTarget] tall. [onClick] makes the whole segment the tap
 * target, with the Expressive press-shape morph; a row without one only
 * reports. A caller that needs a role beyond a plain click (a switch) adds it
 * through [modifier]. With [enabled] false the segment takes no click and reads
 * as unavailable; a row without [onClick] is unaffected.
 */
@Composable
internal fun SettingsListItem(
    title: String,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    summary: String? = null,
    // Announce summary changes to accessibility services (for a status line that
    // updates in place, e.g. the export progress/outcome).
    summaryLiveRegion: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val itemModifier = modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget)
    val headline: @Composable () -> Unit = {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
    val supporting: (@Composable () -> Unit)? =
        summary?.let {
            {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        if (summaryLiveRegion) {
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        } else {
                            Modifier
                        },
                )
            }
        }
    val colors = settingsSegmentColors()
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = itemModifier,
            enabled = enabled,
            supportingContent = supporting,
            trailingContent = trailing,
            colors = colors,
            content = headline,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = itemModifier,
            supportingContent = supporting,
            trailingContent = trailing,
            colors = colors,
            content = headline,
        )
    }
}

/**
 * Return the colours of every settings segment. Segments sit on
 * surfaceContainer, the role design-system.md gives cards (the settings card
 * before the segmented rows used it too); the Material default is surface,
 * which on the sheet's surfaceContainerLow reads as a recessed group in the
 * dark theme.
 */
@Composable
internal fun settingsSegmentColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

/**
 * Render a segment with custom [content] for a row a list item's slots do not
 * fit (a slider under its title, a row of connected buttons): the same
 * container colour, shape, padding and minimum height as a [SettingsListItem]
 * beside it, without a list item's merged semantics, so each control inside
 * stays its own accessibility node. The segment is one traversal group, so a
 * screen reader finishes a segment's title and controls before the next row.
 */
@Composable
internal fun SettingsSegment(
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier =
        modifier
            .semantics { isTraversalGroup = true }
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .background(color = settingsSegmentColors().containerColor, shape = shapes.shape)
            .padding(SegmentContentPadding),
    verticalArrangement = Arrangement.spacedBy(SegmentContentSpacing),
    content = content,
)

// The title (and optional summary) at the top of a custom segment, styled as a
// list item's headline and supporting text.
@Composable
internal fun SegmentTitle(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
) = Column(modifier = modifier) {
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
        )
    }
}

// A single-choice row. Up to MAX_CONNECTED_CHOICES options show inline as
// connected toggle buttons (ConnectedChoiceRow); a longer list shows the
// current value as the row's summary and opens a radio dialog on tap (the
// Android ListPreference pattern). The option count alone decides, so every
// short choice in Settings looks and behaves the same.
@Composable
internal fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    if (options.size <= MAX_CONNECTED_CHOICES) {
        ConnectedChoiceRow(
            title = title,
            options = options,
            selected = selected,
            onSelect = onSelect,
            shapes = shapes,
            modifier = modifier,
        )
    } else {
        var dialogOpen by remember { mutableStateOf(false) }
        SettingsListItem(
            title = title,
            shapes = shapes,
            modifier = modifier,
            summary = options.firstOrNull { it.first == selected }?.second,
            onClick = { dialogOpen = true },
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
}

/**
 * Render a short single choice inline: the [title] (and optional [summary])
 * over a row of connected toggle buttons, one per option, the selected one
 * checked. Each button is a radio option to accessibility services, at least
 * [FemtoDimens.MinTouchTarget] tall; a tap on the selected option changes
 * nothing. A label too long for its button wraps to a second line and shrinks
 * toward the body floor rather than clipping.
 */
@Composable
internal fun <T> ConnectedChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    summary: String? = null,
) = SettingsSegment(shapes = shapes, modifier = modifier) {
    SegmentTitle(title = title, summary = summary)
    Row(
        // Equal heights, so a label that wraps grows every button in the row.
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            val checked = value == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { nowChecked -> if (nowChecked) onSelect(value) },
                shapes = connectedButtonShapes(index = index, count = options.size),
                // The outer semantics replace the toggle button's checkbox role:
                // the buttons are one exclusive choice, not independent toggles.
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = FemtoDimens.MinTouchTarget)
                        .fillMaxHeight()
                        .semantics {
                            role = Role.RadioButton
                            this.selected = checked
                        },
            ) {
                FitText(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun connectedButtonShapes(
    index: Int,
    count: Int,
): ToggleButtonShapes =
    when (index) {
        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }

// Internal so a screen whose ViewModel decides when the choice opens (the
// saved-regions storage limit) shows the same dialog as ChoiceRow.
@Composable
internal fun <T> ChoiceDialog(
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
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget)) {
            Text(text = stringResource(R.string.settings_cancel))
        }
    },
)

// A boolean row: the whole segment is the toggle, so the inline Switch is
// presentation-only (onCheckedChange = null) and never double-fires. The
// segment's click carries the switch role and on / off state itself, since the
// list item's own checked overload would announce a checkbox. An optional
// [summary] explains what the toggle does (e.g. attribution / cost notes). With
// [enabled] false the row takes no tap and the switch reads as unavailable.
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) = SettingsListItem(
    title = title,
    shapes = shapes,
    modifier =
        modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
    summary = summary,
    onClick = { onCheckedChange(!checked) },
    enabled = enabled,
) {
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
}

// A numeric row: an inline slider under the title / current-value line, with an
// optional [description] caption that explains what the value trades off. A
// custom segment, not a list item: the slider needs the full width and its own
// accessibility node.
@Composable
internal fun SliderRow(
    title: String,
    valueLabel: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    description: String? = null,
) = SettingsSegment(shapes = shapes, modifier = modifier) {
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
    // The stored setting is the source of truth, so the slider's state mirrors
    // [value] on every recomposition instead of owning it: the thumb then shows
    // the rounded, clamped setting, exactly as Material 3's deprecated
    // value-based overload did internally.
    val sliderValue = value.coerceIn(range.first, range.last).toFloat()
    val sliderState =
        remember(range) {
            SliderState(value = sliderValue, trackRange = range.first.toFloat()..range.last.toFloat())
        }
    sliderState.value = sliderValue
    Slider(
        state = sliderState,
        onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
    )
}

// A navigation row: links out to a system screen, marked with an external glyph.
// A row whose action stays in the app (checking for or downloading an update)
// passes a glyph of its own, so the row never promises a jump it does not make.
@Composable
internal fun ActionRow(
    title: String,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryLiveRegion: Boolean = false,
    icon: ImageVector = Lucide.ExternalLink,
) = SettingsListItem(
    title = title,
    shapes = shapes,
    modifier = modifier,
    summary = summary,
    summaryLiveRegion = summaryLiveRegion,
    onClick = onClick,
) {
    TrailingIcon(icon)
}

// A destructive row: resetting or deleting one group of data (the dock, the
// track history). Tapping opens a confirm dialog with the caller's copy, so a
// stray tap on the head unit never wipes the user's configuration. The global
// "Reset to defaults" row is ResetSettingsRow, which asks what to reset.
@Composable
internal fun ResetRow(
    onConfirm: () -> Unit,
    shapes: ListItemShapes,
    title: String,
    confirmTitle: String,
    confirmMessage: String,
    modifier: Modifier = Modifier,
    // Reset-shaped by default; a delete-shaped caller (track history) passes
    // the trash glyph so the row reads as removal, not restoration.
    icon: ImageVector = Lucide.RotateCcw,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    SettingsListItem(
        title = title,
        shapes = shapes,
        modifier = modifier,
        onClick = { dialogOpen = true },
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

// Shared confirm dialog for ResetRow and the per-category reset
// (SettingsCategoryDetail's SectionResetButton) — title and message vary by
// caller, the Reset / Cancel actions do not.
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

// The flat row scaffold for the screens outside the settings groups (the saved
// regions): a tap target ≥ MinTouchTarget with a title, optional summary, and a
// trailing slot. The caller supplies the interaction (clickable / toggleable)
// through [modifier] so each row keeps the right accessibility role.
@Composable
internal fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    // Announce summary changes to accessibility services (for a status line that
    // updates in place, e.g. a download's progress/outcome).
    summaryLiveRegion: Boolean = false,
    trailing: @Composable () -> Unit = {},
) = Row(
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .padding(horizontal = SettingRowHorizontalInset, vertical = SettingRowVerticalInset),
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
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) = SettingsListItem(
    title = title,
    shapes = shapes,
    modifier = modifier,
    summary = family ?: stringResource(R.string.settings_font_system),
    onClick = onClick,
) {
    TrailingIcon(Lucide.ChevronRight)
}

// Whether a typed value is a URL the map WebView can actually load. A bare
// hostname would resolve against the page's own appassets origin and an http one
// is blocked by the WebView's mixed-content policy; both surface as a blank map
// long after the dialog is gone, so the dialogs refuse them up front.
internal fun isHttpsUrl(value: String): Boolean = HTTPS_URL_PATTERN.matches(value.trim())

private val HTTPS_URL_PATTERN = Regex("""^https://[^\s/]+(/\S*)?$""")

// The entry dialog for a single https URL setting (the tile host, the custom
// style URL): a hint, one field that flags anything but an https URL, Save
// enabled only for a valid one, and a clear action that restores the default.
// The field is not a password field on purpose — a URL is checked by reading
// it — so a key it carries is visible while the dialog is open; the row summary
// behind it is what stays masked.
@Composable
internal fun HttpsUrlDialog(
    title: String,
    hint: String,
    initialValue: String,
    saveLabel: String,
    clearLabel: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    isError = draft.isNotBlank() && !isHttpsUrl(draft),
                    label = { Text(title) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isHttpsUrl(draft),
                onClick = { onSave(draft) },
                modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
            ) { Text(saveLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onClear,
                modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
            ) { Text(clearLabel) }
        },
    )
}
