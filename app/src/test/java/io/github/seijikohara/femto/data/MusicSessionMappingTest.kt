package io.github.seijikohara.femto.data

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Robolectric provides real PlaybackState / MediaMetadata builders, so the
// extracted pure logic is exercised with genuine platform values instead of
// mocks (MediaController itself stays out of reach — see MusicSessionRepository).
private fun playbackState(
    state: Int,
    positionMs: Long = 0L,
    speed: Float = 1f,
    updateTimeMs: Long = 0L,
): PlaybackState =
    PlaybackState
        .Builder()
        .setState(state, positionMs, speed, updateTimeMs)
        .build()

private fun metadata(
    title: String? = null,
    displayTitle: String? = null,
    artist: String? = null,
    album: String? = null,
    durationMs: Long = 0L,
    albumArt: Bitmap? = null,
): MediaMetadata =
    MediaMetadata
        .Builder()
        .apply {
            title?.let { putString(MediaMetadata.METADATA_KEY_TITLE, it) }
            displayTitle?.let { putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, it) }
            artist?.let { putString(MediaMetadata.METADATA_KEY_ARTIST, it) }
            album?.let { putString(MediaMetadata.METADATA_KEY_ALBUM, it) }
            putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            albumArt?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        }.build()

/** Plain value holder standing in for a MediaController in selection tests. */
private data class FakeSession(
    val name: String,
    val playbackState: PlaybackState?,
)

