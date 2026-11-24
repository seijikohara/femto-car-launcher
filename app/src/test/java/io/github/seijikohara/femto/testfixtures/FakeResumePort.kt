package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.music.BrowserConnection
import io.github.seijikohara.femto.data.music.ResumePort
import io.github.seijikohara.femto.data.music.ResumeWatch
import io.github.seijikohara.femto.data.music.SessionPlayback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Scriptable [ResumePort]: a test calls [play] to move the remembered app's sessions, [otherAppPlays] to start
 * another app, [loseBrowser] or [failWatch] to break the connection or the session watch, and reads [calls] to see
 * which steps the chain took, in order ("browser", "disconnect", "media key", "open app").
 *
 * [hasBrowser] false plays an app without a media browser service; [launchable] false an uninstalled one; [opensApp]
 * false one that lost its launch intent after the attempt started; [foreground] false a launcher the driver left.
 */
internal class FakeResumePort(
    var launchable: Boolean = true,
    var hasBrowser: Boolean = true,
    var opensApp: Boolean = true,
    var foreground: Boolean = true,
) : ResumePort {
    private val watch = MutableStateFlow(ResumeWatch())
    val calls = mutableListOf<String>()

    /** Whether a connection is open: connected and not yet disconnected. */
    var connected = false
        private set

    private var onBrowserLost: (() -> Unit)? = null
    private val watchFailure = CompletableDeferred<Throwable>()

    /** Move the remembered app's sessions to [playback]. */
    fun play(playback: SessionPlayback) = watch.update { it.copy(target = playback) }

    /** Start a session of another app playing. */
    fun otherAppPlays() = watch.update { it.copy(otherPlaying = true) }

    /** Fail or suspend the browser connection, as a refusing or crashing service does. */
    fun loseBrowser() = checkNotNull(onBrowserLost).invoke()

    /** Fail the session watch, as revoking notification access does. */
    fun failWatch(error: Throwable = SecurityException("notification access revoked")) = watchFailure.complete(error)

    override fun isLaunchable(packageName: String): Boolean = launchable

    override fun sessions(packageName: String): Flow<ResumeWatch> =
        channelFlow {
            launch { watch.collect { send(it) } }
            throw watchFailure.await()
        }

    override fun connectBrowser(
        packageName: String,
        onLost: () -> Unit,
    ): BrowserConnection? =
        if (hasBrowser) {
            calls += "browser"
            connected = true
            onBrowserLost = onLost
            BrowserConnection {
                calls += "disconnect"
                connected = false
            }
        } else {
            null
        }

    override fun dispatchPlayMediaKey() {
        calls += "media key"
    }

    override fun isForeground(): Boolean = foreground

    override fun openApp(packageName: String): Boolean = opensApp.also { calls += "open app" }
}
