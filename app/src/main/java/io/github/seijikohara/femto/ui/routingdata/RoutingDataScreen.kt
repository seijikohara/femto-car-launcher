package io.github.seijikohara.femto.ui.routingdata

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.routing.InstalledRoutingTile
import io.github.seijikohara.femto.ui.common.MessageDialog
import io.github.seijikohara.femto.ui.common.SheetCard
import io.github.seijikohara.femto.ui.common.fileSize
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
 * The Routing data sheet (#389 phase 3): the routing tiles on the device, one row per 5° area with its size and the
 * date of its last check against brouter.de, each with a delete, and a delete of everything below them. Every delete
 * asks first. Laid out like the Saved map regions sheet, in the same card. Pure UI — every tap flows up through
 * [onAction].
 */
@Composable
internal fun RoutingDataScreen(
    uiState: RoutingDataUiState,
    onAction: (RoutingDataAction) -> Unit,
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
        // As tall as the Saved map regions sheet's title row, so the two sheets line up.
        Box(
            modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.settings_map_routing_data),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        SheetCard(modifier = Modifier.weight(1f)) {
            TileList(uiState = uiState, onAction = onAction)
        }
    }
    uiState.dialog?.let { RoutingDataDialogHost(dialog = it, onAction = onAction) }
}

// A few dozen rows at most (a long trip across a continent), so a plain scrolling column rather than a lazy list.
@Composable
private fun TileList(
    uiState: RoutingDataUiState,
    onAction: (RoutingDataAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Text(
        text = stringResource(R.string.routing_data_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SettingRowHorizontalInset, vertical = SettingRowVerticalInset),
    )
    uiState.tiles.forEach { tile ->
        TileRow(tile = tile, onDelete = { onAction(RoutingDataAction.DeleteTile(tile.name)) })
    }
    // Only once the store has read its directory, so an empty list never flashes before the tiles.
    if (uiState.loaded && uiState.tiles.isEmpty()) {
        SettingRow(title = stringResource(R.string.routing_data_empty))
    }
    if (uiState.tiles.isNotEmpty()) {
        SettingRow(
            title = stringResource(R.string.routing_data_delete_all),
            summary = fileSize(uiState.totalBytes),
            modifier = Modifier.clickable { onAction(RoutingDataAction.DeleteAll) },
        ) {
            TrailingIcon(Lucide.Trash2)
        }
    }
}

@Composable
private fun TileRow(
    tile: InstalledRoutingTile,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingRow(
    title = tile.name,
    summary = tileSummary(tile),
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
            contentDescription = stringResource(R.string.routing_data_delete_tile, tile.name),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FemtoDimens.InlineIconSize),
        )
    }
}

// A tile installed without a record has never been checked, so its size stands alone.
@Composable
private fun tileSummary(tile: InstalledRoutingTile): String {
    val size = fileSize(tile.sizeBytes)
    return tile.checkedAtEpochMs
        ?.let { stringResource(R.string.routing_data_tile_summary, size, mediumDate(it)) }
        ?: size
}

@Composable
private fun mediumDate(epochMs: Long): String {
    val context = LocalContext.current
    return remember(context, epochMs) { DateFormat.getMediumDateFormat(context).format(Date(epochMs)) }
}

// Every way out of a dialog goes through [onAction], so the ViewModel, which opened it, closes it.
@Composable
private fun RoutingDataDialogHost(
    dialog: RoutingDataDialog,
    onAction: (RoutingDataAction) -> Unit,
) {
    val close = { onAction(RoutingDataAction.CloseDialog) }
    when (dialog) {
        is RoutingDataDialog.ConfirmDelete -> {
            MessageDialog(
                title = stringResource(R.string.routing_data_delete_title, dialog.tile.name),
                text = stringResource(R.string.routing_data_delete_message, fileSize(dialog.tile.sizeBytes)),
                confirmLabel = stringResource(R.string.routing_data_delete),
                onConfirm = { onAction(RoutingDataAction.ConfirmDelete) },
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = close,
            )
        }

        is RoutingDataDialog.ConfirmDeleteAll -> {
            MessageDialog(
                title = stringResource(R.string.routing_data_delete_all_title),
                text = stringResource(R.string.routing_data_delete_all_message, fileSize(dialog.totalBytes)),
                confirmLabel = stringResource(R.string.routing_data_delete),
                onConfirm = { onAction(RoutingDataAction.ConfirmDeleteAll) },
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = close,
            )
        }
    }
}

@PreviewLightDark
@Preview(name = "Routing data", widthDp = 853, heightDp = 512)
@Composable
private fun RoutingDataListPreview() {
    FemtoTheme {
        RoutingDataScreen(
            uiState =
                RoutingDataUiState(
                    loaded = true,
                    tiles =
                        listOf(
                            InstalledRoutingTile("E135_N30", 48_211_968L, PREVIEW_DATE_EPOCH_MS, PREVIEW_DATE_EPOCH_MS),
                            InstalledRoutingTile("E135_N35", 96_403_456L, PREVIEW_DATE_EPOCH_MS, PREVIEW_DATE_EPOCH_MS),
                        ),
                ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun RoutingDataEmptyPreview() {
    FemtoTheme {
        RoutingDataScreen(uiState = RoutingDataUiState(loaded = true), onAction = {})
    }
}

private const val PREVIEW_DATE_EPOCH_MS = 1_763_812_800_000L
