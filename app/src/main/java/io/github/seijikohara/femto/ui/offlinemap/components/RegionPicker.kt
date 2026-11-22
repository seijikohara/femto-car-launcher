package io.github.seijikohara.femto.ui.offlinemap.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.common.SheetCard
import io.github.seijikohara.femto.ui.common.fileSize
import io.github.seijikohara.femto.ui.offlinemap.OfflineRegionsAction
import io.github.seijikohara.femto.ui.offlinemap.RegionPickerUiState
import io.github.seijikohara.femto.ui.offlinemap.RegionRow
import io.github.seijikohara.femto.ui.settings.components.BackButton
import io.github.seijikohara.femto.ui.settings.components.SettingRow
import io.github.seijikohara.femto.ui.settings.components.SettingsSubheader
import io.github.seijikohara.femto.ui.settings.components.TrailingIcon
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon

private const val SUGGESTION_HEADER_KEY = "__suggestion_header__"

/** The chevron of a row that opens its sub-regions; decorative, so tests find it by this tag. */
internal const val REGION_PICKER_CHEVRON_TAG = "region-picker-chevron"

/**
 * The region tree, one level at a time, in place of the list. The back arrow goes up a level, and from the root
 * back to the list. A row with sub-regions opens them; a row with a file and none, or the `All of` row, asks what
 * saving it would take. A saved region's row does nothing, sub-regions or not, and while one offer is pending no row
 * takes a tap.
 */
@Composable
internal fun RegionPicker(
    picker: RegionPickerUiState,
    onAction: (OfflineRegionsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    PickerHeader(
        title = (picker as? RegionPickerUiState.Level)?.parentName ?: stringResource(R.string.offline_regions_add),
        onBack = { onAction(OfflineRegionsAction.Back) },
    )
    SheetCard(modifier = Modifier.weight(1f)) {
        when (picker) {
            RegionPickerUiState.Loading -> {
                Status(text = stringResource(R.string.offline_regions_picker_loading)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            RegionPickerUiState.Unavailable -> {
                Status(text = stringResource(R.string.offline_regions_picker_unavailable)) {
                    TextButton(
                        onClick = { onAction(OfflineRegionsAction.RetryCatalog) },
                        modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
                    ) { Text(text = stringResource(R.string.offline_regions_picker_retry)) }
                }
            }

            is RegionPickerUiState.Level -> {
                PickerLevel(level = picker, onAction = onAction)
            }
        }
    }
}

@Composable
private fun PickerHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    BackButton(onClick = onBack)
    // A long region name ends in an ellipsis inside the width the back arrow leaves, instead of being clipped.
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
}

// The suggestion can be a row of the same level too (a root region with a file), so each key names its role.
@Composable
private fun PickerLevel(
    level: RegionPickerUiState.Level,
    onAction: (OfflineRegionsAction) -> Unit,
    modifier: Modifier = Modifier,
) = LazyColumn(modifier = modifier.fillMaxSize()) {
    level.suggestion?.let { suggestion ->
        item(key = SUGGESTION_HEADER_KEY) {
            SettingsSubheader(stringResource(R.string.offline_regions_picker_suggestion))
        }
        item(key = "suggestion:${suggestion.id}") {
            PickerRow(row = suggestion, pendingId = level.pendingId, onAction = onAction)
        }
    }
    items(level.rows, key = { if (it.whole) "whole:${it.id}" else "row:${it.id}" }) { row ->
        PickerRow(row = row, pendingId = level.pendingId, onAction = onAction)
    }
}

@Composable
private fun PickerRow(
    row: RegionRow,
    pendingId: String?,
    onAction: (OfflineRegionsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val action =
        when {
            // A saved region's file already covers its sub-regions, so its row does nothing.
            pendingId != null || row.saved -> null

            row.hasChildren -> OfflineRegionsAction.OpenRegion(row.id)

            row.approxBytes != null -> OfflineRegionsAction.PickRegion(row.id)

            else -> null
        }
    SettingRow(
        title = if (row.whole) stringResource(R.string.offline_regions_picker_whole, row.name) else row.name,
        summary = rowSummary(row),
        modifier = if (action != null) modifier.clickable { onAction(action) } else modifier,
    ) {
        when {
            row.id == pendingId -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(FemtoDimens.InlineIconSize),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            row.hasChildren && !row.saved -> {
                TrailingIcon(Lucide.ChevronRight, modifier = Modifier.testTag(REGION_PICKER_CHEVRON_TAG))
            }
        }
    }
}

// A saved region says so in place of its size; a region without a file of its own says nothing.
@Composable
private fun rowSummary(row: RegionRow): String? =
    when {
        row.saved -> stringResource(R.string.offline_regions_picker_saved)
        row.approxBytes != null -> stringResource(R.string.offline_regions_picker_size, fileSize(row.approxBytes))
        else -> null
    }

@Composable
private fun Status(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Column(
    modifier = modifier.fillMaxWidth().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    content()
}
