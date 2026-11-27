package io.github.seijikohara.femto.data.voice

import io.github.seijikohara.femto.testfixtures.FakeSpeechEnginePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/** The speaker's state machine, with a fake engine, fake sessions and a fake sound focus: no platform is involved. */
class TurnSpeakerTest {
    private val english = mapOf(ENGINE to EnglishVoice.AVAILABLE)

    @Test
    fun `two open requests open the engine once`() =
        runTest {
            val sessions = FakeSpeechSessions()
            val speaker = speaker(sessions = sessions)

            speaker.open()
            speaker.open()
            runCurrent()

            assertEquals(listOf(ENGINE), sessions.opened)
        }

    @Test
    fun `an opened engine is ready under its package`() =
        runTest {
            val speaker = speaker()

            speaker.open()
            runCurrent()

            assertEquals(SpeakerStatus.Ready(ENGINE), speaker.status.value)
        }

    @Test
    fun `a device with no engine cannot speak`() =
        runTest {
            val speaker = speaker(port = FakeSpeechEnginePort(answers = emptyMap()))

            speaker.open()
            runCurrent()

            assertEquals(SpeakerStatus.Unavailable(SpeakerUnavailable.NO_ENGINE), speaker.status.value)
        }

    @Test
    fun `engines without an English voice cannot speak`() =
        runTest {
            val speaker = speaker(port = FakeSpeechEnginePort(mapOf(ENGINE to EnglishVoice.NOT_SUPPORTED)))

            speaker.open()
            runCurrent()

            assertEquals(SpeakerStatus.Unavailable(SpeakerUnavailable.NO_ENGLISH), speaker.status.value)
        }

    @Test
    fun `an engine that gives no session did not start`() =
        runTest {
            val speaker = speaker(sessions = FakeSpeechSessions(session = null))

            speaker.open()
            runCurrent()

            assertEquals(SpeakerStatus.Unavailable(SpeakerUnavailable.DID_NOT_START), speaker.status.value)
        }

    @Test
    fun `an unavailable speaker tries again when asked to open`() =
        runTest {
            val sessions = FakeSpeechSessions(session = null)
            val speaker = speaker(sessions = sessions)
            speaker.open()
            runCurrent()

            speaker.open()
            runCurrent()

            assertEquals(listOf(ENGINE, ENGINE), sessions.opened)
        }

    @Test
    fun `a prompt before the engine is ready is dropped`() =
        runTest {
            val speaker = speaker()

            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            assertEquals(SpokenPrompt("final at 80 m", PromptOutcome.NOT_READY), speaker.lastPrompt.value)
        }

