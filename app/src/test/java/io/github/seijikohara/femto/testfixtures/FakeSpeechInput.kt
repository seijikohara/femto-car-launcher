package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.voice.SpeechInput
import io.github.seijikohara.femto.data.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Scriptable [SpeechInput]: a test sets [state] to play the recognizer's
 * lifecycle and reads the call counters to see what the ViewModel drove.
 */
internal class FakeSpeechInput(
    initial: VoiceState = VoiceState.Idle,
) : SpeechInput {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<VoiceState> = mutableState

    /** Every control call in order ("start", "stop", "reset", "destroy"). */
    val calls = mutableListOf<String>()

    var starts = 0
        private set
    var stops = 0
        private set
    var resets = 0
        private set
    var destroys = 0
        private set

    override fun start() {
        starts++
        calls += "start"
        mutableState.value = VoiceState.Listening(partial = "")
    }

    override fun stop() {
        stops++
        calls += "stop"
    }

    override fun reset() {
        resets++
        calls += "reset"
        mutableState.value = VoiceState.Idle
    }

    override fun destroy() {
        destroys++
        calls += "destroy"
    }
}
