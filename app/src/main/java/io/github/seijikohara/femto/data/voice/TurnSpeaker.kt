package io.github.seijikohara.femto.data.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "TurnSpeaker"

// A prompt not reported ended this long after the engine accepted it counts as cut short. The longest sentence takes a
// few seconds to say; an engine whose process dies mid-prompt reports nothing, and without a bound the sound would stay
// lowered for the rest of the route.
private const val PROMPT_MAX_MS = 15_000L

/** One opened engine. */
internal interface SpeechSession {
    /**
     * Speak [text] in place of anything still queued. [onEnd] runs once, with whether the utterance played to its end.
     * Returns false when the engine refused the request, and then never calls [onEnd].
     */
    fun speak(
        text: String,
        onEnd: (played: Boolean) -> Unit,
    ): Boolean

    fun close()
}

internal fun interface SpeechSessions {
    /** Open [enginePackage] with an English voice, or return null when it did not start or has none after all. */
    suspend fun open(enginePackage: String): SpeechSession?
}

/** The sound for the length of a prompt, asked for in the way that lets a player lower its volume. */
internal interface SoundFocus {
    /** Return whether the sound was granted. */
    fun request(): Boolean

    fun release()
}

internal sealed interface SpeakerStatus {
    data object Closed : SpeakerStatus

    data object Opening : SpeakerStatus

    data class Ready(
        val enginePackage: String,
    ) : SpeakerStatus

    data class Unavailable(
        val reason: SpeakerUnavailable,
    ) : SpeakerStatus
}

internal enum class SpeakerUnavailable { NO_ENGINE, NO_ENGLISH, DID_NOT_START }

/** How a prompt ended, or why it never began. */
internal enum class PromptOutcome { PLAYED, CUT_SHORT, NO_SOUND, ENGINE_REFUSED, NOT_READY }

/**
 * The last prompt handed to the speaker, for diagnostics: [label] carries no manoeuvre, and [outcome] is null while the
 * prompt plays.
 */
internal data class SpokenPrompt(
    val label: String,
    val outcome: PromptOutcome?,
)

/**
 * Speaks the turn prompts through one text-to-speech engine, for the length of a route.
 *
 * Every request goes through one queue to one coroutine, which is the only code that touches the state: the engine's
 * callbacks arrive on its own threads, and the dashboard calls from the main thread, which must not wait on an
 * engine. A prompt that cannot be spoken when it is due (the engine is not ready, the sound is refused, the engine
 * refuses it) is dropped, never queued: a turn announced late is worse than none.
 *
 * The sound is held from the first prompt until the last one ends, once however many prompts replace each other, and
 * released exactly once. The constructor stays injectable for JVM tests; production wiring goes through [get].
 */
