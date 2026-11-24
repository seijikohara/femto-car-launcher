package io.github.seijikohara.femto.data.music

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.media.MediaBrowserService
import android.util.Log
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "PlatformResumePort"

/**
 * The [ResumePort] over the platform: the package manager, the media browser and session APIs, and the audio
 * manager. Its calls run on the main thread, where [MediaBrowser] and [MediaController] deliver their callbacks.
 * Every log names the app's package only, never a track.
 */
internal class PlatformResumePort(
    private val context: Context,
    private val apps: MediaApps = PackageManagerMediaApps(context),
) : ResumePort {
    private val sessionManager: MediaSessionManager = checkNotNull(context.getSystemService())
    private val audioManager: AudioManager = checkNotNull(context.getSystemService())
    private val listenerComponent = ComponentName(context, MusicSessionListenerService::class.java)

    override fun isLaunchable(packageName: String): Boolean = apps.isLaunchable(packageName)

    /**
     * Watch every active session through the notification-listener grant, as the music card does, with a callback on
     * each so a state change inside a session is heard as well as a session joining or leaving: the remembered app's
     * sessions for its progress, the others for another app starting to play. The flow fails when the sessions cannot
     * be read, and when the grant is revoked while it runs: the platform then stops telling the launcher about
     * sessions, so PLAYING could never be seen.
     */
    override fun sessions(packageName: String): Flow<ResumeWatch> =
        callbackFlow {
            var watched: Map<MediaSession.Token, MediaController> = emptyMap()

            fun publish() {
                trySend(
                    resumeWatchOf(
                        packageName,
                        watched.values.map { it.packageName to runCatching { it.playbackState?.state }.getOrNull() },
                    ),
                )
            }

            val callback =
                object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                }

            fun rewatch(sessions: List<MediaController>) {
                val update = reconcileWatchSet(watched, sessions) { it.sessionToken }
                update.removed.forEach { runCatching { it.unregisterCallback(callback) } }
                update.added.forEach { runCatching { it.registerCallback(callback) } }
                watched = update.watched
                publish()
            }

            val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { rewatch(it.orEmpty()) }
            val accessObserver =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        context
                            .takeUnless(::hasNotificationListenerAccess)
                            ?.let { close(SecurityException("notification access revoked")) }
                    }
                }

            fun release() {
                context.contentResolver.unregisterContentObserver(accessObserver)
                runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
                watched.values.forEach { runCatching { it.unregisterCallback(callback) } }
                watched = emptyMap()
            }

            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(ENABLED_NOTIFICATION_LISTENERS),
                false,
                accessObserver,
            )
            // Both calls throw without the grant; the flow then fails, and the attempt ends.
            runCatching {
                rewatch(sessionManager.getActiveSessions(listenerComponent))
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            }.onFailure { error ->
                release()
                throw error
            }
            awaitClose(::release)
        }.flowOn(Dispatchers.Main.immediate)

    override fun connectBrowser(
        packageName: String,
        onLost: () -> Unit,
    ): BrowserConnection? =
        browserServiceOrNull(packageName)?.let { service ->
            val callback = ResumeCallback(packageName, onLost)
            // The "recent" root hint is what Android's own resumption sends: it asks
            // the service for the item it played last, which prepare() then loads.
            val hints = Bundle().apply { putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) }
            runCatching {
                MediaBrowser(context, service, callback, hints).also { browser ->
                    callback.browser = browser
                    browser.connect()
                }
            }.onFailure { Log.w(TAG, "media browser connect failed for $packageName", it) }
                .getOrNull()
                ?.let { browser -> BrowserConnection { runCatching { browser.disconnect() } } }
        }

    override fun dispatchPlayMediaKey() {
        runCatching { audioManager.dispatchPlayMediaKey() }.onFailure { Log.w(TAG, "play media key failed", it) }
    }

    // The top importance, which the launcher's process holds while it has a
    // visible or resumed activity: a translucent activity of another app over
    // the dashboard still counts as in front. A process kept alive only by the
    // trip's foreground service, or held behind another app as the home
    // process or a cached one, does not.
    override fun isForeground(): Boolean =
        ActivityManager
            .RunningAppProcessInfo()
            .also(ActivityManager::getMyMemoryState)
            .importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    // An app without a launch intent is the one "false". A start that fails
    // otherwise still counts as opened, so the attempt ends without forgetting
    // a track the app can play. The chain asks isForeground() first: the
    // launcher, as the home app, may start activities from the background.
    override fun openApp(packageName: String): Boolean =
        runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }
            .getOrNull()
            ?.let { launch ->
                runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Log.w(TAG, "opening $packageName failed", it) }
                true
            } ?: false

    // The app's media browser service, found the way Android's own resumption
    // finds it; the <queries> entry in the manifest makes it visible.
    private fun browserServiceOrNull(packageName: String): ComponentName? =
        runCatching {
            context.packageManager
                .queryIntentServices(
                    Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName),
                    PackageManager.ResolveInfoFlags.of(0),
                ).firstOrNull()
                ?.serviceInfo
                ?.let { ComponentName(it.packageName, it.name) }
        }.getOrNull()

    /**
     * Once connected, ask the session to prepare its recent item and play it, in that order, as Android's own
     * resumption does. A refused or failed connection, a suspended one (the service died), and a request that
     * fails all report [onLost].
     */
    private inner class ResumeCallback(
        private val packageName: String,
        private val onLost: () -> Unit,
    ) : MediaBrowser.ConnectionCallback() {
        var browser: MediaBrowser? = null

        override fun onConnected() {
            runCatching {
                MediaController(context, checkNotNull(browser).sessionToken).transportControls.run {
                    prepare()
                    play()
                }
            }.onFailure {
                Log.w(TAG, "resume request to $packageName failed", it)
                onLost()
            }
        }

        override fun onConnectionFailed() = onLost()

        override fun onConnectionSuspended() = onLost()
    }
}
