package io.github.seijikohara.femto.data.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechEnginesTest {
    // The case a head unit is expected to show: Android 13 ships no engine of its own.
    @Test
    fun `a device with no engine reports none and starts nothing`() =
        runTest {
            val port = FakeSpeechEnginePort(answers = emptyMap())

            assertEquals(emptyList(), speechEngines(port))
            assertEquals(emptyList(), port.asked)
        }

    @Test
    fun `an engine with an English voice is reported as available`() =
        runTest {
            val port = FakeSpeechEnginePort(mapOf("com.example.tts" to EnglishVoice.AVAILABLE))

            assertEquals(
                listOf(SpeechEngine("com.example.tts", isDefault = false, english = EnglishVoice.AVAILABLE)),
                speechEngines(port),
            )
        }

    @Test
    fun `an engine without English keeps the reason it gave`() =
        runTest {
            val port =
                FakeSpeechEnginePort(
                    mapOf(
                        "com.example.none" to EnglishVoice.NOT_SUPPORTED,
                        "com.example.nodata" to EnglishVoice.MISSING_DATA,
                        "com.example.broken" to EnglishVoice.DID_NOT_START,
                    ),
                )

            assertEquals(
                listOf(EnglishVoice.NOT_SUPPORTED, EnglishVoice.MISSING_DATA, EnglishVoice.DID_NOT_START),
                speechEngines(port).map { it.english },
            )
        }

    @Test
    fun `only the platform's default engine is marked`() =
        runTest {
            val port =
                FakeSpeechEnginePort(
                    answers =
                        mapOf(
                            "com.example.first" to EnglishVoice.AVAILABLE,
                            "com.example.second" to EnglishVoice.AVAILABLE,
                        ),
                    default = "com.example.second",
                )

            assertEquals(listOf(false, true), speechEngines(port).map { it.isDefault })
        }

    @Test
    fun `an engine that never answers is reported as silent and the others keep their answers`() =
        runTest {
            val port =
                FakeSpeechEnginePort(
                    mapOf("com.example.silent" to null, "com.example.tts" to EnglishVoice.AVAILABLE),
                )

            assertEquals(
                listOf(EnglishVoice.NO_ANSWER, EnglishVoice.AVAILABLE),
                speechEngines(port, answerTimeoutMs = 5_000L).map { it.english },
            )
        }

    // Asked one after another, each silent engine would add its own wait to the diagnostics section.
    @Test
    fun `several silent engines cost a single wait`() =
        runTest {
            val port = FakeSpeechEnginePort(mapOf("com.example.a" to null, "com.example.b" to null))

            speechEngines(port, answerTimeoutMs = 5_000L)

            assertEquals(5_000L, currentTime)
        }

    /** Answers from a table; an engine mapped to null never answers. */
    private class FakeSpeechEnginePort(
        private val answers: Map<String, EnglishVoice?>,
        private val default: String? = null,
    ) : SpeechEnginePort {
        val asked = mutableListOf<String>()

        override fun installed(): List<String> = answers.keys.toList()

        override fun defaultOrNull(): String? = default

        override suspend fun english(packageName: String): EnglishVoice {
            asked += packageName
            return answers.getValue(packageName) ?: awaitCancellation()
        }
    }
}
