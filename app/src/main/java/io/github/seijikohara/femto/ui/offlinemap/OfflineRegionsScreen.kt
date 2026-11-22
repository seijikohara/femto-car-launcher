package io.github.seijikohara.femto.ui.offlinemap

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.map.offline.OfflineStorageLimit
import io.github.seijikohara.femto.data.map.offline.RegionDownload
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase
import io.github.seijikohara.femto.data.map.offline.SavedRegion
import io.github.seijikohara.femto.ui.common.SheetCard
import io.github.seijikohara.femto.ui.common.fileSize
import io.github.seijikohara.femto.ui.offlinemap.components.OfflineRegionsDialogHost
import io.github.seijikohara.femto.ui.offlinemap.components.RegionDownloadRow
import io.github.seijikohara.femto.ui.offlinemap.components.RegionPicker
import io.github.seijikohara.femto.ui.offlinemap.components.limitLabel
import io.github.seijikohara.femto.ui.offlinemap.components.storageLimitLabel
import io.github.seijikohara.femto.ui.settings.components.SettingRow
import io.github.seijikohara.femto.ui.settings.components.SettingRowHorizontalInset
import io.github.seijikohara.femto.ui.settings.components.SettingRowVerticalInset
import io.github.seijikohara.femto.ui.settings.components.TrailingIcon
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import java.util.Date

/**
 * The Saved map regions sheet: the list of saved regions, with the storage figures, the storage limit and the one
 * download, or the region picker in its place while the user adds a region. Pure UI — every tap flows up through
 * [onAction], and the dialogs [OfflineRegionsUiState.dialog] names render over either view.
 */
