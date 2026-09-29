package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.update.SUPPORTED_MANIFEST_SCHEMA_VERSION
import io.github.seijikohara.femto.data.update.UpdateChannel
import io.github.seijikohara.femto.data.update.UpdateManifest
import io.github.seijikohara.femto.ui.common.ImmersiveSheetEffect
import io.github.seijikohara.femto.ui.common.fileSize
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress

/**
 * The dashboard's update prompt: asks once whether to update to [update]
 * (HomeViewModel.updatePrompt), and names its download size first. "Update"
 * opens Settings on Updates and starts the one-tap update there; "Later"
 * leaves the update to the dock's dot and Settings. Back counts as "Later":
 * either way the prompt never asks about this build again. A tap beside the
 * dialog answers nothing, since a stray tap near the map must not answer
 * "never ask about this version". [fullscreen] keeps the dashboard's
 * immersive mode on the dialog's own window.
 */
@Composable
internal fun UpdatePromptDialog(
    update: UpdateManifest,
    onAction: (HomeAction) -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val later = { onAction(HomeAction.UpdateLater(update.versionCode)) }
    AlertDialog(
        onDismissRequest = later,
        modifier = modifier,
        title = { Text(text = stringResource(R.string.update_prompt_title)) },
        text = {
            // The dialog's window does not inherit the Activity's immersive
            // flags; on Android 13 its focus brings the system bars back.
            ImmersiveSheetEffect(fullscreen)
            Text(text = stringResource(R.string.update_prompt_text, update.versionName, fileSize(update.apk.size)))
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(HomeAction.UpdateNow(update.versionCode)) },
                modifier = AnswerTarget,
            ) { Text(text = stringResource(R.string.update_prompt_update)) }
        },
        dismissButton = {
            TextButton(
                onClick = later,
                modifier = AnswerTarget,
            ) { Text(text = stringResource(R.string.update_prompt_later)) }
        },
        properties = DialogProperties(dismissOnClickOutside = false),
    )
}

// Each answer is a full tap target both ways: a short label such as "Later"
// alone makes a button narrower than the floor.
private val AnswerTarget = Modifier.sizeIn(
    minWidth = FemtoDimens.MinTouchTarget,
    minHeight = FemtoDimens.MinTouchTarget,
)

@PreviewLightDark
@PreviewTextStress
@Composable
private fun UpdatePromptDialogPreview() =
    FemtoTheme {
        UpdatePromptDialog(
            update =
                UpdateManifest(
                    schemaVersion = SUPPORTED_MANIFEST_SCHEMA_VERSION,
                    channel = UpdateChannel.STABLE.id,
                    versionCode = 26092501,
                    versionName = "2026.09.25-1",
                    apk =
                        UpdateManifest.Apk(
                            name = "femto-car-launcher.apk",
                            // A release APK's size, the scale the prompt's text has to fit.
                            size = 45_310_215L,
                            sha256 = "0".repeat(64),
                            url = "https://example.invalid/femto-car-launcher.apk",
                        ),
                ),
            onAction = {},
            fullscreen = false,
        )
    }
