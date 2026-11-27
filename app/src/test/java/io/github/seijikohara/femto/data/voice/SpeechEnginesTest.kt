package io.github.seijikohara.femto.data.voice

import android.speech.tts.TextToSpeech
import io.github.seijikohara.femto.testfixtures.FakeSpeechEnginePort
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    // Left to travel, one engine's failure would cancel the others and blank the whole diagnostics section.
    @Test
    fun `an engine that throws is reported as not started and the others keep their answers`() =
        runTest {
            val port =
                FakeSpeechEnginePort(
                    answers = mapOf("com.example.broken" to null, "com.example.tts" to EnglishVoice.AVAILABLE),
                    throwing = setOf("com.example.broken"),
                )

            assertEquals(
                listOf(EnglishVoice.DID_NOT_START, EnglishVoice.AVAILABLE),
                speechEngines(port).map { it.english },
            )
        }

    @Test
    fun `the engine picked in the system settings is the default while it is installed`() {
        val services = listOf(systemService("com.example.system", priority = 100), service("com.example.own"))

        assertEquals("com.example.own", defaultEngineOrNull(services, picked = "com.example.own"))
    }

    @Test
    fun `a picked engine that is gone gives way to the system image's engine`() {
        val services = listOf(service("com.example.own"), systemService("com.example.system", priority = 0))

        assertEquals("com.example.system", defaultEngineOrNull(services, picked = "com.example.removed"))
    }

    @Test
    fun `with no engine picked the highest-priority engine of the system image is the default`() {
        val services =
            listOf(
                systemService("com.example.low", priority = 0),
                systemService("com.example.high", priority = 100),
                service("com.example.own", priority = 500),
            )

        assertEquals("com.example.high", defaultEngineOrNull(services, picked = null))
    }

    // The platform falls back to an engine of the system image only, so an installed engine nobody picked is not one.
    @Test
    fun `an installed engine that was not picked is no default`() {
        assertEquals(null, defaultEngineOrNull(listOf(service("com.example.own")), picked = null))
    }

    @Test
    fun `no engine has no default`() {
        assertEquals(null, defaultEngineOrNull(emptyList(), picked = "com.example.removed"))
    }

    @Test
    fun `the platform's language codes map to the answer they stand for`() {
        assertEquals(
            listOf(
                EnglishVoice.NOT_SUPPORTED,
                EnglishVoice.MISSING_DATA,
                EnglishVoice.AVAILABLE,
                EnglishVoice.AVAILABLE,
                EnglishVoice.AVAILABLE,
            ),
            listOf(
                TextToSpeech.LANG_NOT_SUPPORTED,
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
            ).map(::englishVoiceOf),
        )
    }

    private fun service(
        packageName: String,
        priority: Int = 0,
    ) = SpeechService(packageName, isSystem = false, priority = priority)

    private fun systemService(
        packageName: String,
        priority: Int,
    ) = SpeechService(packageName, isSystem = true, priority = priority)
}
