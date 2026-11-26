package io.github.seijikohara.femto.data.voice

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

// A cold engine starts its process before it answers, which takes a second or two on a slow box. Past this, the engine
// counts as silent: the diagnostics section that asks must not wait on a broken engine.
private const val ENGINE_ANSWER_TIMEOUT_MS = 5_000L

/** What one text-to-speech engine said when asked for an English voice. */
internal enum class EnglishVoice {
    AVAILABLE,

    /** The engine speaks English, but its voice data is not on the device. */
    MISSING_DATA,
    NOT_SUPPORTED,

    /** The engine reported an error instead of starting. */
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
                            withTimeoutOrNull(answerTimeoutMs) { port.english(packageName) }
                                ?: EnglishVoice.NO_ANSWER,
                    )
                }
            }.awaitAll()
    }

/**
 * The [SpeechEnginePort] over the platform. The engines are visible through the manifest's `<queries>` entry for the
 * text-to-speech service; without it the list is empty on Android 11 and later.
 */
internal class PlatformSpeechEnginePort(
    private val context: Context,
) : SpeechEnginePort {
    override fun installed(): List<String> = services().map { it.serviceInfo.packageName }.distinct()

    // The platform's own choice (TtsEngines.getDefaultEngine): the engine the user picked in the system settings
    // while it is installed, otherwise the highest-priority engine of the system image. TextToSpeech.getDefaultEngine
    // gives the same answer but needs an instance, which starts an engine only to be asked this.
    override fun defaultOrNull(): String? {
        val services = services()
        val picked = Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
        return picked?.takeIf { name -> services.any { it.serviceInfo.packageName == name } }
            ?: services
                .filter { it.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
                .maxByOrNull { it.priority }
                ?.serviceInfo
                ?.packageName
    }

    override suspend fun english(packageName: String): EnglishVoice {
        val started = CompletableDeferred<Int>()
        // The listener can run inside the constructor, before the instance exists, so it only records the status.
        // Where the named engine cannot be bound, the platform connects its fallback engine instead and the answer
        // is that engine's: the public constructor has no way to switch the fallback off.
        val engine = TextToSpeech(context, { status -> started.complete(status) }, packageName)
        return try {
            when {
                started.await() != TextToSpeech.SUCCESS -> EnglishVoice.DID_NOT_START
                else -> withContext(Dispatchers.IO) { engine.isLanguageAvailable(Locale.ENGLISH) }.toEnglishVoice()
            }
        } finally {
            // Also on the way out of a wait that ran out: an engine left bound stays in memory.
            engine.shutdown()
        }
    }

    // The same query the platform lists engines with, so this list is the one TextToSpeech would connect to.
    private fun services(): List<ResolveInfo> =
        context.packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )

    private fun Int.toEnglishVoice(): EnglishVoice =
        when {
            this >= TextToSpeech.LANG_AVAILABLE -> EnglishVoice.AVAILABLE
            this == TextToSpeech.LANG_MISSING_DATA -> EnglishVoice.MISSING_DATA
            else -> EnglishVoice.NOT_SUPPORTED
        }
}
