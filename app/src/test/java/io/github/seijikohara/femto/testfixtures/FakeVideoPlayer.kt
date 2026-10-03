package io.github.seijikohara.femto.testfixtures

import android.view.TextureView
import io.github.seijikohara.femto.ui.video.VideoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [VideoPlayer]: records what the ViewModel asked of it. [loaded] is
 * the file the player holds (null once stopped), [playing] mirrors play / pause,
 * and [fail] stages a playback error the way a file the decoder cannot open
 * reports one. [attached] is the surface the UI attached and has not
 * detached.
 */
internal class FakeVideoPlayer : VideoPlayer {
    private val playing = MutableStateFlow(false)
    private val failure = MutableStateFlow(false)

    override val isPlaying: StateFlow<Boolean> = playing
    override val failed: StateFlow<Boolean> = failure
    override val videoAspectRatio: StateFlow<Float?> = MutableStateFlow(null)

    /** The surface the picture is drawn into now, or null. */
    var attached: TextureView? = null
        private set

    var loaded: String? = null
        private set
    var loads = 0
        private set
    var stops = 0
        private set
    var released = false
        private set

    override fun load(uri: String) {
        loaded = uri
        loads++
        failure.value = false
    }

    override fun play() {
        if (loaded != null) playing.value = true
    }

    override fun pause() {
        playing.value = false
    }

    override fun stop() {
        loaded = null
        stops++
        playing.value = false
    }

    override fun attach(view: TextureView) {
        attached = view
    }

    override fun detach(view: TextureView) {
        if (attached === view) attached = null
    }

    override fun release() {
        stop()
        released = true
    }

    fun fail() {
        playing.value = false
        failure.value = true
    }
}
