package io.github.seijikohara.femto.data.voice

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

// A cold engine starts its process and sets itself up before it answers: 3.4 s on the emulator, and a head unit is
// slower. Past this, the engine counts as silent: the diagnostics section that asks must not wait on a broken engine
// for good, and a working engine must not read as a silent one.
internal const val ENGINE_ANSWER_TIMEOUT_MS = 10_000L

/** What one text-to-speech engine said when asked for an English voice. */
internal enum class EnglishVoice {
    AVAILABLE,

    /** The engine speaks English, but its voice data is not on the device. */
    MISSING_DATA,
    NOT_SUPPORTED,

    /** The engine reported an error, or failed, instead of starting. */
    DID_NOT_START,

    /** The engine never reported back within the wait. */
    NO_ANSWER,
}

/** One text-to-speech engine on the device, named by its package as the platform names engines. */
internal data class SpeechEngine(
    val packageName: String,
    /** The engine the platform speaks through when an app names none. */
    val isDefault: Boolean,
    val english: EnglishVoice,
)

/**
 * The device's text-to-speech engines as [speechEngines] needs them. [PlatformSpeechEnginePort] is the platform
 * implementation; tests substitute a fake, because Robolectric has no engine to ask.
 */
internal interface SpeechEnginePort {
    fun installed(): List<String>

    fun defaultOrNull(): String?

    /** Start [packageName] and ask it for an English voice. Suspends until it answers, which a broken engine never does. */
    suspend fun english(packageName: String): EnglishVoice
}

/**
 * Ask every installed engine for an English voice, all at once, so one silent engine costs a single wait. A device
 * with no engine returns an empty list without starting anything.
 *
 * Each engine is started for the question, a cold one with its whole process, so this belongs behind a request of the
 * user's (the diagnostics screen) and never on a start-up path.
 */
internal suspend fun speechEngines(
    port: SpeechEnginePort,
    answerTimeoutMs: Long = ENGINE_ANSWER_TIMEOUT_MS,
): List<SpeechEngine> =
    coroutineScope {
        val default = port.defaultOrNull()
        port
            .installed()
            .map { packageName ->
                async {
                    SpeechEngine(
                        packageName = packageName,
                        isDefault = packageName == default,
                        english =
                            withTimeoutOrNull(answerTimeoutMs) { port.englishOrDidNotStart(packageName) }
                                ?: EnglishVoice.NO_ANSWER,
                    )
                }
            }.awaitAll()
    }

// An engine that throws is that engine's answer. Left to travel, the failure would cancel the questions to the other
// engines and take down the whole diagnostics section that asked.
private suspend fun SpeechEnginePort.englishOrDidNotStart(packageName: String): EnglishVoice =
    try {
        english(packageName)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        EnglishVoice.DID_NOT_START
    }

/** One installed text-to-speech service, reduced to what the platform ranks engines by. */
internal data class SpeechService(
    val packageName: String,
    /** Part of the system image. */
    val isSystem: Boolean,
    val priority: Int,
)

/**
 * The engine the platform speaks through when an app names none, by the platform's own rule
 * (`TtsEngines.getDefaultEngine`): the engine [picked] in the system settings while it is installed, otherwise the
 * highest-priority engine of the system image, and none when the image has no engine. `TextToSpeech.getDefaultEngine`
 * gives the same answer but needs an instance, which starts an engine only to be asked this.
 */
internal fun defaultEngineOrNull(
    services: List<SpeechService>,
    picked: String?,
): String? =
    picked?.takeIf { name -> services.any { it.packageName == name } }
        ?: services.filter { it.isSystem }.maxByOrNull { it.priority }?.packageName

/** Read a `TextToSpeech.isLanguageAvailable` result: every code from "available" up names a closer match. */
internal fun englishVoiceOf(languageAvailability: Int): EnglishVoice =
    when {
        languageAvailability >= TextToSpeech.LANG_AVAILABLE -> EnglishVoice.AVAILABLE
        languageAvailability == TextToSpeech.LANG_MISSING_DATA -> EnglishVoice.MISSING_DATA
        else -> EnglishVoice.NOT_SUPPORTED
    }

/**
 * The [SpeechEnginePort] over the platform. The engines are visible through the manifest's `<queries>` entry for the
 * text-to-speech service; without it the list is empty on Android 11 and later.
 */
internal class PlatformSpeechEnginePort(
    private val context: Context,
) : SpeechEnginePort {
    override fun installed(): List<String> = services().map { it.packageName }.distinct()

    override fun defaultOrNull(): String? =
        defaultEngineOrNull(
            services = services(),
            picked = Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH),
        )

    override suspend fun english(packageName: String): EnglishVoice {
        val started = CompletableDeferred<Int>()
        // The listener can run inside the constructor, before the instance exists, so it only records the status.
        // Where the named engine cannot be bound, the platform connects its fallback engine instead and the answer
        // is that engine's: the public constructor has no way to switch the fallback off.
        val engine = TextToSpeech(context, { status -> started.complete(status) }, packageName)
        return try {
            when {
                started.await() != TextToSpeech.SUCCESS -> EnglishVoice.DID_NOT_START
                else -> englishVoiceOf(withContext(Dispatchers.IO) { engine.isLanguageAvailable(Locale.ENGLISH) })
            }
        } finally {
            // A shutdown has no effect on an engine that is still connecting, which would then stay bound once it
            // has connected. So the shutdown follows the engine's answer: at once for one that has answered, and
            // whenever the answer comes for one the wait ran out on.
            started.invokeOnCompletion { engine.shutdown() }
        }
    }

    // The same query the platform lists engines with, so this list is the one TextToSpeech would connect to.
    private fun services(): List<SpeechService> =
        context.packageManager
            .queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            ).map { resolved ->
                SpeechService(
                    packageName = resolved.serviceInfo.packageName,
                    isSystem = resolved.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    priority = resolved.priority,
                )
            }
}
