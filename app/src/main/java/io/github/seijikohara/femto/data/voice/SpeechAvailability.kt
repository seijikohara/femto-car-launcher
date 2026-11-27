package io.github.seijikohara.femto.data.voice

/** Whether voice can speak on this device, and through which engine. */
internal sealed interface SpeechAvailability {
    data class Ready(
        val enginePackage: String,
    ) : SpeechAvailability

    /** No text-to-speech engine is installed. */
    data object NoEngine : SpeechAvailability

    /** Engines are installed, and none has an English voice ready. */
    data object NoEnglish : SpeechAvailability
}

/**
 * Return the engine voice would speak through: the default engine when it has an English voice, else the first other
 * engine that has one. Missing voice data counts as none: the launcher starts no download, and a prompt that waits on
 * one would never be heard.
 */
internal fun speechAvailabilityOf(engines: List<SpeechEngine>): SpeechAvailability =
    engines
        .filter { it.english == EnglishVoice.AVAILABLE }
        .let { speaking -> speaking.firstOrNull { it.isDefault } ?: speaking.firstOrNull() }
        ?.let { SpeechAvailability.Ready(it.packageName) }
        ?: if (engines.isEmpty()) SpeechAvailability.NoEngine else SpeechAvailability.NoEnglish
