package io.github.seijikohara.femto.data.voice

import io.github.seijikohara.femto.testfixtures.FakeSpeechEnginePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

    // An engine whose process dies mid-prompt never reports the end: the sound must not stay lowered for the rest of
    // the route.
    @Test
    fun `a prompt that never ends releases the sound once after the bound and is recorded as cut short`() =
        runTest {
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(FakeSpeechSession()), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()
            advanceTimeBy(PROMPT_BOUND_MS - 1)
            runCurrent()
            val justBefore = focus.releases

            advanceTimeBy(2)
            runCurrent()

            assertEquals(
                Triple(0, 1, SpokenPrompt("final at 80 m", PromptOutcome.CUT_SHORT)),
                Triple(justBefore, focus.releases, speaker.lastPrompt.value),
            )
        }

    @Test
    fun `a prompt that ends in time is not ended a second time by its bound`() =
        runTest {
            val session = FakeSpeechSession()
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()
            advanceTimeBy(1_000)
            session.end(0, played = true)
            runCurrent()

            advanceTimeBy(2 * PROMPT_BOUND_MS)
            runCurrent()

            assertEquals(
                Pair(1, SpokenPrompt("final at 80 m", PromptOutcome.PLAYED)),
                Pair(focus.releases, speaker.lastPrompt.value),
            )
        }

    // The older prompt's bound must not end the newer one, which holds the sound until its own end or bound.
    @Test
    fun `a newer prompt keeps the sound past the bound of the one it replaced`() =
        runTest {
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(FakeSpeechSession()), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("In 300 metres, turn left.", label = "advance at 300 m")
            runCurrent()
            advanceTimeBy(10_000)
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            advanceTimeBy(PROMPT_BOUND_MS - 10_000 + 1_000)
            runCurrent()
            val pastTheFirstBound = focus.releases
            advanceTimeBy(10_000)
            runCurrent()

            assertEquals(
                Triple(0, 1, SpokenPrompt("final at 80 m", PromptOutcome.CUT_SHORT)),
                Triple(pastTheFirstBound, focus.releases, speaker.lastPrompt.value),
            )
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

    // --- Whether the device can speak: a lasting record, which a close does not reset ---

    @Test
    fun `a device with no engine installed is known to be unable to speak at once, with nothing started`() =
        runTest {
            val port = FakeSpeechEnginePort(answers = emptyMap())
            val sessions = FakeSpeechSessions()
            val speaker = speaker(port = port, sessions = sessions)

            runCurrent()

            assertEquals<Any>(
                Triple(true, emptyList<String>(), emptyList<String>()),
                Triple(speaker.cannotSpeak.value, sessions.opened, port.asked),
            )
        }

    @Test
    fun `a device with an engine is not known to be unable to speak before anything is asked`() =
        runTest {
            val speaker = speaker()

            runCurrent()

            assertEquals(false, speaker.cannotSpeak.value)
        }

    @Test
    fun `an opening that ends without English records that the device cannot speak, and a close keeps it`() =
        runTest {
            val speaker = speaker(port = FakeSpeechEnginePort(mapOf(ENGINE to EnglishVoice.NOT_SUPPORTED)))
            speaker.open()
            runCurrent()
            val afterTheOpening = speaker.cannotSpeak.value

            speaker.close()
            runCurrent()

            assertEquals(Pair(true, true), afterTheOpening to speaker.cannotSpeak.value)
        }

    @Test
    fun `an opening that ends unavailable for any other reason records it too`() =
        runTest {
            val speaker = speaker(sessions = FakeSpeechSessions(session = null))

            speaker.open()
            runCurrent()

            assertEquals(true, speaker.cannotSpeak.value)
        }

    @Test
    fun `a later opening that ends ready records that the device can speak`() =
        runTest {
            val sessions = FakeSpeechSessions().apply { throwing = true }
            val speaker = speaker(sessions = sessions)
            speaker.open()
            runCurrent()
            val afterTheFailure = speaker.cannotSpeak.value
            sessions.throwing = false

            speaker.open()
            runCurrent()

            assertEquals(Pair(true, false), afterTheFailure to speaker.cannotSpeak.value)
        }

    @Test
    fun `the availability the settings ask for sets it too`() =
        runTest {
            val without = speaker(port = FakeSpeechEnginePort(mapOf(ENGINE to EnglishVoice.NOT_SUPPORTED)))
            val withEnglish = speaker(sessions = FakeSpeechSessions().apply { throwing = true })
            withEnglish.open()
            runCurrent()
            val beforeTheAsk = withEnglish.cannotSpeak.value

            without.availability()
            withEnglish.availability()

            assertEquals(
                Triple(true, true, false),
                Triple(without.cannotSpeak.value, beforeTheAsk, withEnglish.cannotSpeak.value),
            )
        }

    // --- Opening: the default engine first, the probe only when it gives no session ---

    @Test
    fun `the default engine opens without the probe asking any engine`() =
        runTest {
            val port = FakeSpeechEnginePort(english, default = ENGINE)
            val sessions = FakeSpeechSessions()
            val speaker = speaker(port = port, sessions = sessions)

            speaker.open()
            runCurrent()

            assertEquals<Any>(
                Triple(SpeakerStatus.Ready(ENGINE), listOf(ENGINE), emptyList<String>()),
                Triple(speaker.status.value, sessions.opened, port.asked),
            )
        }

    @Test
    fun `a default engine without English falls back to another engine that has it`() =
        runTest {
            val port =
                FakeSpeechEnginePort(
                    mapOf(DEFAULT_ENGINE to EnglishVoice.NOT_SUPPORTED, ENGINE to EnglishVoice.AVAILABLE),
                    default = DEFAULT_ENGINE,
                )
            val sessions = FakeSpeechSessions(answers = mapOf(DEFAULT_ENGINE to null))
            val speaker = speaker(port = port, sessions = sessions)

            speaker.open()
            runCurrent()

            assertEquals<Any>(
                Pair(SpeakerStatus.Ready(ENGINE), listOf(DEFAULT_ENGINE, ENGINE)),
                Pair(speaker.status.value, sessions.opened),
            )
        }

    @Test
    fun `a device with no engine installed starts nothing`() =
        runTest {
            val port = FakeSpeechEnginePort(answers = emptyMap())
            val sessions = FakeSpeechSessions()
            val speaker = speaker(port = port, sessions = sessions)

            speaker.open()
            runCurrent()

            assertEquals<Any>(
                Triple(
                    SpeakerStatus.Unavailable(SpeakerUnavailable.NO_ENGINE),
                    emptyList<String>(),
                    emptyList<String>(),
                ),
                Triple(speaker.status.value, sessions.opened, port.asked),
            )
        }

    @Test
    fun `a default engine with English that does not open is not opened again`() =
        runTest {
            val sessions = FakeSpeechSessions(session = null)
            val speaker = speaker(port = FakeSpeechEnginePort(english, default = ENGINE), sessions = sessions)

            speaker.open()
            runCurrent()

            assertEquals<Any>(
                Pair(SpeakerStatus.Unavailable(SpeakerUnavailable.DID_NOT_START), listOf(ENGINE)),
                Pair(speaker.status.value, sessions.opened),
            )
        }

    @Test
    fun `a default engine without English and no other cannot speak`() =
        runTest {
            val port = FakeSpeechEnginePort(mapOf(ENGINE to EnglishVoice.NOT_SUPPORTED), default = ENGINE)
            val speaker = speaker(port = port, sessions = FakeSpeechSessions(session = null))

            speaker.open()
            runCurrent()

            assertEquals(SpeakerStatus.Unavailable(SpeakerUnavailable.NO_ENGLISH), speaker.status.value)
        }

    // --- A speaker that closes or is refused while a prompt plays ---

    @Test
    fun `a prompt that was playing when the speaker closed is recorded as cut short`() =
        runTest {
            val speaker = speaker()
            speaker.open()
            runCurrent()
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            speaker.close()
            runCurrent()

            assertEquals(SpokenPrompt("final at 80 m", PromptOutcome.CUT_SHORT), speaker.lastPrompt.value)
        }

    // The refused prompt never reached the engine, so the earlier one is still speaking and still holds the sound.
    @Test
    fun `a refusal while an earlier prompt plays leaves its hold as it was`() =
        runTest {
            val session = FakeSpeechSession()
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("In 300 metres, turn left.", label = "advance at 300 m")
            runCurrent()
            session.accepts = false
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()
            val afterTheRefusal = Pair(focus.requests, focus.releases)

            session.end(0, played = true)
            runCurrent()

            assertEquals(
                Pair(Pair(1, 0), 1),
                Pair(afterTheRefusal, focus.releases),
            )
        }

    @Test
    fun `a refusal while an earlier prompt plays keeps its bound`() =
        runTest {
            val session = FakeSpeechSession()
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("In 300 metres, turn left.", label = "advance at 300 m")
            runCurrent()
            session.accepts = false
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            advanceTimeBy(PROMPT_BOUND_MS + 1)
            runCurrent()

            assertEquals(1, focus.releases)
        }

    @Test
    fun `a refusal with nothing playing gives the sound back`() =
        runTest {
            val session = FakeSpeechSession()
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            session.accepts = false

            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()

            assertEquals(
                Pair(1, SpokenPrompt("final at 80 m", PromptOutcome.ENGINE_REFUSED)),
                Pair(focus.releases, speaker.lastPrompt.value),
            )
        }

    // --- Engines that throw: the command loop survives every one of them ---

    @Test
    fun `an engine that throws on opening did not start, and a later opening works`() =
        runTest {
            val sessions = FakeSpeechSessions().apply { throwing = true }
            val speaker = speaker(sessions = sessions)
            speaker.open()
            runCurrent()
            val afterTheThrow = speaker.status.value
            sessions.throwing = false

            speaker.open()
            runCurrent()

            assertEquals(
                Pair(SpeakerStatus.Unavailable(SpeakerUnavailable.DID_NOT_START), SpeakerStatus.Ready(ENGINE)),
                Pair(afterTheThrow, speaker.status.value),
            )
        }

    @Test
    fun `a session that throws on a prompt refuses it, gives the sound back, and a later prompt plays`() =
        runTest {
            val session = FakeSpeechSession().apply { throwOnSpeak = true }
            val focus = FakeSoundFocus()
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()
            val afterTheThrow = Triple(speaker.lastPrompt.value, focus.requests, focus.releases)
            session.throwOnSpeak = false

            speaker.say("Turn right.", label = "final at 70 m")
            runCurrent()

            assertEquals<Any>(
                Pair(
                    Triple(SpokenPrompt("final at 80 m", PromptOutcome.ENGINE_REFUSED), 1, 1),
                    Pair(SpokenPrompt("final at 70 m", outcome = null), listOf("Turn right.")),
                ),
                Pair(afterTheThrow, Pair(speaker.lastPrompt.value, session.spoken)),
            )
        }

    @Test
    fun `a sound request that throws drops the prompt, and a later prompt plays`() =
        runTest {
            val session = FakeSpeechSession()
            val focus = FakeSoundFocus().apply { throwOnRequest = true }
            val speaker = speaker(sessions = FakeSpeechSessions(session), focus = focus)
            speaker.open()
            runCurrent()
            speaker.say("Turn left.", label = "final at 80 m")
            runCurrent()
            val afterTheThrow = Pair(speaker.lastPrompt.value, session.spoken.toList())
            focus.throwOnRequest = false

            speaker.say("Turn right.", label = "final at 70 m")
            runCurrent()

            assertEquals<Any>(
                Pair(
                    Pair(SpokenPrompt("final at 80 m", PromptOutcome.NO_SOUND), emptyList<String>()),
                    listOf("Turn right."),
                ),
                Pair(afterTheThrow, session.spoken),
            )
        }

    @Test
    fun `a speaker opened, closed and opened again ends ready, and the first opening's late session is closed`() =
        runTest {
            val first = FakeSpeechSession()
            val second = FakeSpeechSession()
            val secondGate = CompletableDeferred<Unit>()
            val speaker =
                speaker(sessions = FakeQueuedSessions(listOf(first, second), listOf(CompletableDeferred(), secondGate)))
            speaker.open()
            runCurrent()
            speaker.close()
            runCurrent()
            speaker.open()
            runCurrent()

            secondGate.complete(Unit)
            runCurrent()

            assertEquals(
                Triple(SpeakerStatus.Ready(ENGINE), true, false),
                Triple(speaker.status.value, first.closed, second.closed),
            )
        }

    @Test
    fun `closing an unavailable speaker leaves it closed`() =
        runTest {
            val speaker = speaker(sessions = FakeSpeechSessions(session = null))
            speaker.open()
            runCurrent()

            speaker.close()
            runCurrent()

            assertEquals(SpeakerStatus.Closed, speaker.status.value)
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
        // What a named engine opens to instead of [session].
        private val answers: Map<String, SpeechSession?> = emptyMap(),
    ) : SpeechSessions {
        val opened = mutableListOf<String>()

        // Whether opening fails with an exception, as a platform call can.
        var throwing = false

        override suspend fun open(enginePackage: String): SpeechSession? {
            opened += enginePackage
            check(!throwing) { "engine failed" }
            if (enginePackage in answers) return answers[enginePackage]
            // An engine mid-start cannot be interrupted: its session arrives even for an opening cancelled since.
            try {
                gate?.await()
            } catch (e: CancellationException) {
                return session
            }
            return session
        }
    }

    /** Opens to a session per call, in order, each only once its gate (if any) is completed. */
    private class FakeQueuedSessions(
        private val sessions: List<FakeSpeechSession>,
        private val gates: List<CompletableDeferred<Unit>?>,
    ) : SpeechSessions {
        private var calls = 0

        override suspend fun open(enginePackage: String): SpeechSession? {
            val index = calls++
            try {
                gates[index]?.await()
            } catch (e: CancellationException) {
                // An engine mid-start cannot be interrupted: its session arrives for an opening cancelled since.
            }
            return sessions[index]
        }
    }

    private class FakeSpeechSession(
        var accepts: Boolean = true,
    ) : SpeechSession {
        val spoken = mutableListOf<String>()
        var closed = false

        // Whether a request fails with an exception, as a platform call can.
        var throwOnSpeak = false
        private val ends = mutableListOf<(Boolean) -> Unit>()

        override fun speak(
            text: String,
            onEnd: (played: Boolean) -> Unit,
        ): Boolean {
            check(!throwOnSpeak) { "engine failed" }
            return accepts.also {
                if (it) {
                    spoken += text
                    ends += onEnd
                }
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

        // Whether a request fails with an exception, as a platform call can.
        var throwOnRequest = false

        override fun request(): Boolean {
            check(!throwOnRequest) { "focus failed" }
            return grant.also { requests++ }
        }

        override fun release() {
            releases++
        }
    }

    private companion object {
        const val ENGINE = "com.example.tts"
        const val DEFAULT_ENGINE = "com.example.default"

        // The speaker's own bound on a prompt, as its tests see it: a prompt of a few seconds has long ended by then.
        const val PROMPT_BOUND_MS = 15_000L
    }
}
