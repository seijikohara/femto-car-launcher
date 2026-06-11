package io.github.seijikohara.femto.ui.assistant

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.voice.VoiceRecognizer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the in-launcher voice flow: owns a [VoiceRecognizer] and surfaces its
 * lifecycle as [AssistantUiState]. The recognizer captures speech in-process so
 * the assistant stays on the launcher rather than handing off to a system
 * Activity; the sheet falls back to delegation when recognition is unavailable.
 */
internal class AssistantViewModel(
    application: Application,
) : ViewModel() {
    private val recognizer = VoiceRecognizer(application)

    val uiState: StateFlow<AssistantUiState> =
        recognizer.state
            .map { AssistantUiState(voice = it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                AssistantUiState(voice = recognizer.state.value),
            )

    fun onAction(action: AssistantAction) {
        when (action) {
            AssistantAction.StartListening -> recognizer.start()
            AssistantAction.StopListening -> recognizer.stop()
            AssistantAction.Reset -> recognizer.reset()
        }
    }

    override fun onCleared() {
        recognizer.destroy()
    }
}

internal class AssistantViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return AssistantViewModel(application) as T
    }
}
