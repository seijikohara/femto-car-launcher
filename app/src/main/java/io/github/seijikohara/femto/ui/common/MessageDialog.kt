package io.github.seijikohara.femto.ui.common

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * A plain message with one or two actions, as the sheets over settings ask their confirmations (the Saved map regions
 * and Routing data sheets); [dismissLabel] null leaves the confirm action alone.
 */
@Composable
internal fun MessageDialog(
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
