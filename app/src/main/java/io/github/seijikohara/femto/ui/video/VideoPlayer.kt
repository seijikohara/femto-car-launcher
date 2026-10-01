package io.github.seijikohara.femto.ui.video

import android.content.Context
import android.util.Log
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "VideoPlayer"

/**
 * The video window's player, owned by [VideoViewModel] so playback outlives
 * the composition: the dashboard leaving the screen detaches the surface
 * ([detach]) and the audio keeps playing. [ExoVideoPlayer] is the production
 * implementation; tests substitute an in-memory fake. Every call runs on the
 * main thread.
 */
internal interface VideoPlayer {
    val isPlaying: StateFlow<Boolean>

    /** Whether the loaded file failed to play (unreadable, or a format the device cannot decode). */
    val failed: StateFlow<Boolean>

    /** Load [uri] (a content URI), paused, in place of anything loaded before. */
    fun load(uri: String)

    fun play()

    fun pause()

    /** Stop playback and free the decoders; the next [load] starts afresh. */
    fun stop()

    /** Draw the picture into [view]. */
    fun attach(view: TextureView)

    /** Stop drawing into [view]; playback and its audio go on. */
    fun detach(view: TextureView)

    fun release()
}

/**
 * [VideoPlayer] over media3's ExoPlayer, built on the first [load] and freed
 * on [stop], so a window that is off holds no decoder. The surface is a plain
 * [TextureView] attached through `Player.setVideoTextureView`: media3's Compose
 * surfaces (`PlayerSurface`, `ContentFrame`) are `@UnstableApi` in the pinned
 * media3, and a TextureView, unlike a SurfaceView, fades and clips with the
 * glass window and panel around it.
 */
internal class ExoVideoPlayer(
    context: Context,
) : VideoPlayer {
    private val appContext = context.applicationContext
    private val playing = MutableStateFlow(false)
    private val failure = MutableStateFlow(false)
    private var player: ExoPlayer? = null
    private var surface: TextureView? = null

    override val isPlaying: StateFlow<Boolean> = playing
    override val failed: StateFlow<Boolean> = failure

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing.value = isPlaying
            }

            // The error code name only: the message and cause can carry the
            // file's URI, which names the user's file.
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "playback failed: ${error.errorCodeName}")
                failure.value = true
            }
        }

    override fun load(uri: String) {
        failure.value = false
        (player ?: newPlayer().also { player = it }).run {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    override fun play() {
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun stop() {
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
        playing.value = false
        failure.value = false
    }

    override fun attach(view: TextureView) {
        surface = view
        player?.setVideoTextureView(view)
    }

    override fun detach(view: TextureView) {
        if (surface === view) surface = null
        player?.clearVideoTextureView(view)
    }

    override fun release() = stop()

    // Media usage with focus handling: playing the video pauses other media
    // (the music app the dashboard's card follows), and a call or another
    // player taking focus pauses the video. Unplugging headphones pauses it
    // too, as any media player would.
    private fun newPlayer(): ExoPlayer =
        ExoPlayer
            .Builder(appContext)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                // handleAudioFocus: a Java parameter, so it cannot be named.
                true,
            ).setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(listener)
                surface?.let(::setVideoTextureView)
            }
}
