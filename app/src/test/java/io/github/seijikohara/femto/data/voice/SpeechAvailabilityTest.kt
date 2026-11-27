package io.github.seijikohara.femto.data.voice

import org.junit.Test
import kotlin.test.assertEquals

/** Which engine voice would speak through, from what the engines said about English. */
class SpeechAvailabilityTest {
    @Test
    fun `the default engine is chosen when it has an English voice`() {
        assertEquals(
            SpeechAvailability.Ready("com.example.default"),
            speechAvailabilityOf(
                listOf(
                    engine("com.example.other", isDefault = false, EnglishVoice.AVAILABLE),
                    engine("com.example.default", isDefault = true, EnglishVoice.AVAILABLE),
                ),
            ),
        )
    }

    @Test
    fun `another engine is chosen when the default has no English voice`() {
        assertEquals(
            SpeechAvailability.Ready("com.example.other"),
            speechAvailabilityOf(
                listOf(
                    engine("com.example.default", isDefault = true, EnglishVoice.NOT_SUPPORTED),
                    engine("com.example.other", isDefault = false, EnglishVoice.AVAILABLE),
                ),
            ),
        )
    }

    @Test
    fun `the first of several other engines is chosen`() {
        assertEquals(
            SpeechAvailability.Ready("com.example.first"),
            speechAvailabilityOf(
                listOf(
                    engine("com.example.first", isDefault = false, EnglishVoice.AVAILABLE),
                    engine("com.example.second", isDefault = false, EnglishVoice.AVAILABLE),
                ),
            ),
        )
    }

    // The launcher starts no download, so an engine that would need one has nothing to say.
    @Test
    fun `an engine whose English voice data is missing counts as having none`() {
        assertEquals(
            SpeechAvailability.NoEnglish,
            speechAvailabilityOf(listOf(engine("com.example.tts", isDefault = true, EnglishVoice.MISSING_DATA))),
        )
    }

    @Test
    fun `no installed engine is reported as none`() {
        assertEquals(SpeechAvailability.NoEngine, speechAvailabilityOf(emptyList()))
    }

    @Test
    fun `engines of which none has English are reported as without English`() {
        assertEquals(
            SpeechAvailability.NoEnglish,
            speechAvailabilityOf(
                listOf(
                    engine("com.example.a", isDefault = true, EnglishVoice.NOT_SUPPORTED),
                    engine("com.example.b", isDefault = false, EnglishVoice.DID_NOT_START),
                    engine("com.example.c", isDefault = false, EnglishVoice.NO_ANSWER),
                ),
            ),
        )
    }

    private fun engine(
        packageName: String,
        isDefault: Boolean,
        english: EnglishVoice,
    ) = SpeechEngine(packageName, isDefault = isDefault, english = english)
}