@Composable
internal fun OfflineRegionsScreen(
    uiState: OfflineRegionsUiState,
    onAction: (OfflineRegionsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(FemtoDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val picker = uiState.picker
        if (picker == null) {
            // As tall as the picker's back arrow, so the title does not jump between the two views.
            Box(
                modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.settings_map_offline_regions),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            SheetCard(modifier = Modifier.weight(1f)) {
                RegionList(uiState = uiState, onAction = onAction)
            }
        } else {
            RegionPicker(picker = picker, onAction = onAction, modifier = Modifier.weight(1f))
        }
    }
    uiState.dialog?.let { OfflineRegionsDialogHost(dialog = it, limit = uiState.limit, onAction = onAction) }
}

// A handful of rows at most, so a plain scrolling column rather than a lazy list.
@Composable
private fun RegionList(
    uiState: OfflineRegionsUiState,
    onAction: (OfflineRegionsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    // Read from the directory, like the saved regions: before that the figures would say zero.
    if (uiState.loaded) {
        Text(
            text = storageLine(uiState.usedBytes, uiState.freeBytes, uiState.limit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SettingRowHorizontalInset, vertical = SettingRowVerticalInset),
        )
    }
    // The value trails the row, as a switch does, so the summary can say what the limit does.
    SettingRow(
        title = stringResource(R.string.offline_regions_limit),
        summary = stringResource(R.string.offline_regions_limit_summary),
        modifier = Modifier.clickable { onAction(OfflineRegionsAction.OpenStorageLimit) },
    ) {
        Text(
            text = storageLimitLabel(uiState.limit),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TrailingIcon(Lucide.ChevronRight)
    }
    uiState.download?.let { RegionDownloadRow(download = it, onAction = onAction) }
    uiState.saved.forEach { region ->
        SavedRegionRow(region = region, onDelete = { onAction(OfflineRegionsAction.DeleteRegion(region.id)) })
    }
    // Only once the repository has read its directory, so an empty list never flashes before the saved regions.
    if (uiState.loaded && uiState.saved.isEmpty()) {
        SettingRow(title = stringResource(R.string.offline_regions_empty))
    }
    AddRegionRow(
        enabled = uiState.loaded && uiState.download == null,
        busy = uiState.download != null,
        onClick = { onAction(OfflineRegionsAction.AddRegion) },
    )
}

@Composable
private fun storageLine(
    usedBytes: Long,
    freeBytes: Long,
    limit: OfflineStorageLimit,
): String {
    val used = fileSize(usedBytes)
    val free = fileSize(freeBytes)
    return limit.bytes
        ?.let { stringResource(R.string.offline_regions_storage_limited, used, limitLabel(it), free) }
        ?: stringResource(R.string.offline_regions_storage, used, free)
}

@Composable
private fun SavedRegionRow(
    region: SavedRegion,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingRow(
    title = region.name,
    summary = savedRegionSummary(region),
    modifier = modifier,
) {
    Box(
        modifier =
            Modifier
                .size(FemtoDimens.MinTouchTarget)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
        contentAlignment = Alignment.Center,
    ) {
        FemtoIcon(
            imageVector = Lucide.Trash2,
            contentDescription = stringResource(R.string.offline_regions_delete_region, region.name),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FemtoDimens.InlineIconSize),
        )
    }
}

// A record without a Last-Modified has no data date to name, so its size stands alone.
@Composable
private fun savedRegionSummary(region: SavedRegion): String {
    val size = fileSize(region.sizeBytes)
    return region.dataDateEpochMs
        ?.let { stringResource(R.string.offline_regions_saved_summary, size, mediumDate(it)) }
        ?: size
}

@Composable
private fun mediumDate(epochMs: Long): String {
    val context = LocalContext.current
    return remember(context, epochMs) { DateFormat.getMediumDateFormat(context).format(Date(epochMs)) }
}

// One download at a time: while one exists, the row stays in place, takes no tap, and says why. Before the load it
// takes no tap either: a paused download the load restores would answer the offer as busy, and the sheet would
// close that offer without a word.
@Composable
private fun AddRegionRow(
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingRow(
    title = stringResource(R.string.offline_regions_add),
    summary = if (busy) stringResource(R.string.offline_regions_add_busy) else null,
    modifier = modifier.clickable(enabled = enabled, onClick = onClick),
) {
    TrailingIcon(Lucide.Plus)
}

@PreviewLightDark
@Preview(name = "Saved regions", widthDp = 853, heightDp = 512)
@Composable
private fun OfflineRegionsListPreview() {
    FemtoTheme {
        OfflineRegionsScreen(
            uiState =
                OfflineRegionsUiState(
                    loaded = true,
                    saved =
                        listOf(
                            SavedRegion("berlin", "Berlin", 49_217_536L, PREVIEW_DATE_EPOCH_MS, PREVIEW_DATE_EPOCH_MS),
                            SavedRegion("monaco", "Monaco", 1_728_512L, PREVIEW_DATE_EPOCH_MS, PREVIEW_DATE_EPOCH_MS),
                        ),
                    download =
                        RegionDownload(
                            regionId = "bayern",
                            name = "Bayern",
                            receivedBytes = 412_000_000L,
                            totalBytes = 1_043_341_312L,
                            phase = RegionDownloadPhase.RUNNING,
                        ),
                    usedBytes = 462_946_048L,
                    freeBytes = 21_400_000_000L,
                    limit = OfflineStorageLimit.GB_5,
                ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun OfflineRegionsEmptyPreview() {
    FemtoTheme {
        OfflineRegionsScreen(
            uiState = OfflineRegionsUiState(loaded = true, freeBytes = 21_400_000_000L),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun OfflineRegionsPickerPreview() {
    FemtoTheme {
        OfflineRegionsScreen(
            uiState =
                OfflineRegionsUiState(
                    loaded = true,
                    picker =
                        RegionPickerUiState.Level(
                            parentName = null,
                            suggestion =
                                RegionRow("bayern", "Bayern", 1_043_341_312L, hasChildren = false, saved = false),
                            rows =
                                listOf(
                                    RegionRow("asia", "Asia", null, hasChildren = true, saved = false),
                                    RegionRow("europe", "Europe", null, hasChildren = true, saved = false),
                                    RegionRow(
                                        "north-america",
                                        "North America",
                                        null,
                                        hasChildren = true,
                                        saved = false,
                                    ),
                                ),
                            pendingId = null,
                        ),
                ),
            onAction = {},
        )
    }
}

private const val PREVIEW_DATE_EPOCH_MS = 1_763_400_000_000L
