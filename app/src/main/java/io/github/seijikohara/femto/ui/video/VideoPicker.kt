package io.github.seijikohara.femto.ui.video

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

private const val TAG = "VideoPicker"

// The document types the picker offers: any video. Whether the device can
// decode the one picked shows when it plays (VideoPlayer.failed).
private val VideoPickerMimeTypes: Array<String> = arrayOf("video/*")

/**
 * Return an action that opens the system file picker on video documents and
 * hands the picked document's URI to [onPicked]. The picker is the only route
 * to a file: it reaches USB storage on a head unit without any storage
 * permission, and the URI it returns carries a read grant the store keeps
 * (adoptSource). Backing out of the picker picks nothing. A device without a
 * picker (some head units strip the system's documents app) logs and does
 * nothing rather than crash the launcher.
 */
@Composable
internal fun rememberVideoPicker(onPicked: (String) -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { onPicked(it.toString()) }
        }
    return {
        runCatching { launcher.launch(VideoPickerMimeTypes) }
            .onFailure { Log.w(TAG, "no file picker: ${it.javaClass.simpleName}") }
    }
}