    @Test
    fun `a prompt the sound is refused for is dropped and the engine is not asked`() =
        runTest {
            val session = FakeSpeechSession()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = FakeSoundFocus(grant = false))
            speaker.open()
            runCurrent()

            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            assertEquals<Any>(
                Pair(SpokenPrompt("final at 80 m", PromptOutcome.NO_SOUND), emptyList<String>()),
                Pair(speaker.lastPrompt.value, session.spoken),
            )
        }

    @Test
    fun `a prompt the engine refuses gives the sound back`() =
        runTest {
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(FakeSpeechSession(accepts = false)), focus = focus)
            speaker.open()
            runCurrent()

            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            assertEquals(
                Triple(SpokenPrompt("final at 80 m", PromptOutcome.ENGINE_REFUSED), 1, 1),
                Triple(speaker.lastPrompt.value, focus.requests, focus.releases),
            )
        }

    @Test
    fun `a prompt that plays to its end releases the sound once and is recorded as played`() =
        runTest {
            val session = FakeSpeechSession()
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            session.end(0, played = true)
            runCurrent()

            assertEquals(
                Triple(SpokenPrompt("final at 80 m", PromptOutcome.PLAYED), 1, 1),
                Triple(speaker.lastPrompt.value, focus.requests, focus.releases),
            )
        }

    @Test
    fun `a prompt is playing, with no outcome, until the engine reports its end`() =
        runTest {
            val session = FakeSpeechSession()
            val speaker = speaker(sessions = FakeSpeechSessions(session))
            speaker.open()
            runCurrent()

            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            assertEquals<Any>(
                Pair(SpokenPrompt("final at 80 m", outcome = null), listOf("Turn left.")),
                Pair(speaker.lastPrompt.value, session.spoken),
            )
        }

    @Test
    fun `a prompt the engine stopped is recorded as cut short`() =
        runTest {
            val session = FakeSpeechSession()
            val speaker = speaker(sessions = FakeSpeechSessions(session))
            speaker.open()
            runCurrent()
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            session.end(0, played = false)
            runCurrent()

            assertEquals(PromptOutcome.CUT_SHORT, speaker.lastPrompt.value?.outcome)
        }

    // The engine flushes the first prompt when the second comes, and reports it stopped; the sound stays held for the
    // second and goes back once, when that one ends.
    @Test
    fun `a second prompt while the first plays keeps the sound held once and releases it once at the end`() =
        runTest {
            val session = FakeSpeechSession()
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("In 300 metres, turn left.", label = "advance at 300 m")
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()
            val heldMeanwhile = Pair(focus.requests, focus.releases)

            session.end(0, played = false)
            runCurrent()
            val afterTheReplacedEnds = Pair(focus.requests, focus.releases)
            session.end(1, played = true)
            runCurrent()

            assertEquals(
                Triple(Pair(1, 0), Pair(1, 0), Pair(1, 1)),
                Triple(heldMeanwhile, afterTheReplacedEnds, Pair(focus.requests, focus.releases)),
            )
        }

    @Test
    fun `the last prompt is the newer one when it replaced one that was playing`() =
        runTest {
            val session = FakeSpeechSession()
            val speaker = speaker(sessions = FakeSpeechSessions(session))
            speaker.open()
            runCurrent()
            speaker.say("In 300 metres, turn left.", label = "advance at 300 m")
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            session.end(0, played = false)
            session.end(1, played = true)
            runCurrent()

            assertEquals(SpokenPrompt("final at 80 m", PromptOutcome.PLAYED), speaker.lastPrompt.value)
        }

    @Test
    fun `closing while the engine is opening leaves it closed and closes a session that arrives late`() =
        runTest {
            val late = FakeSpeechSession()
            val gate = CompletableDeferred<Unit>()
            val speaker = speaker(sessions = FakeSpeechSessions(late, gate = gate))
            speaker.open()
            runCurrent()
            val whileOpening = speaker.status.value

            speaker.close()
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals(
                Triple(SpeakerStatus.Opening, SpeakerStatus.Closed, true),
                Triple(whileOpening, speaker.status.value, late.closed),
            )
        }

    @Test
    fun `closing gives back a sound that is held and closes the session`() =
        runTest {
            val session = FakeSpeechSession()
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            speaker.close()
            runCurrent()

            assertEquals(
                Triple(SpeakerStatus.Closed, 1, true),
                Triple(speaker.status.value, focus.releases, session.closed),
            )
        }

    @Test
    fun `a prompt that ends after the speaker closed releases nothing more`() =
        runTest {
            val session = FakeSpeechSession()
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()
            speaker.close()
            runCurrent()

            session.end(0, played = false)
            runCurrent()

            assertEquals(1, focus.releases)
        }

    @Test
    fun `a speaker can be opened again after it was closed`() =
        runTest {
            val sessions = FakeSpeechSessions()
            val speaker = speaker(sessions = sessions)
            speaker.open()
            runCurrent()
            speaker.close()
            runCurrent()

            speaker.open()
            runCurrent()

            assertEquals(SpeakerStatus.Ready(ENGINE), speaker.status.value)
        }

    @Test
    fun `asking for the availability opens no session`() =
        runTest {
            val sessions = FakeSpeechSessions()
            val speaker = speaker(sessions = sessions)

            assertEquals<Any>(
                Pair(SpeechAvailability.Ready(ENGINE), emptyList<String>()),
                Pair(speaker.availability(), sessions.opened),
            )
        }

    private fun TestScope.speaker(
        port: SpeechEnginePort = FakeSpeechEnginePort(english),
        sessions: SpeechSessions = FakeSpeechSessions(),
        focus: SoundFocus = FakeSoundFocus(),
    ) = TurnSpeaker(engines = port, sessions = sessions, focus = focus, scope = backgroundScope)

    /** Opens to [session] (by default a fresh one); with a [gate], only once it is completed, whatever cancels it. */
    private class FakeSpeechSessions(
        private val session: SpeechSession? = FakeSpeechSession(),
        private val gate: CompletableDeferred<Unit>? = null,
    ) : SpeechSessions {
        val opened = mutableListOf<String>()

        override suspend fun open(enginePackage: String): SpeechSession? {
            opened += enginePackage
            // An engine mid-start cannot be interrupted: its session arrives even for an opening cancelled since.
            try {
                gate?.await()
            } catch (e: CancellationException) {
                return session
            }
            return session
        }
    }

    private class FakeSpeechSession(
        private val accepts: Boolean = true,
    ) : SpeechSession {
        val spoken = mutableListOf<String>()
        var closed = false
        private val ends = mutableListOf<(Boolean) -> Unit>()

        override fun speak(
            text: String,
            onEnd: (played: Boolean) -> Unit,
        ): Boolean =
            accepts.also {
                if (it) {
                    spoken += text
                    ends += onEnd
                }
            }

        override fun close() {
            closed = true
        }

        /** Report the end of the [index]th accepted utterance, as the engine's callback does. */
        fun end(
            index: Int,
            played: Boolean,
        ) = ends[index](played)
    }

    private class FakeSoundFocus(
        private val grant: Boolean = true,
    ) : SoundFocus {
        var requests = 0
        var releases = 0

        override fun request(): Boolean = grant.also { requests++ }

        override fun release() {
            releases++
        }
    }

    private companion object {
        const val ENGINE = "com.example.tts"
    }
}