internal class TurnSpeaker(
    private val engines: SpeechEnginePort,
    private val sessions: SpeechSessions,
    private val focus: SoundFocus,
    private val scope: CoroutineScope,
) {
    private val mutableStatus = MutableStateFlow<SpeakerStatus>(SpeakerStatus.Closed)
    private val mutableLastPrompt = MutableStateFlow<SpokenPrompt?>(null)

    /** Whether an engine is open, opening, or why none can be. */
    val status: StateFlow<SpeakerStatus> = mutableStatus.asStateFlow()

    /** The last prompt handed to the speaker and how it went; null before the first. */
    val lastPrompt: StateFlow<SpokenPrompt?> = mutableLastPrompt.asStateFlow()

    private val mutableCannotSpeak = MutableStateFlow(false)

    /**
     * Whether this device is known to be unable to speak: no engine is installed, or an opening or the settings' question
     * found none with an English voice, or the engine did not start. False until known otherwise, and a close does not
     * reset it, unlike [status], which falls back to Closed: the turn card's control reads this one.
     */
    val cannotSpeak: StateFlow<Boolean> = mutableCannotSpeak.asStateFlow()

    private val commands = Channel<Command>(Channel.UNLIMITED)

    // Everything below is touched only by the coroutine that reads [commands].
    private var session: SpeechSession? = null
    private var opening: Job? = null

    // Counts the openings and closings, so that an answer to an opening that has been closed since is told apart.
    private var generation = 0
    private var promptCount = 0

    // The prompt that holds the sound, by its number; a prompt that a newer one replaced no longer does.
    private var playing: Int? = null

    // The bound on [playing]: it sends the prompt's end when the engine has not, and goes with the prompt.
    private var playingBound: Job? = null
    private var focusHeld = false

    // After the fields above: the coroutine may start on another thread before the constructor returns.
    init {
        scope.launch { for (command in commands) handleSafely(command) }
        // A device with no engine is known at once, without starting anything, so its card never offers the control.
        commands.trySend(Command.CheckInstalled)
    }

    /** Start opening the engine; does nothing while it is opening or open. */
    fun open() {
        commands.trySend(Command.Open)
    }

    /** Speak [text]; one that cannot be spoken now is dropped, never queued. [label] is what diagnostics keep of it. */
    fun say(
        text: String,
        label: String,
    ) {
        commands.trySend(Command.Say(text, label))
    }

    /** Close the engine, stopping a prompt that is playing and giving the sound back. */
    fun close() {
        commands.trySend(Command.Close)
    }

    /** Ask the engines without keeping one open: for Settings. */
    suspend fun availability(): SpeechAvailability =
        speechAvailabilityOf(speechEngines(engines)).also { mutableCannotSpeak.value = it !is SpeechAvailability.Ready }

    private sealed interface Command {
        data object CheckInstalled : Command

        data object Open : Command

        class Say(
            val text: String,
            val label: String,
        ) : Command

        data object Close : Command

        // The answer to the opening of [generation]: what the engines said, null when they could not be asked, and the
        // session when one opened.
        class Opened(
            val generation: Int,
            val availability: SpeechAvailability?,
            val session: SpeechSession?,
        ) : Command

        class Ended(
            val sequence: Int,
            val played: Boolean,
        ) : Command
    }

    // A failure here would end the launcher's process, which runs the home screen; speech is never worth that. The
    // handlers do not suspend, so a CancellationException here (java.util.concurrent's, from a platform call) is a
    // failure like any other: rethrown, it would end the command loop for good.
    private fun handleSafely(command: Command) {
        try {
            handle(command)
        } catch (e: Exception) {
            Log.w(TAG, "voice command failed: ${e.javaClass.simpleName}")
        }
    }

    private fun handle(command: Command) =
        when (command) {
            Command.CheckInstalled -> {
                checkInstalled()
            }

            Command.Open -> {
                startOpening()
            }

            is Command.Say -> {
                speak(command.text, command.label)
            }

            // The dashboard sends one, when it had opened the engine; a speaker already closed has nothing to do.
            Command.Close -> {
                shutDownUnlessClosed()
            }

            is Command.Opened -> {
                finishOpening(command)
            }

            is Command.Ended -> {
                finishPrompt(command.sequence, command.played)
            }
        }

    // Off the main thread: the command coroutine runs in the speaker's own scope.
    private fun checkInstalled() {
        if (engines.installed().isEmpty()) mutableCannotSpeak.value = true
    }

    private fun startOpening() {
        if (mutableStatus.value is SpeakerStatus.Closed || mutableStatus.value is SpeakerStatus.Unavailable) {
            mutableStatus.value = SpeakerStatus.Opening
            val mine = ++generation
            opening = scope.launch { commands.trySend(openEngine(mine)) }
        }
    }

    // The platform's default engine is tried first, on its own: the probe starts every engine and asks each, which
    // took 8 s on an emulator under load, and a probe that runs out would make voice unavailable for the whole route.
    // The probe runs only when the default gives no session (none, it did not start, or it has no English), and its
    // answer names another engine to open, or says why none can.
    private suspend fun openEngine(mine: Int): Command.Opened {
        val installed = askOrNull { engines.installed() }
        val default = askOrNull { engines.defaultOrNull() }
        val viaDefault = default?.let { name -> askOrNull { sessions.open(name) } }
        return when {
            installed == null -> {
                Command.Opened(mine, availability = null, session = null)
            }

            installed.isEmpty() -> {
                Command.Opened(mine, SpeechAvailability.NoEngine, session = null)
            }

            default != null && viaDefault != null -> {
                Command.Opened(mine, SpeechAvailability.Ready(default), viaDefault)
            }

            else -> {
                val availability = askOrNull { availability() }
                // An engine the probe finds with English that is the default one has been tried: not again.
                val other = (availability as? SpeechAvailability.Ready)?.takeIf { it.enginePackage != default }
                val opened = other?.let { ready -> askOrNull { sessions.open(ready.enginePackage) } }
                Command.Opened(mine, availability, opened)
            }
        }
    }

    // An engine that throws has answered: it did not start.
    private suspend fun <T> askOrNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "voice engine failed: ${e.javaClass.simpleName}")
            null
        }

    private fun finishOpening(answer: Command.Opened) {
        if (answer.generation != generation) {
            // Closed while the engine was starting: its session arrived for nobody.
            answer.session?.close()
        } else {
            opening = null
            val next =
                when (answer.availability) {
                    null -> {
                        SpeakerStatus.Unavailable(SpeakerUnavailable.DID_NOT_START)
                    }

                    SpeechAvailability.NoEngine -> {
                        SpeakerStatus.Unavailable(SpeakerUnavailable.NO_ENGINE)
                    }

                    SpeechAvailability.NoEnglish -> {
                        SpeakerStatus.Unavailable(SpeakerUnavailable.NO_ENGLISH)
                    }

                    is SpeechAvailability.Ready -> {
                        answer.session
                            ?.also { session = it }
                            ?.let { SpeakerStatus.Ready(answer.availability.enginePackage) }
                            ?: SpeakerStatus.Unavailable(SpeakerUnavailable.DID_NOT_START)
                    }
                }
            mutableStatus.value = next
            mutableCannotSpeak.value = next is SpeakerStatus.Unavailable
        }
    }

    private fun speak(
        text: String,
        label: String,
    ) {
        val open = session
        when {
            open == null || mutableStatus.value !is SpeakerStatus.Ready -> {
                record(label, PromptOutcome.NOT_READY)
            }

            !holdFocus() -> {
                record(label, PromptOutcome.NO_SOUND)
            }

            else -> {
                val sequence = ++promptCount
                val accepted =
                    runCatching { open.speak(text) { played -> commands.trySend(Command.Ended(sequence, played)) } }
                        .onFailure { Log.w(TAG, "voice engine failed: ${it.javaClass.simpleName}") }
                        .getOrDefault(false)
                if (accepted) {
                    // The new prompt takes over the hold, and its bound, only once the engine has taken it. Its end
                    // cannot be handled before this returns: it queues behind this command.
                    playing = sequence
                    mutableLastPrompt.value = SpokenPrompt(label, outcome = null)
                    playingBound?.cancel()
                    playingBound =
                        scope.launch {
                            delay(PROMPT_MAX_MS)
                            commands.trySend(Command.Ended(sequence, played = false))
                        }
                } else {
                    // An earlier prompt may still be speaking: its hold stays as it was, and the sound goes back
                    // only when nothing else holds it.
                    if (playing == null) releaseFocus()
                    record(label, PromptOutcome.ENGINE_REFUSED)
                }
            }
        }
    }

    // A prompt replaced by a newer one finds [playing] moved on and changes nothing: the newer one holds the sound now.
    private fun finishPrompt(
        sequence: Int,
        played: Boolean,
    ) {
        if (playing == sequence) {
            playing = null
            playingBound?.cancel()
            releaseFocus()
            endLastPrompt(if (played) PromptOutcome.PLAYED else PromptOutcome.CUT_SHORT)
        }
    }

    // Give the last prompt its [outcome] while it still shows as playing; one that was refused or dropped since keeps
    // its own.
    private fun endLastPrompt(outcome: PromptOutcome) {
        mutableLastPrompt.update { last -> last?.takeIf { it.outcome == null }?.copy(outcome = outcome) ?: last }
    }

    private fun shutDownUnlessClosed() {
        if (mutableStatus.value != SpeakerStatus.Closed) shutDown()
    }

    private fun shutDown() {
        generation++
        opening?.cancel()
        opening = null
        session?.let { open -> runCatching { open.close() } }
        session = null
        if (playing != null) endLastPrompt(PromptOutcome.CUT_SHORT)
        playing = null
        playingBound?.cancel()
        playingBound = null
        releaseFocus()
        mutableStatus.value = SpeakerStatus.Closed
    }

    // Return whether the sound is held, asking for it unless a playing prompt already holds it.
    private fun holdFocus(): Boolean =
        focusHeld || runCatching { focus.request() }.getOrDefault(false).also { focusHeld = it }

    private fun releaseFocus() {
        if (focusHeld) {
            focusHeld = false
            runCatching { focus.release() }
        }
    }

    private fun record(
        label: String,
        outcome: PromptOutcome,
    ) {
        mutableLastPrompt.value = SpokenPrompt(label, outcome)
    }

    companion object {
        @Volatile
        private var instance: TurnSpeaker? = null

        fun get(context: Context): TurnSpeaker =
            instance ?: synchronized(this) {
                instance ?: context.applicationContext
                    .let { app ->
                        TurnSpeaker(
                            engines = PlatformSpeechEnginePort(app),
                            sessions = PlatformSpeechSessions(app),
                            focus = PlatformSoundFocus(app),
                            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                        )
                    }.also { instance = it }
            }
    }
}
