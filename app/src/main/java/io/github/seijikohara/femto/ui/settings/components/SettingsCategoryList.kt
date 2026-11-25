package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Ruler
import com.composables.icons.lucide.Settings
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.common.UpdateBadge
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon

/**
 * The wide master-detail layout's category navigator: an expanded Material 3
 * Expressive navigation rail ([WideNavigationRail]) with one icon-and-label
 * item per entry of [categories] (visibleSettingsCategories), in that order.
 * The item matching [selectedId] carries the active indicator, so the rail
 * always shows which category the detail pane is showing. The items of
 * [badged] categories carry the update dot ([UpdateBadge]): today only
 * Updates, while an update is on offer.
 *
 * The rail has no container of its own (it sits on the settings sheet) and no
 * window insets (the sheet already handles them). Its items are floored at
 * [FemtoDimens.MinTouchTarget], and scroll as one column: eight such items (nine
 * with Navigation) do not fit the height a head unit leaves under the settings
 * header, and the rail itself lays its items out without scrolling.
 */
@Composable
internal fun SettingsCategoryRail(
    categories: List<SettingsCategoryId>,
    selectedId: SettingsCategoryId,
    onSelect: (SettingsCategoryId) -> Unit,
    modifier: Modifier = Modifier,
    badged: Set<SettingsCategoryId> = emptySet(),
) = WideNavigationRail(
    modifier = modifier,
    state = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded),
    colors = WideNavigationRailDefaults.colors(containerColor = Color.Transparent),
    windowInsets = WindowInsets(0),
    contentPadding = PaddingValues(0.dp),
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides FemtoDimens.MinTouchTarget) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            categories.forEach { id ->
                val title = stringResource(id.titleRes)
                val selectDescription = stringResource(R.string.settings_category_select, title)
                WideNavigationRailItem(
                    selected = id == selectedId,
                    onClick = { onSelect(id) },
                    icon = { FemtoIcon(imageVector = id.icon, contentDescription = null) },
                    label = { CategoryLabel(title = title, badged = id in badged) },
                    railExpanded = true,
                    // See SettingsCategoryListItem on the "Select X" description.
                    modifier = Modifier.semantics { contentDescription = selectDescription },
                )
            }
        }
    }
}

/**
 * The narrow list-detail layout's category navigator: one segmented group with
 * a row per entry of [categories] (visibleSettingsCategories), in that order.
 * The row matching [selectedId] is highlighted; the rows of [badged]
 * categories carry the update dot ([UpdateBadge]), as in
 * [SettingsCategoryRail].
 */
@Composable
internal fun SettingsCategoryList(
    categories: List<SettingsCategoryId>,
    selectedId: SettingsCategoryId,
    onSelect: (SettingsCategoryId) -> Unit,
    modifier: Modifier = Modifier,
    badged: Set<SettingsCategoryId> = emptySet(),
) = Column(modifier = modifier.verticalScroll(rememberScrollState())) {
    SettingsGroup {
        categories.forEach { id ->
            item(key = id) { shapes ->
                SettingsCategoryListItem(
                    title = stringResource(id.titleRes),
                    selected = id == selectedId,
                    onClick = { onSelect(id) },
                    shapes = shapes,
                    badged = id in badged,
                )
            }
        }
    }
}

// One list row: a >= MinTouchTarget segment, highlighted with the selected
// list-item colours when selected. The click target's own contentDescription
// ("Select X") is distinct from the title Text's plain string so the two never
// collide where the navigator and the detail pane's own header both show the
// same category title at once; the rail items carry the same description. The
// outer semantics keep the tab role the list has always had, over the radio
// role of the list item's selection overload. A [badged] row's dot follows the
// title, and its "Update available" merges into the row's description after
// "Select X".
@Composable
private fun SettingsCategoryListItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    badged: Boolean = false,
) {
    val selectDescription = stringResource(R.string.settings_category_select, title)
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        colors = settingsSegmentColors(),
        modifier =
            modifier
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .semantics {
                    role = Role.Tab
                    contentDescription = selectDescription
                },
    ) {
        CategoryLabel(title = title, badged = badged)
    }
}

// A category's title, followed by the update dot when [badged].
@Composable
private fun CategoryLabel(
    title: String,
    badged: Boolean,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Shrinks a long title rather than pushing the dot off the row.
        modifier = Modifier.weight(1f, fill = false),
    )
    if (badged) UpdateBadge()
}

// The rail icon of each category.
private val SettingsCategoryId.icon: ImageVector
    get() =
        when (this) {
            SettingsCategoryId.APPEARANCE -> Lucide.Palette

            SettingsCategoryId.SCREEN -> Lucide.Monitor

            SettingsCategoryId.UNITS -> Lucide.Ruler

            SettingsCategoryId.MAP -> Lucide.Map

            // The icon of the destination panel's Show route button.
            SettingsCategoryId.NAVIGATION -> Lucide.Route

            SettingsCategoryId.LOCATION -> Lucide.MapPin

            SettingsCategoryId.PANELS -> Lucide.LayoutDashboard

            SettingsCategoryId.UPDATES -> Lucide.Download

            SettingsCategoryId.SYSTEM -> Lucide.Settings
        }
