package io.github.seijikohara.femto.ui.video

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.RecordingPlayer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What [ExoVideoPlayer] asks of the media3 player under it: a load never
 * starts playback by itself, and Play after the end starts the file over.
 * Robolectric supplies the android.net.Uri a media item parses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExoVideoPlayerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val recording = RecordingPlayer()
    private val player = ExoVideoPlayer(context) { recording.player }

    @Test
    fun `a load stays paused even after a played file ended`() {
        player.load(FILE)

        val pause = recording.calls.indexOf("setPlayWhenReady" to listOf<Any?>(false))
        assertTrue(pause >= 0, "load never cleared playWhenReady: ${recording.callNames}")
        assertTrue(pause < recording.callNames.indexOf("prepare"), "playWhenReady cleared after prepare")
    }

    @Test
    fun `play after the end starts the file over`() {
        player.load(FILE)
        recording.playbackState = Player.STATE_ENDED
        recording.calls.clear()

        player.play()

        assertEquals(listOf("getPlaybackState", "seekToDefaultPosition", "play"), recording.callNames)
    }

    @Test
    fun `play mid-file resumes where it paused`() {
        player.load(FILE)
        recording.playbackState = Player.STATE_READY
        recording.calls.clear()

        player.play()

        assertFalse("seekToDefaultPosition" in recording.callNames)
        assertTrue("play" in recording.callNames)
    }

    private companion object {
        const val FILE = "content://com.example.documents/document/video%3A1"
    }
}
