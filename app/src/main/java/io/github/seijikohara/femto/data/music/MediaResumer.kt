package io.github.seijikohara.femto.data.music

import android.media.session.PlaybackState
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "MediaResumer"

/**
 * How long each step of the resume chain waits for the app to show progress (buffering or playing) before the next
 * step. Internal so tests probe both sides of each.
 *
 * The browser step's window covers a cold start after the head unit boots: the app's process, its browser service
 * and session, and the prepare that loads its recent item. The media key reaches an app that is already running (the
 * browser bound it) or one the platform starts through its media button receiver, so it gets less. Opening the app is
 * the last step: its window only tells an app that starts playing on open from one that waits for the user, for the
 * diagnostics record, and keeps the browser bound meanwhile.
 */
internal const val RESUME_BROWSER_WINDOW_MS = 8_000L
internal const val RESUME_MEDIA_KEY_WINDOW_MS = 5_000L
internal const val RESUME_OPEN_APP_WINDOW_MS = 5_000L

/**
 * How long a step keeps waiting for PLAYING once the app buffers. A streaming app just after boot buffers while the
 * network comes up, which on a head unit's tethered or cellular link can take tens of seconds. Bounded, so the chain
 * always ends: with no progress at all it ends within the three windows (18 s); buffering at every step without ever
 * playing, within the windows plus three of these (78 s).
 */
internal const val RESUME_BUFFERING_LIMIT_MS = 20_000L

/** How far a remembered app's sessions have got while a resume attempt watches them; a later entry is further. */
internal enum class SessionPlayback { IDLE, BUFFERING, PLAYING }

/**
 * Return the furthest of [states], the [PlaybackState] codes of one app's active sessions (null for a session that
 * reports none). Only PLAYING is success; BUFFERING and CONNECTING are progress toward it. An active session that is
 * stopped, paused or has no state is not, which is what binding an app's browser service usually creates at once.
 */
internal fun sessionPlaybackOf(states: List<Int?>): SessionPlayback =
    states.maxOfOrNull { state ->
        when (state) {
            PlaybackState.STATE_PLAYING -> SessionPlayback.PLAYING
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> SessionPlayback.BUFFERING
            else -> SessionPlayback.IDLE
        }
    } ?: SessionPlayback.IDLE

/** The step of the resume chain that started playback. */
internal enum class ResumeStep { BROWSER, MEDIA_KEY, OPEN_APP }

/** Why no step started playback. */
internal enum class NotStartedReason {
    /** Every step ran out of time without the app playing. */
    NO_PLAYBACK,

    /** The app is uninstalled or has no launch intent; the remembered track is forgotten. */
    APP_GONE,

    /** The app's sessions could no longer be read (notification access revoked), so playback could not be seen. */
    SESSIONS_UNREADABLE,

    /** The attempt was cancelled, with the dashboard that started it. */
    INTERRUPTED,
}

/** How a resume attempt for [packageName] ended. Names the app and the step, never the track. */
internal sealed interface ResumeOutcome {
    val packageName: String

    data class Started(
        override val packageName: String,
        val step: ResumeStep,
    ) : ResumeOutcome

    data class NotStarted(
        override val packageName: String,
        val reason: NotStartedReason,
    ) : ResumeOutcome
}

/** A connection to an app's media browser service, held until [disconnect]. */
internal fun interface BrowserConnection {
    fun disconnect()
}

/**
 * The platform half of the resume chain, behind one port so the chain runs on the JVM with a fake.
 * [PlatformResumePort] is the production implementation. Called on the main thread.
 */
internal interface ResumePort {
    /** Return whether [packageName] is installed and has a launch intent. */
    fun isLaunchable(packageName: String): Boolean

    /**
     * Follow how far [packageName]'s active sessions have got. The flow fails once they can no longer be read, as
     * when notification access is revoked.
     */
    fun playback(packageName: String): Flow<SessionPlayback>

    /**
     * Connect to [packageName]'s media browser service and, once connected, ask its session to prepare and play its
     * most recent item; or return null when the app has no such service or the connection cannot start. [onLost]
     * runs when the connection fails or is suspended (the service refused it or died), or the request fails.
     */
    fun connectBrowser(
        packageName: String,
        onLost: () -> Unit,
    ): BrowserConnection?

    /** Press the play media key. */
    fun dispatchPlayMediaKey()

    /** Start [packageName]'s launch activity; return false when the app has none. */
    fun openApp(packageName: String): Boolean
}

/**
 * Process-wide record of the last resume attempt, which the MUSIC diagnostics section reads when it is collected. It
 * names the app and the step, never the track. Session-scoped by design, like the tile benchmark's record.
 */
internal object ResumeAttempts {
    private val latest = AtomicReference<ResumeOutcome?>(null)

    fun record(outcome: ResumeOutcome) = latest.set(outcome)

    fun latestOrNull(): ResumeOutcome? = latest.get()
}

