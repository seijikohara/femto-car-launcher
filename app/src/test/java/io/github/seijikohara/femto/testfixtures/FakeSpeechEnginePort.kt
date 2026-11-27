package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.voice.EnglishVoice
import io.github.seijikohara.femto.data.voice.SpeechEnginePort
import kotlinx.coroutines.awaitCancellation

/** Answers from a table; an engine mapped to null never answers, and one in [throwing] fails when asked. */
internal class FakeSpeechEnginePort(
    private val answers: Map<String, EnglishVoice?>,
    private val default: String? = null,
    private val throwing: Set<String> = emptySet(),
) : SpeechEnginePort {
    val asked = mutableListOf<String>()

    override fun installed(): List<String> = answers.keys.toList()

    override fun defaultOrNull(): String? = default

    override suspend fun english(packageName: String): EnglishVoice {
        asked += packageName
        check(packageName !in throwing) { "engine failed" }
        return answers.getValue(packageName) ?: awaitCancellation()
    }
}
