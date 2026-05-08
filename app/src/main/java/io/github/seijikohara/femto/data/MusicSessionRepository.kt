package io.github.seijikohara.femto.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.ui.home.components.MusicCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

internal class MusicSessionRepository(
    private val context: Context,
) {
    private val sessionManager: MediaSessionManager = checkNotNull(context.getSystemService())
    private val componentName = ComponentName(context, MusicSessionListenerService::class.java)

    fun nowPlayingFlow(): Flow<NowPlaying?> =
        callbackFlow {
            val callback =
                MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    trySend(controllers.toNowPlaying())
                }

            runCatching {
                trySend(sessionManager.getActiveSessions(componentName).toNowPlaying())
                sessionManager.addOnActiveSessionsChangedListener(callback, componentName)
            }.onFailure { trySend(null) }

            awaitClose {
                runCatching { sessionManager.removeOnActiveSessionsChangedListener(callback) }
            }
        }.flowOn(Dispatchers.Main.immediate)

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
                sessionManager
                    .getActiveSessions(componentName)
                    .firstOrNull { it.playbackState?.isActive() == true }
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

    private fun List<MediaController>?.toNowPlaying(): NowPlaying? =
        this
            ?.firstOrNull { it.playbackState?.isActive() == true }
            ?.let { controller ->
                val metadata = controller.metadata ?: return@let null
                NowPlaying(
                    title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.asImageBitmap(),
                    isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                    positionMs = controller.playbackState?.position ?: 0L,
                    durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    packageName = controller.packageName,
                )
            }
}
