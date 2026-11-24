package io.github.seijikohara.femto.data.music

import android.media.session.PlaybackState
import android.os.SystemClock
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
 * and session, and the prepare that loads its recent item. The media key's window is the longest: the app it reaches
 * is often one whose process the browser bind has only just started (an app that refuses the browser still starts that
 * way), and that app still has its own start-up and the load of its track ahead. On an emulator on a fast host it took
 * about 3 s from the key to PLAYING; a slow head unit just after boot takes several times that, and a shorter window
 * would open the app over the screen just before it plays. Opening the app is the last step: its window only tells an
 * app that starts playing on open from one that waits for the user, for the diagnostics record, and keeps the browser
 * bound meanwhile.
 */
internal const val RESUME_BROWSER_WINDOW_MS = 8_000L
internal const val RESUME_MEDIA_KEY_WINDOW_MS = 12_000L
internal const val RESUME_OPEN_APP_WINDOW_MS = 5_000L

/**
 * How long a step keeps waiting for PLAYING once the app buffers. A streaming app just after boot buffers while the
 * network comes up, which on a head unit's tethered or cellular link can take tens of seconds. Bounded, so the chain
 * always ends: with no progress at all it ends within the three windows (25 s); buffering at every step without ever
 * playing, within the windows plus three of these (85 s).
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

/**
 * What a resume attempt sees of the active sessions: how far the remembered app's own have got ([target]), and
 * whether a session of another app is PLAYING ([otherPlaying]).
 */
internal data class ResumeWatch(
    val target: SessionPlayback = SessionPlayback.IDLE,
    val otherPlaying: Boolean = false,
)

/**
 * Read the active [sessions] (package name to [PlaybackState] code) as a resume attempt for [packageName] sees them.
 * Another app's session counts only once it plays: an idle or paused one is no reason to stop the attempt.
 */
internal fun resumeWatchOf(
    packageName: String,
    sessions: List<Pair<String, Int?>>,
): ResumeWatch =
    sessions.partition { (sessionPackage, _) -> sessionPackage == packageName }.let { (own, others) ->
        ResumeWatch(
            target = sessionPlaybackOf(own.map { (_, state) -> state }),
            otherPlaying = others.any { (_, state) -> state == PlaybackState.STATE_PLAYING },
        )
    }

/** The step of the resume chain that started playback. */
internal enum class ResumeStep { BROWSER, MEDIA_KEY, OPEN_APP }

/** Why no step started playback. */
internal enum class NotStartedReason {
    /** Every step ran out of time without the app playing. */
    NO_PLAYBACK,

    /** The app is uninstalled or has no launch intent; the remembered track is forgotten. */
    APP_GONE,

    /**
     * Another app started playing during the attempt (the media key can reach whichever app the platform routes it
     * to). The attempt stops there, so the remembered app is not opened over it.
     */
    OTHER_APP_PLAYING,

    /** The dashboard was no longer in front when the last step would have opened the app, so it was not opened. */
    LEFT_DASHBOARD,

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
     * Follow the active sessions as an attempt for [packageName] reads them (see [resumeWatchOf]). The flow fails once
     * they can no longer be read, as when notification access is revoked.
     */
    fun sessions(packageName: String): Flow<ResumeWatch>

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