private fun selectPrimary(sessions: List<FakeSession>): FakeSession? =
    selectPrimarySession(sessions) {
        it.playbackState
    }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicSessionMappingTest {
    // -- selectPrimarySession ------------------------------------------------

    @Test
    fun `selectPrimarySession returns null for an empty session list`() {
        assertNull(selectPrimary(emptyList()))
    }

    @Test
    fun `selectPrimarySession returns null when no session is playing or paused`() {
        val sessions =
            listOf(
                FakeSession("stateless", playbackState = null),
                FakeSession("stopped", playbackState(PlaybackState.STATE_STOPPED)),
                FakeSession("errored", playbackState(PlaybackState.STATE_ERROR)),
            )

        assertNull(selectPrimary(sessions))
    }

    @Test
    fun `selectPrimarySession skips inactive sessions and picks the first playing one`() {
        val sessions =
            listOf(
                FakeSession("stopped", playbackState(PlaybackState.STATE_STOPPED)),
                FakeSession("playing", playbackState(PlaybackState.STATE_PLAYING)),
            )

        assertEquals("playing", selectPrimary(sessions)?.name)
    }

    @Test
    fun `selectPrimarySession keeps a paused session selectable so it stays resumable`() {
        val sessions = listOf(FakeSession("paused", playbackState(PlaybackState.STATE_PAUSED)))

        assertEquals("paused", selectPrimary(sessions)?.name)
    }

    @Test
    fun `selectPrimarySession prefers the higher-priority paused session over a later playing one`() {
        // The input list is priority-ordered by MediaSessionManager; the first
        // playing-or-paused entry wins even when a lower-priority one is playing.
        val sessions =
            listOf(
                FakeSession("paused", playbackState(PlaybackState.STATE_PAUSED)),
                FakeSession("playing", playbackState(PlaybackState.STATE_PLAYING)),
            )

        assertEquals("paused", selectPrimary(sessions)?.name)
    }

    @Test
    fun `a missing playback state is never playing or paused`() {
        assertFalse((null as PlaybackState?).isPlayingOrPaused())
    }

    // -- musicCardStateOf ----------------------------------------------------

    @Test
    fun `musicCardStateOf reports NeedsPermission while the listener grant is missing`() {
        assertEquals(
            MusicCardState.NeedsPermission,
            musicCardStateOf(hasPermission = false) { fakeNowPlaying() },
        )
    }

    @Test
    fun `musicCardStateOf does not inspect sessions while the grant is missing`() {
        // Session enumeration throws SecurityException before the grant, so the
        // lambda must stay unevaluated on the NeedsPermission path.
        musicCardStateOf(hasPermission = false) { error("session inspected without permission") }
    }

    @Test
    fun `musicCardStateOf wraps a resolved session in Playing`() {
        val nowPlaying = fakeNowPlaying()

        assertEquals(
            MusicCardState.Playing(nowPlaying),
            musicCardStateOf(hasPermission = true) { nowPlaying },
        )
    }

    @Test
    fun `musicCardStateOf falls back to NoActiveSession when nothing resolves`() {
        assertEquals(
            MusicCardState.NoActiveSession,
            musicCardStateOf(hasPermission = true) { null },
        )
    }

    // -- nowPlayingOf --------------------------------------------------------

    @Test
    fun `nowPlayingOf uses the metadata title when present`() {
        val result =
            nowPlayingOf(
                metadata = metadata(title = "Strobe", displayTitle = "Display"),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertEquals("Strobe", result.title)
    }

    @Test
    fun `nowPlayingOf falls back to the display title when the title is blank`() {
        val result =
            nowPlayingOf(
                metadata = metadata(title = " ", displayTitle = "Display"),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertEquals("Display", result.title)
    }

    @Test
    fun `nowPlayingOf falls back to the source label when both titles are blank`() {
        val result =
            nowPlayingOf(
                metadata = metadata(title = "", displayTitle = " "),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertEquals(FALLBACK, result.title)
    }

    @Test
    fun `nowPlayingOf resolves the fallback label only when needed`() {
        // sourceLabel touches PackageManager in production, so it must stay
        // unevaluated while a usable title exists.
        nowPlayingOf(
            metadata = metadata(title = "Strobe"),
            playbackState = null,
            packageName = PACKAGE,
            fallbackTitle = { error("fallback resolved despite a usable title") },
        )
    }

    @Test
    fun `nowPlayingOf is playing only for STATE_PLAYING`() {
        fun isPlayingFor(state: Int): Boolean =
            nowPlayingOf(
                metadata = metadata(title = "Strobe"),
                playbackState = playbackState(state),
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            ).isPlaying

        assertTrue(isPlayingFor(PlaybackState.STATE_PLAYING))
        // A paused session stays on the card but renders the Play affordance.
        assertFalse(isPlayingFor(PlaybackState.STATE_PAUSED))
    }

    @Test
    fun `nowPlayingOf copies position, speed, and update basis from the playback state`() {
        val result =
            nowPlayingOf(
                metadata = metadata(title = "Strobe"),
                playbackState =
                    playbackState(
                        PlaybackState.STATE_PLAYING,
                        positionMs = 5_000L,
                        speed = 1.5f,
                        updateTimeMs = 42_000L,
                    ),
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertEquals(5_000L, result.positionMs)
        assertEquals(1.5f, result.playbackSpeed)
        assertEquals(42_000L, result.positionUpdateTimeMs)
    }

    @Test
    fun `nowPlayingOf defaults transport fields when the playback state is null`() {
        val result =
            nowPlayingOf(
                metadata = metadata(title = "Strobe"),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertFalse(result.isPlaying)
        assertEquals(0L, result.positionMs)
        assertEquals(1f, result.playbackSpeed)
        assertEquals(0L, result.positionUpdateTimeMs)
    }

    @Test
    fun `nowPlayingOf maps artist, album, duration, and package name`() {
        val result =
            nowPlayingOf(
                metadata =
                    metadata(
                        title = "Strobe",
                        artist = "deadmau5",
                        album = "For Lack of a Better Name",
                        durationMs = 632_000L,
                    ),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertEquals("deadmau5", result.artist)
        assertEquals("For Lack of a Better Name", result.album)
        assertEquals(632_000L, result.durationMs)
        assertEquals(PACKAGE, result.packageName)
    }

    @Test
    fun `nowPlayingOf decodes the album art when present and leaves it null otherwise`() {
        val art = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        assertNotNull(
            nowPlayingOf(
                metadata = metadata(title = "Strobe", albumArt = art),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            ).albumArt,
        )
        assertNull(
            nowPlayingOf(
                metadata = metadata(title = "Strobe"),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            ).albumArt,
        )
    }

    private companion object {
        const val PACKAGE = "com.example.music"
        const val FALLBACK = "Now playing"
    }
}

/**
 * State table for [isPlayingOrPaused]: every state that keeps the card on
 * screen (the platform `isActive` set plus STATE_PAUSED) versus the ones that
 * release it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33])
class IsPlayingOrPausedStateTest(
    private val state: Int,
    private val expected: Boolean,
) {
    @Test
    fun `keeps the card on screen exactly for playing-or-paused states`() {
        assertEquals(expected, playbackState(state).isPlayingOrPaused())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "state={0} -> {1}")
        fun params(): List<Array<Any>> =
            listOf(
                arrayOf(PlaybackState.STATE_PLAYING, true),
                arrayOf(PlaybackState.STATE_PAUSED, true),
                arrayOf(PlaybackState.STATE_BUFFERING, true),
                arrayOf(PlaybackState.STATE_CONNECTING, true),
                arrayOf(PlaybackState.STATE_FAST_FORWARDING, true),
                arrayOf(PlaybackState.STATE_REWINDING, true),
                arrayOf(PlaybackState.STATE_SKIPPING_TO_PREVIOUS, true),
                arrayOf(PlaybackState.STATE_SKIPPING_TO_NEXT, true),
                arrayOf(PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM, true),
                arrayOf(PlaybackState.STATE_NONE, false),
                arrayOf(PlaybackState.STATE_STOPPED, false),
                arrayOf(PlaybackState.STATE_ERROR, false),
            )
    }
}
