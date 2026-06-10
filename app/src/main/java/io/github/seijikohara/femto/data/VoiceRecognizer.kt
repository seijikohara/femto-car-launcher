package io.github.seijikohara.femto.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import io.github.seijikohara.femto.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "VoiceRecognizer"

/**
 * One step of the in-launcher voice flow. [Unavailable] means the device has no
 * speech recognizer (e.g. a head unit without Google services), in which case
 * the assistant sheet falls back to system-intent delegation.
 */
internal sealed interface VoiceState {
    data object Unavailable : VoiceState

    data object Idle : VoiceState

    data class Listening(
        val partial: String,
    ) : VoiceState

    data class Result(
        val text: String,
    ) : VoiceState

    data class Failed(
        val messageRes: Int,
    ) : VoiceState
}

/**
 * Thin wrapper over the platform [SpeechRecognizer] that exposes the recognition
 * lifecycle as a [StateFlow]. The launcher hosts the mic UI itself (rather than
 * firing `ACTION_ASSIST` and leaving), so speech is captured in-process and the
 * partial transcript streams to the sheet as the user speaks.
 *
 * Must be created and driven on the main thread — [SpeechRecognizer] requires
 * it. The owning ViewModel calls [start] / [stop] from Compose (main) and
 * [destroy] from `onCleared`.
 */
internal class VoiceRecognizer(
    context: Context,
) {
    private val appContext = context.applicationContext
    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    private val _state =
        MutableStateFlow<VoiceState>(if (isAvailable) VoiceState.Idle else VoiceState.Unavailable)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!isAvailable) return
        val active = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        _state.value = VoiceState.Listening(partial = "")
        active.startListening(recognizerIntent())
    }

    fun stop() {
        recognizer?.stopListening()
    }

    /** Return to the ready state, discarding any result or error. */
    fun reset() {
        if (isAvailable) _state.value = VoiceState.Idle
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private val listener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState.Listening(partial = "")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstText(partialResults)?.takeIf { it.isNotBlank() }?.let {
                    _state.value = VoiceState.Listening(partial = it)
                }
            }

            override fun onResults(results: Bundle?) {
                _state.value =
                    firstText(results)?.takeIf { it.isNotBlank() }?.let { VoiceState.Result(it) }
                        ?: VoiceState.Idle
            }

            override fun onError(error: Int) {
                // messageFor collapses codes into three strings; keep the raw
                // platform code for diagnosis.
                Log.w(TAG, "speech recognition error=$error")
                _state.value = VoiceState.Failed(messageRes = messageFor(error))
            }

            override fun onEndOfSpeech() = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) = Unit
        }

    internal fun firstText(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    internal fun messageFor(error: Int): Int =
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            -> R.string.assistant_voice_error_no_match

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            -> R.string.assistant_voice_error_network

            else -> R.string.assistant_voice_error_generic
        }
}
