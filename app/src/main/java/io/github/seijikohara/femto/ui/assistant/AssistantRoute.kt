package io.github.seijikohara.femto.ui.assistant

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seijikohara.femto.data.location.hasRecordAudioPermission
import io.github.seijikohara.femto.data.voice.VoiceState

/**
 * Assistant entry point: binds [AssistantViewModel], collects its state, and
 * owns the RECORD_AUDIO runtime request. The mic permission is requested only
 * when the user taps the mic (never at startup); a denial leaves the sheet on
 * its delegation rows. [onSubmitQuery] dispatches a recognized phrase, and
 * [onLaunchOption] fires the system-intent fallback — both flow up to
 * [io.github.seijikohara.femto.MainActivity].
 */
@Composable
internal fun AssistantRoute(
    onSubmitQuery: (String) -> Unit,
    onLaunchOption: (AssistantOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: AssistantViewModel =
        viewModel(factory = AssistantViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.onAction(AssistantAction.StartListening)
        }
    val onMicTap: () -> Unit = {
        if (uiState.voice is VoiceState.Listening) {
            viewModel.onAction(AssistantAction.StopListening)
        } else if (context.hasRecordAudioPermission()) {
            viewModel.onAction(AssistantAction.StartListening)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    AssistantScreen(
        uiState = uiState,
        onMicTap = onMicTap,
        onReset = { viewModel.onAction(AssistantAction.Reset) },
        onSubmitQuery = onSubmitQuery,
        onLaunchOption = onLaunchOption,
        modifier = modifier,
    )
}
