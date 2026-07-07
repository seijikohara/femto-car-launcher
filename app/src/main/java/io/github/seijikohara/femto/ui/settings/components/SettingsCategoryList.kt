package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * The master-detail layout's category navigator: the rail in the wide layout,
 * the list in the narrow list-detail layout — one composable either way, only
 * its width / placement differs at the call site (see `SettingsScreen`). Every
 * [SettingsCategoryId] gets one row, in enum declaration order; the row
 * matching [selectedId] is highlighted so the wide rail always shows which
 * category the detail pane is showing.
 */
@Composable
internal fun SettingsCategoryList(
    selectedId: SettingsCategoryId,
    onSelect: (SettingsCategoryId) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
) {
    SettingsCategoryId.entries.forEach { id ->
        SettingsCategoryListItem(
            title = stringResource(id.titleRes),
            selected = id == selectedId,
            onClick = { onSelect(id) },
        )
    }
}

// One rail / list row: a >= MinTouchTarget tap target, highlighted with the
// secondary-container role color when selected. The click target's own
// contentDescription ("Select X") is distinct from the title Text's plain
// string so the two never collide in the wide layout, where the rail's row
// and the detail pane's own header both show the same category title at once.
@Composable
private fun SettingsCategoryListItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val selectDescription = stringResource(R.string.settings_category_select, title)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .background(containerColor, MaterialTheme.shapes.large)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .semantics { contentDescription = selectDescription }
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
