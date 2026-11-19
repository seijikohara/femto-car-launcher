package io.github.seijikohara.femto.ui.offlinemap.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.map.offline.OfflineStorageLimit
import io.github.seijikohara.femto.data.map.offline.RegionOffer.Unavailable.Reason
import io.github.seijikohara.femto.ui.common.fileSize
import io.github.seijikohara.femto.ui.offlinemap.OfflineRegionsAction
import io.github.seijikohara.femto.ui.offlinemap.OfflineRegionsDialog
import io.github.seijikohara.femto.ui.settings.components.ChoiceDialog
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * The dialog the sheet's state names: the answer to an offer, the confirmations that come before a delete
 * or a cancel, and the storage-limit choice. Every way out of a dialog goes through [onAction], so the ViewModel,
 * which opened it, closes it.
 */
@Composable
internal fun OfflineRegionsDialogHost(
    dialog: OfflineRegionsDialog,
    limit: OfflineStorageLimit,
    onAction: (OfflineRegionsAction) -> Unit,
) {
    val close = { onAction(OfflineRegionsAction.CloseDialog) }
    when (dialog) {
        is OfflineRegionsDialog.ConfirmSave -> {
            MessageDialog(
                title = stringResource(R.string.offline_regions_save_title, dialog.name),
                text = saveMessage(dialog),
                confirmLabel = stringResource(R.string.offline_regions_save),
                onConfirm = { onAction(OfflineRegionsAction.ConfirmSave) },
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = close,
            )
        }

        is OfflineRegionsDialog.OverLimit -> {
            MessageDialog(
                title = stringResource(R.string.offline_regions_over_limit_title),
                text =
                    stringResource(
                        R.string.offline_regions_over_limit_message,
                        dialog.name,
                        fileSize(dialog.sizeBytes),
                        fileSize(dialog.overByBytes),
                        limitLabel(dialog.limitBytes),
                    ),
                confirmLabel = stringResource(R.string.offline_regions_change_limit),
                onConfirm = { onAction(OfflineRegionsAction.ChangeLimit) },
                dismissLabel = stringResource(R.string.settings_dialog_close),
                onDismiss = close,
            )
        }

        is OfflineRegionsDialog.NoSpace -> {
            MessageDialog(
                title = stringResource(R.string.offline_regions_no_space_title),
                text =
                    stringResource(
                        R.string.offline_regions_no_space_message,
                        dialog.name,
                        fileSize(dialog.sizeBytes),
                        fileSize(dialog.freeBytes),
                    ),
                confirmLabel = stringResource(R.string.settings_dialog_close),
                onConfirm = close,
                onDismiss = close,
            )
        }

        is OfflineRegionsDialog.OfferUnavailable -> {
            MessageDialog(
                title = dialog.name,
                text = unavailableText(dialog.reason),
                confirmLabel = stringResource(R.string.settings_dialog_close),
                onConfirm = close,
                onDismiss = close,
            )
        }

        is OfflineRegionsDialog.ConfirmDelete -> {
            MessageDialog(
                title = stringResource(R.string.offline_regions_delete_title, dialog.region.name),
                text = stringResource(R.string.offline_regions_delete_message, fileSize(dialog.region.sizeBytes)),
                confirmLabel = stringResource(R.string.offline_regions_delete),
                onConfirm = { onAction(OfflineRegionsAction.ConfirmDelete) },
                dismissLabel = stringResource(R.string.settings_cancel),
                onDismiss = close,
            )
        }

        is OfflineRegionsDialog.ConfirmCancel -> {
            MessageDialog(
                title = null,
                text = stringResource(R.string.offline_regions_cancel_message, dialog.name),
                confirmLabel = stringResource(R.string.offline_regions_cancel_confirm),
                onConfirm = { onAction(OfflineRegionsAction.ConfirmCancel) },
                dismissLabel = stringResource(R.string.offline_regions_cancel_keep),
                onDismiss = close,
            )
        }

        OfflineRegionsDialog.StorageLimit -> {
            ChoiceDialog(
                title = stringResource(R.string.offline_regions_limit),
                options = OfflineStorageLimit.entries.map { it to storageLimitLabel(it) },
                selected = limit,
                onSelect = { onAction(OfflineRegionsAction.SetStorageLimit(it)) },
                onDismiss = close,
            )
        }
    }
}

@Composable
private fun saveMessage(dialog: OfflineRegionsDialog.ConfirmSave): String {
    val size = fileSize(dialog.sizeBytes)
    val usedAfter = fileSize(dialog.usedAfterBytes)
    val free = fileSize(dialog.freeBytes)
    return dialog.limitBytes
        ?.let { stringResource(R.string.offline_regions_save_message_limited, size, usedAfter, limitLabel(it), free) }
        ?: stringResource(R.string.offline_regions_save_message, size, usedAfter, free)
}

// BUSY and ALREADY_SAVED never reach a dialog (the ViewModel closes them silently); they fall back to the network
// copy only to keep the mapping total.
@Composable
private fun unavailableText(reason: Reason): String =
    stringResource(
        when (reason) {
            Reason.GONE -> R.string.offline_regions_failure_gone
            Reason.SIZE_UNKNOWN -> R.string.offline_regions_offer_size_unknown
            Reason.NETWORK, Reason.BUSY, Reason.ALREADY_SAVED -> R.string.offline_regions_offer_network
        },
    )

// A plain message with one or two actions; [dismissLabel] null leaves the confirm action alone.
@Composable
private fun MessageDialog(
    title: String?,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = title?.let { { Text(text = it) } },
    text = { Text(text = text) },
    confirmButton = { DialogButton(label = confirmLabel, onClick = onConfirm) },
    dismissButton = dismissLabel?.let { { DialogButton(label = it, onClick = onDismiss) } },
)

@Composable
private fun DialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = TextButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = FemtoDimens.MinTouchTarget),
) {
    Text(text = label)
}