    /** Return whether the launcher is in front, so opening another app over the screen is the driver's own doing. */
    fun isForeground(): Boolean

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
 *  3. Open the app: when step 2 shows no progress within [RESUME_MEDIA_KEY_WINDOW_MS], and only while the launcher is
 *     still in front. The chain outlives the dashboard (a bound-only service dies with its binding, so leaving must
 *     not drop it mid-buffer), and the launcher as the home app may start activities from the background: without
 *     this check, the app would open over whatever the driver went to meanwhile.
 *
 * Success is the app's own session reporting PLAYING, never merely a session existing: binding a browser service
 * usually creates an idle session at once. A session that buffers keeps its step waiting, up to
 * [RESUME_BUFFERING_LIMIT_MS]. Another app starting to play ends the attempt at any step: music is playing, and the
 * remembered app must not be woken or opened over it.
 *
 * The browser connection stays up until the app plays or the chain ends, on every path including cancellation: a
 * service that is only bound is destroyed when it is unbound, and playback started through it would die with it.
 *
 * The caller runs one attempt at a time. Each outcome goes to [record]; an app found gone is passed to [forget]. Each
 * step is logged with the app's package and the time since the tap, never the track, so a log excerpt shows the path.
 */
internal class MediaResumer(
    private val port: ResumePort,
    private val forget: suspend (packageName: String) -> Unit,
    private val record: (ResumeOutcome) -> Unit = ResumeAttempts::record,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) {
    /** Try to resume [packageName]'s recent track and return how the attempt ended. Call on the main thread. */
    suspend fun resume(packageName: String): ResumeOutcome {
        val startedAtMs = elapsedRealtimeMs()
        return try {
            attempt(packageName, startedAtMs)
        } catch (e: CancellationException) {
            finish(ResumeOutcome.NotStarted(packageName, NotStartedReason.INTERRUPTED), startedAtMs)
            throw e
        }.also { outcome ->
            finish(outcome, startedAtMs)
            if ((outcome as? ResumeOutcome.NotStarted)?.reason == NotStartedReason.APP_GONE) forget(packageName)
        }
    }

    private fun finish(
        outcome: ResumeOutcome,
        startedAtMs: Long,
    ) {
        Log.i(TAG, "resume ${outcome.packageName}: $outcome after ${elapsedRealtimeMs() - startedAtMs} ms")
        record(outcome)
    }

    private suspend fun attempt(
        packageName: String,
        startedAtMs: Long,
    ): ResumeOutcome =
        if (port.isLaunchable(packageName)) {
            watching(packageName) { progress -> Attempt(packageName, progress, startedAtMs).run() }
        } else {
            ResumeOutcome.NotStarted(packageName, NotStartedReason.APP_GONE)
        }

    /**
     * Run [steps] while a child coroutine follows the sessions into the reading they see. A watch that fails ends the
     * attempt: success could no longer be seen.
     */
    private suspend fun watching(
        packageName: String,
        steps: suspend (StateFlow<ResumeWatch>) -> ResumeOutcome,
    ): ResumeOutcome {
        val progress = MutableStateFlow(ResumeWatch())
        return try {
            coroutineScope {
                val watch = launch { port.sessions(packageName).collect { progress.value = it } }
                steps(progress).also { watch.cancel() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "session watch for $packageName failed; ending the attempt", e)
            ResumeOutcome.NotStarted(packageName, NotStartedReason.SESSIONS_UNREADABLE)
        }
    }

    /** One attempt's steps, holding its browser connection until [run] returns or is cancelled. */
    private inner class Attempt(
        private val packageName: String,
        private val progress: StateFlow<ResumeWatch>,
        private val startedAtMs: Long,
    ) {
        private val browserLost = MutableStateFlow(false)
        private var browser: BrowserConnection? = null

        suspend fun run(): ResumeOutcome =
            try {
                browserStep()
            } finally {
                release()
            }

        private suspend fun browserStep(): ResumeOutcome {
            browser = port.connectBrowser(packageName) { browserLost.value = true }
            logStep(if (browser != null) "media browser" else "no media browser service")
            return when (browser?.let { progress.stepEnd(RESUME_BROWSER_WINDOW_MS, abandon = browserLost) }) {
                StepEnd.PLAYED -> {
                    started(ResumeStep.BROWSER)
                }

                StepEnd.OTHER_PLAYING -> {
                    notStarted(NotStartedReason.OTHER_APP_PLAYING)
                }

                StepEnd.NO_PROGRESS, null -> {
                    // A failed or suspended connection is dropped at once, as Android's own controls drop it. A silent
                    // one stays bound: the session it created may still answer the media key, and unbinding would
                    // destroy it.
                    if (browserLost.value) release()
                    mediaKeyStep()
                }
            }
        }

        private suspend fun mediaKeyStep(): ResumeOutcome {
            logStep("play media key")
            port.dispatchPlayMediaKey()
            return when (progress.stepEnd(RESUME_MEDIA_KEY_WINDOW_MS)) {
                StepEnd.PLAYED -> started(ResumeStep.MEDIA_KEY)
                StepEnd.OTHER_PLAYING -> notStarted(NotStartedReason.OTHER_APP_PLAYING)
                StepEnd.NO_PROGRESS -> openAppStep()
            }
        }

        private suspend fun openAppStep(): ResumeOutcome =
            when {
                !port.isForeground() -> {
                    logStep("dashboard left; not opening the app")
                    notStarted(NotStartedReason.LEFT_DASHBOARD)
                }

                !port.openApp(packageName) -> {
                    notStarted(NotStartedReason.APP_GONE)
                }

                else -> {
                    logStep("app opened")
                    when (progress.stepEnd(RESUME_OPEN_APP_WINDOW_MS)) {
                        StepEnd.PLAYED -> started(ResumeStep.OPEN_APP)
                        StepEnd.OTHER_PLAYING -> notStarted(NotStartedReason.OTHER_APP_PLAYING)
                        StepEnd.NO_PROGRESS -> notStarted(NotStartedReason.NO_PLAYBACK)
                    }
                }
            }

        // Disconnects at most once, whichever path gets here first.
        private fun release() {
            browser?.disconnect()
            browser = null
        }

        private fun started(step: ResumeStep) = ResumeOutcome.Started(packageName, step)

        private fun notStarted(reason: NotStartedReason) = ResumeOutcome.NotStarted(packageName, reason)

        private fun logStep(step: String) =
            Log.i(TAG, "resume $packageName: $step at ${elapsedRealtimeMs() - startedAtMs} ms")
    }
}

/** How one step's wait ended. */
private enum class StepEnd { PLAYED, OTHER_PLAYING, NO_PROGRESS }

// Never turns true: the abandon signal of a step with no connection to lose.
private val NeverAbandoned: StateFlow<Boolean> = MutableStateFlow(false)

/**
 * Wait up to [window] for the remembered app to show progress and, once it buffers, up to [RESUME_BUFFERING_LIMIT_MS]
 * for it to play. [StepEnd.PLAYED] once it plays; [StepEnd.OTHER_PLAYING] once another app plays first; else
 * [StepEnd.NO_PROGRESS]: no progress in time, buffering that stopped without playing (a prepare that ended paused
 * wants the media key), or [abandon] turned true.
 */
private suspend fun StateFlow<ResumeWatch>.stepEnd(
    window: Long,
    abandon: StateFlow<Boolean> = NeverAbandoned,
): StepEnd {
    // The remembered app playing outranks everything else in the same reading.
    val ends =
        combine(this, abandon) { watch, abandoned ->
            when {
                watch.target == SessionPlayback.PLAYING -> StepReading.Ended(StepEnd.PLAYED)
                watch.otherPlaying -> StepReading.Ended(StepEnd.OTHER_PLAYING)
                abandoned -> StepReading.Ended(StepEnd.NO_PROGRESS)
                watch.target == SessionPlayback.BUFFERING -> StepReading.Buffering
                else -> StepReading.Idle
            }
        }
    return when (val first = withTimeoutOrNull(window) { ends.first { it != StepReading.Idle } }) {
        is StepReading.Ended -> {
            first.end
        }

        StepReading.Buffering -> {
            val settled = withTimeoutOrNull(RESUME_BUFFERING_LIMIT_MS) { ends.first { it != StepReading.Buffering } }
            (settled as? StepReading.Ended)?.end ?: StepEnd.NO_PROGRESS
        }

        else -> {
            StepEnd.NO_PROGRESS
        }
    }
}

/** One reading during a step's wait. */
private sealed interface StepReading {
    data object Idle : StepReading

    data object Buffering : StepReading

    data class Ended(
        val end: StepEnd,
    ) : StepReading
}
