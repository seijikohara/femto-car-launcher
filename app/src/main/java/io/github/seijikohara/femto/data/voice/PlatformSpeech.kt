package io.github.seijikohara.femto.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// A prompt plays as navigation guidance: the platform's own usage for it, which volume controls and players that duck
// for a guidance voice recognise. The same attributes go to the engine and to the focus request.
private val GuidanceAttributes: AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

// How long a session start waits for the engine. Nothing waits on an opening, and a cold engine's process starts
// and sets itself up first, which is slow under load: 3.8 s was measured on an emulator while a route computed, so the
// diagnostics probe's 10 s bound for a question is too tight for a start that has no one waiting.
private const val SESSION_START_TIMEOUT_MS = 30_000L

/** The [SpeechSessions] over the platform's `TextToSpeech`. */
internal class PlatformSpeechSessions(
    private val context: Context,
) : SpeechSessions {
    override suspend fun open(enginePackage: String): SpeechSession? {
        val started = CompletableDeferred<Int>()
        // The listener can run inside the constructor, before the instance exists, so it only records the status.
        val engine = TextToSpeech(context, { status -> started.complete(status) }, enginePackage)
        var session: SpeechSession? = null
        try {
            session =
                withTimeoutOrNull(SESSION_START_TIMEOUT_MS) { started.await() }
                    ?.takeIf { it == TextToSpeech.SUCCESS }
                    // The calls below ask the engine's process, which is not for the main thread.
                    ?.let { withContext(Dispatchers.IO) { engine.prepareEnglishOrNull() } }
            return session
        } finally {
            // A shutdown has no effect on an engine that is still connecting, which would then stay bound once it
            // has connected. So an engine that is not handed on is shut down when its answer comes: at once for one
            // that has answered, and whenever it does for one the wait ran out on.
            if (session == null) started.invokeOnCompletion { engine.shutdown() }
        }
    }
}

// The engine set up to speak English as guidance, or null when it has no English voice after all.
private fun TextToSpeech.prepareEnglishOrNull(): SpeechSession? =
    takeIf { setLanguage(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE }?.let { engine ->
        engine.otherOfflineEnglishVoiceOrNull()?.let { engine.setVoice(it) }
        engine.setAudioAttributes(GuidanceAttributes)
        PlatformSpeechSession(engine)
    }

// Whether [this] speaks English without a network and has its data on the device.
private fun Voice.isOfflineEnglish(): Boolean =
    locale.language == Locale.ENGLISH.language &&
        !isNetworkConnectionRequired &&
        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty()

// The voice to switch to, or null to keep the engine's own: setLanguage has just made the engine's choice for English
// current, and a choice that already is an offline English voice stays, since another voice would change the speaker
// for no reason. Otherwise an offline English voice of the current voice's locale, else the first one.
private fun TextToSpeech.otherOfflineEnglishVoiceOrNull(): Voice? =
    voice.let { current ->
        if (current != null && current.isOfflineEnglish()) {
            null
        } else {
            voices.orEmpty().filter { it.isOfflineEnglish() }.let { usable ->
                usable.firstOrNull { it.locale == current?.locale } ?: usable.firstOrNull()
            }
        }
    }

private class PlatformSpeechSession(
    private val engine: TextToSpeech,
) : SpeechSession {
    // The callback of each utterance still to end, by its id; the engine reports from its own threads.
    private val pending = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private val counter = AtomicInteger()

    init {
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = end(utteranceId, played = true)

                // The one-argument form is abstract, so it must be implemented, though the two-argument form below
                // is the one the platform calls.
                @Deprecated("Replaced by the two-argument onError")
                override fun onError(utteranceId: String?) = end(utteranceId, played = false)

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) = end(utteranceId, played = false)

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean,
                ) = end(utteranceId, played = false)
            },
        )
    }

    override fun speak(
        text: String,
        onEnd: (played: Boolean) -> Unit,
    ): Boolean {
        val id = "turn-${counter.incrementAndGet()}"
        pending[id] = onEnd
        return (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS).also { accepted ->
            if (!accepted) pending.remove(id)
        }
    }

    override fun close() {
        pending.clear()
        engine.shutdown()
    }

    // Each utterance ends once: an id that is gone, because the engine reported it twice, is ignored.
    private fun end(
        utteranceId: String?,
        played: Boolean,
    ) {
        utteranceId?.let { pending.remove(it) }?.invoke(played)
    }
}

/** The [SoundFocus] over the platform's audio focus, asked for in the way that lets a player lower its volume. */
internal class PlatformSoundFocus(
    private val context: Context,
) : SoundFocus {
    private val audioManager: AudioManager by lazy { context.getSystemService(AudioManager::class.java) }

    private val request: AudioFocusRequest by lazy {
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(GuidanceAttributes)
            .build()
    }

    override fun request(): Boolean = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun release() {
        audioManager.abandonAudioFocusRequest(request)
    }
}