/**
 * Asks a remembered app to resume its track, the way Android's own media controls resume one after a restart, with
 * two fallbacks. One step at a time, each reached only when the step before shows no progress in time:
 *  1. Browser: connect to the app's media browser service with the "recent" root hint, and ask its session to
 *     prepare and play ([ResumePort.connectBrowser]).
 *  2. Media key: when the app has no browser service, the connection fails or is suspended, or step 1 shows no
 *     progress within [RESUME_BROWSER_WINDOW_MS].
 *  3. Open the app: when step 2 shows no progress within [RESUME_MEDIA_KEY_WINDOW_MS].
 *
 * Success is the app's own session reporting PLAYING, never merely a session existing: binding a browser service
 * usually creates an idle session at once. A session that buffers keeps its step waiting, up to
 * [RESUME_BUFFERING_LIMIT_MS].
 *
 * The browser connection stays up until the app plays or the chain ends, on every path including cancellation: a
 * service that is only bound is destroyed when it is unbound, and playback started through it would die with it.
 *
 * The caller runs one attempt at a time. Each outcome goes to [record]; an app found gone is passed to [forget].
 */
internal class MediaResumer(
    private val port: ResumePort,
    private val forget: suspend (packageName: String) -> Unit,
    private val record: (ResumeOutcome) -> Unit = ResumeAttempts::record,
) {
    /** Try to resume [packageName]'s recent track and return how the attempt ended. Call on the main thread. */
    suspend fun resume(packageName: String): ResumeOutcome =
        try {
            attempt(packageName)
        } catch (e: CancellationException) {
            record(ResumeOutcome.NotStarted(packageName, NotStartedReason.INTERRUPTED))
            throw e
        }.also { outcome ->
            record(outcome)
            if ((outcome as? ResumeOutcome.NotStarted)?.reason == NotStartedReason.APP_GONE) forget(packageName)
        }

    private suspend fun attempt(packageName: String): ResumeOutcome =
        if (port.isLaunchable(packageName)) {
            watching(packageName) { progress -> steps(packageName, progress) }
        } else {
            ResumeOutcome.NotStarted(packageName, NotStartedReason.APP_GONE)
        }

    /**
     * Run [steps] while a child coroutine follows the app's sessions into the progress they read. A watch that fails
     * ends the attempt: success could no longer be seen.
     */
    private suspend fun watching(
        packageName: String,
        steps: suspend (StateFlow<SessionPlayback>) -> ResumeOutcome,
    ): ResumeOutcome {
        val progress = MutableStateFlow(SessionPlayback.IDLE)
        return try {
            coroutineScope {
                val watch = launch { port.playback(packageName).collect { progress.value = it } }
                steps(progress).also { watch.cancel() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "session watch for $packageName failed; ending the attempt", e)
            ResumeOutcome.NotStarted(packageName, NotStartedReason.SESSIONS_UNREADABLE)
        }
    }

    private suspend fun steps(
        packageName: String,
        progress: StateFlow<SessionPlayback>,
    ): ResumeOutcome {
        val browserLost = MutableStateFlow(false)
        val browser = port.connectBrowser(packageName) { browserLost.value = true }
        var bound = browser != null

        // Disconnects exactly once, whichever path gets here first.
        fun release() {
            if (bound) {
                bound = false
                browser?.disconnect()
            }
        }

        return try {
            if (browser != null && progress.playsWithin(RESUME_BROWSER_WINDOW_MS, abandon = browserLost)) {
                ResumeOutcome.Started(packageName, ResumeStep.BROWSER)
            } else {
                // A failed or suspended connection is dropped at once, as Android's own controls drop it. A silent one
                // stays bound: the session it created may still answer the media key, and unbinding would destroy it.
                if (browserLost.value) release()
                port.dispatchPlayMediaKey()
                when {
                    progress.playsWithin(RESUME_MEDIA_KEY_WINDOW_MS) -> {
                        ResumeOutcome.Started(packageName, ResumeStep.MEDIA_KEY)
                    }

                    !port.openApp(packageName) -> {
                        ResumeOutcome.NotStarted(packageName, NotStartedReason.APP_GONE)
                    }

                    progress.playsWithin(RESUME_OPEN_APP_WINDOW_MS) -> {
                        ResumeOutcome.Started(packageName, ResumeStep.OPEN_APP)
                    }

                    else -> {
                        ResumeOutcome.NotStarted(packageName, NotStartedReason.NO_PLAYBACK)
                    }
                }
            }
        } finally {
            release()
        }
    }
}

// Never turns true: the abandon signal of a step with no connection to lose.
private val NeverAbandoned: StateFlow<Boolean> = MutableStateFlow(false)

/**
 * Wait up to [window] for the app to show progress and, once it buffers, up to [RESUME_BUFFERING_LIMIT_MS] for it to
 * play. Return true once it plays; false when it shows no progress in time, stops buffering without playing (a prepare
 * that ended paused wants the media key), or [abandon] turns true first.
 */
private suspend fun StateFlow<SessionPlayback>.playsWithin(
    window: Long,
    abandon: StateFlow<Boolean> = NeverAbandoned,
): Boolean {
    // Null once abandoned, which no wait below accepts as progress.
    val readings = combine(this, abandon) { playback, abandoned -> playback.takeUnless { abandoned } }
    return when (withTimeoutOrNull(window) { readings.first { it != SessionPlayback.IDLE } }) {
        SessionPlayback.PLAYING -> {
            true
        }

        SessionPlayback.BUFFERING -> {
            withTimeoutOrNull(RESUME_BUFFERING_LIMIT_MS) { readings.first { it != SessionPlayback.BUFFERING } } ==
                SessionPlayback.PLAYING
        }

        else -> {
            false
        }
    }
}
