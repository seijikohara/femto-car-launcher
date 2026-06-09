package io.github.seijikohara.femto.data

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import io.github.seijikohara.femto.ui.home.components.MusicCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal class MusicSessionRepository(
    private val context: Context,
) {
    private val sessionManager: MediaSessionManager = checkNotNull(context.getSystemService())
    private val componentName = ComponentName(context, MusicSessionListenerService::class.java)

    // Source-app icons keyed by package. Resolved once per package: the icon is
    // stable for the process lifetime, while the session flow re-emits on every
    // playback tick. Touched only from the single sequential icon-resolution map
    // (see stateFlow); coroutine dispatch provides the happens-before, so a plain
    // map is safe even though the resolution runs on the Default pool.
    private val sourceIconCache = mutableMapOf<String, ImageBitmap?>()

    fun stateFlow(): Flow<MusicCardState> =
        combine(permissionFlow(), activeControllersFlow()) { hasPermission, controllers ->
            when {
                !hasPermission -> {
                    MusicCardState.NeedsPermission
                }

                else -> {
                    controllers
                        .toNowPlaying()
                        ?.let(MusicCardState::Playing)
                        ?: MusicCardState.NoActiveSession
                }
            }
        }.flowOn(Dispatchers.Main.immediate)
            // Resolve the source-app icon off Main: the PackageManager icon decode
            // is too heavy for the frame thread. The session logic stays on Main
            // (where MediaController callbacks arrive); the icon resolves on
            // Default, cached per package so it runs at most once per source.
            .map { it.withSourceIcon() }
            .flowOn(Dispatchers.Default)

    /**
     * Forward a transport command to the active media session. The
     * call is a no-op when no session is reachable — typically because
     * the user has not granted the notification-listener permission, in
     * which case the dashboard surfaces a "Connect music player" CTA
     * via [io.github.seijikohara.femto.ui.home.HomeAction.ConnectMusicPlayer].
     */
    fun send(command: MusicCommand) {
        val controller =
            runCatching {
                selectPrimaryController(sessionManager.getActiveSessions(componentName))
            }.getOrNull() ?: return
        val transport = controller.transportControls
        when (command) {
            MusicCommand.PlayPause -> {
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    transport.pause()
                } else {
                    transport.play()
                }
            }

            MusicCommand.SkipNext -> {
                transport.skipToNext()
            }

            MusicCommand.SkipPrevious -> {
                transport.skipToPrevious()
            }
        }
    }

    /**
     * Emit `true` while the user has enabled the launcher's notification-listener
     * service in Settings, `false` otherwise. We watch the Secure setting via a
     * ContentObserver so the dashboard reacts to a permission grant returning
     * from `Settings → Notification Access` without an app restart.
     */
    private fun permissionFlow(): Flow<Boolean> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(hasPermission())
                    }
                }
            val uri = Settings.Secure.getUriFor(ENABLED_NOTIFICATION_LISTENERS)
            context.contentResolver.registerContentObserver(uri, false, observer)
            trySend(hasPermission())
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }

    private fun activeControllersFlow(): Flow<List<MediaController>> =
        callbackFlow {
            // The OnActiveSessionsChangedListener only fires when the SET of
            // sessions changes, so on its own the flow is a one-shot snapshot:
            // progress, play/pause, and in-session metadata never update. We
            // additionally register a MediaController.Callback on the primary
            // controller and re-send the latest controller list on every live
            // change, so the downstream combine recomputes toNowPlaying.
            var current: List<MediaController> = emptyList()
            var watched: MediaController? = null
            val watcher =
                object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        trySend(current)
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        trySend(current)
                    }

                    override fun onSessionDestroyed() {
                        trySend(current)
                    }
                }

            fun rewatch(controllers: List<MediaController>) {
                current = controllers
                val next = selectPrimaryController(controllers)
                // Re-register only when the active controller changes; unregister
                // the previous one first so we never leak a stale callback.
                if (next !== watched) {
                    runCatching { watched?.unregisterCallback(watcher) }
                    watched = next
                    runCatching { watched?.registerCallback(watcher) }
                }
            }

            val sessionsListener =
                MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    rewatch(controllers.orEmpty())
                    trySend(current)
                }

            runCatching {
                rewatch(sessionManager.getActiveSessions(componentName))
                trySend(current)
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            }.onFailure { trySend(emptyList()) }

            awaitClose {
                runCatching { watched?.unregisterCallback(watcher) }
                runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
            }
        }

    private fun hasPermission(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun List<MediaController>.toNowPlaying(): NowPlaying? =
        selectPrimaryController(this)
            ?.let { controller ->
                // A granted session can briefly expose no metadata (just connected,
                // or a metadata-less stream). Degrade to NoActiveSession upstream
                // rather than render a blank, broken-looking card.
                val metadata = controller.metadata ?: return@let null
                val playbackState = controller.playbackState
                NowPlaying(
                    // METADATA_KEY_TITLE is empty for many podcast / radio / stream
                    // sessions; fall back to the display title and finally the source
                    // label so the 23sp title line is never blank.
                    title =
                        metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
                            ?: sourceLabel(controller.packageName),
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.asImageBitmap(),
                    // sourceIcon is resolved downstream off Main (see stateFlow).
                    // A paused controller renders with isPlaying=false (Play icon,
                    // resumable), but stays on screen via selectPrimaryController.
                    isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
                    positionMs = playbackState?.position ?: 0L,
                    durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    packageName = controller.packageName,
                    playbackSpeed = playbackState?.playbackSpeed ?: 1f,
                    positionUpdateTimeMs = playbackState?.lastPositionUpdateTime ?: 0L,
                )
            }

    // Attach the source-app icon to a Playing state, leaving the other variants
    // untouched. Runs on Default (see stateFlow) so the icon decode is off Main.
    private fun MusicCardState.withSourceIcon(): MusicCardState =
        if (this is MusicCardState.Playing) {
            MusicCardState.Playing(nowPlaying.copy(sourceIcon = sourceIconOf(nowPlaying.packageName)))
        } else {
            this
        }

    /**
     * Resolve a source package's launcher icon, cached because it is stable for
     * the process lifetime and the session flow re-emits on every playback tick.
     * Returns null when the package has no resolvable icon (uninstalled /
     * restricted), in which case the card shows a generic launch glyph.
     */
    private fun sourceIconOf(packageName: String): ImageBitmap? =
        sourceIconCache.getOrPut(packageName) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = SOURCE_ICON_PIXELS, height = SOURCE_ICON_PIXELS)
                    .asImageBitmap()
            }.getOrNull()
        }

    private companion object {
        const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

        // The card draws the source icon small (~28 dp); 96 px keeps it crisp on
        // high-density head units without decoding a full-size adaptive icon.
        const val SOURCE_ICON_PIXELS = 96

        /**
         * Return `true` when the state is PLAYING or PAUSED. A paused session is
         * still resumable and must stay on the card, so the plain [isActive] check
         * (which excludes STATE_PAUSED) is not enough here.
         */
        private fun PlaybackState?.isPlayingOrPaused(): Boolean =
            this != null && (isActive() || state == PlaybackState.STATE_PAUSED)

        /**
         * Pick the highest-priority controller that is playing or paused.
         * [MediaSessionManager.getActiveSessions] is priority-ordered, so the
         * first match is the session the user is most likely interacting with.
         */
        private fun selectPrimaryController(controllers: List<MediaController>): MediaController? =
            controllers.firstOrNull { it.playbackState.isPlayingOrPaused() }
    }
}
