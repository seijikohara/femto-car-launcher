package io.github.seijikohara.femto.data.music

import android.graphics.Bitmap
import android.media.session.PlaybackState
import io.github.seijikohara.femto.testfixtures.fakeMediaMetadata
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakePlaybackState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Plain value holder standing in for a MediaController in selection and
 * watch-set tests. [name] doubles as the key the watch set is reconciled by —
 * production keys it by `MediaController.getSessionToken` — so two instances
 * sharing a name stand for two enumerations of one session.
 */
private data class FakeSession(
    val name: String,
    val playbackState: PlaybackState?,
)

private fun selectPrimary(sessions: List<FakeSession>): FakeSession? =
    selectPrimarySession(sessions) {
        it.playbackState
    }

private fun reconcile(
    watched: Map<String, FakeSession>,
    sessions: List<FakeSession>,
): WatchSetUpdate<String, FakeSession> = reconcileWatchSet(watched, sessions) { it.name }

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
                FakeSession("stopped", fakePlaybackState(PlaybackState.STATE_STOPPED)),
                FakeSession("errored", fakePlaybackState(PlaybackState.STATE_ERROR)),
            )

        assertNull(selectPrimary(sessions))
    }

    @Test
    fun `selectPrimarySession skips inactive sessions and picks the first playing one`() {
        val sessions =
            listOf(
                FakeSession("stopped", fakePlaybackState(PlaybackState.STATE_STOPPED)),
                FakeSession("playing", fakePlaybackState(PlaybackState.STATE_PLAYING)),
            )

        assertEquals("playing", selectPrimary(sessions)?.name)
    }

    @Test
    fun `selectPrimarySession keeps a paused session selectable so it stays resumable`() {
        val sessions = listOf(FakeSession("paused", fakePlaybackState(PlaybackState.STATE_PAUSED)))

        assertEquals("paused", selectPrimary(sessions)?.name)
    }

    @Test
    fun `selectPrimarySession prefers the higher-priority paused session over a later playing one`() {
        // The input list is priority-ordered by MediaSessionManager; the first
        // playing-or-paused entry wins even when a lower-priority one is playing.
        val sessions =
            listOf(
                FakeSession("paused", fakePlaybackState(PlaybackState.STATE_PAUSED)),
                FakeSession("playing", fakePlaybackState(PlaybackState.STATE_PLAYING)),
            )

        assertEquals("paused", selectPrimary(sessions)?.name)
    }

    @Test
    fun `a missing playback state is never playing or paused`() {
        assertFalse((null as PlaybackState?).isPlayingOrPaused())
    }

    @Test
    fun `a missing playback state is never playing`() {
        assertFalse((null as PlaybackState?).isPlaying())
    }

    // -- reconcileWatchSet ---------------------------------------------------
    //
    // These exercise the reconciler on its own. WHICH sessions production hands
    // it — every enumerated one rather than only the one the card shows, the
    // issue-#358 fix — is a property of the call site in SessionWatch, and is
    // pinned in MusicSessionRepositoryTest, which drives the repository through
    // a real MediaSessionManager.

    @Test
    fun `reconcileWatchSet adds every session of a first enumeration`() {
        val playing = FakeSession("playing", fakePlaybackState(PlaybackState.STATE_PLAYING))
        val stopped = FakeSession("stopped", fakePlaybackState(PlaybackState.STATE_STOPPED))

        val update = reconcile(watched = emptyMap(), sessions = listOf(playing, stopped))

        assertEquals(listOf(playing, stopped), update.added)
        assertEquals(emptyList(), update.removed)
        assertEquals(mapOf("playing" to playing, "stopped" to stopped), update.watched)
    }

    @Test
    fun `reconcileWatchSet keeps the watched instance when a token re-enumerates`() {
        // getActiveSessions mints a fresh MediaController per token on every
        // call, so a same-token re-enumeration must diff to nothing and keep
        // the instance that already carries the registration.
        val registered = FakeSession("music", fakePlaybackState(PlaybackState.STATE_STOPPED))
        val reEnumerated = FakeSession("music", fakePlaybackState(PlaybackState.STATE_PLAYING))

        val update = reconcile(watched = mapOf("music" to registered), sessions = listOf(reEnumerated))

        assertEquals(emptyList(), update.added)
        assertEquals(emptyList(), update.removed)
        assertSame(registered, update.watched["music"])
    }

    @Test
    fun `reconcileWatchSet removes a departed session`() {
        val gone = FakeSession("gone", fakePlaybackState(PlaybackState.STATE_PLAYING))
        val stays = FakeSession("stays", fakePlaybackState(PlaybackState.STATE_PAUSED))

        val update = reconcile(watched = mapOf("gone" to gone, "stays" to stays), sessions = listOf(stays))

        assertEquals(listOf(gone), update.removed)
        assertEquals(emptyList(), update.added)
        assertEquals(mapOf("stays" to stays), update.watched)
    }

    @Test
    fun `reconcileWatchSet clears the watch set when the enumeration comes back empty`() {
        val gone = FakeSession("gone", fakePlaybackState(PlaybackState.STATE_PLAYING))

        val update = reconcile(watched = mapOf("gone" to gone), sessions = emptyList())

        assertEquals(listOf(gone), update.removed)
        assertEquals(emptyMap(), update.watched)
    }

    @Test
    fun `reconcileWatchSet keeps the enumeration priority order`() {
        // The watch set doubles as the emitted controller list, and
        // selectPrimarySession takes the first playing-or-paused entry, so the
        // priority order MediaSessionManager returned must survive a rewatch.
        val first = FakeSession("first", fakePlaybackState(PlaybackState.STATE_PAUSED))
        val second = FakeSession("second", fakePlaybackState(PlaybackState.STATE_PLAYING))

        val update = reconcile(watched = mapOf("second" to second), sessions = listOf(first, second))

        assertEquals(listOf("first", "second"), update.watched.values.map { it.name })
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
                metadata = fakeMediaMetadata(title = "Strobe", displayTitle = "Display"),
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
                metadata = fakeMediaMetadata(title = " ", displayTitle = "Display"),
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
                metadata = fakeMediaMetadata(title = "", displayTitle = " "),
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
            metadata = fakeMediaMetadata(title = "Strobe"),
            playbackState = null,
            packageName = PACKAGE,
            fallbackTitle = { error("fallback resolved despite a usable title") },
        )
    }

    @Test
    fun `nowPlayingOf is playing only for STATE_PLAYING`() {
        fun isPlayingFor(state: Int): Boolean =
            nowPlayingOf(
                metadata = fakeMediaMetadata(title = "Strobe"),
                playbackState = fakePlaybackState(state),
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
                metadata = fakeMediaMetadata(title = "Strobe"),
                playbackState =
                    fakePlaybackState(
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
                metadata = fakeMediaMetadata(title = "Strobe"),
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
                    fakeMediaMetadata(
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
                metadata = fakeMediaMetadata(title = "Strobe", albumArt = art),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            ).albumArt,
        )
        assertNull(
            nowPlayingOf(
                metadata = fakeMediaMetadata(title = "Strobe"),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            ).albumArt,
        )
    }

    @Test
    fun `nowPlayingOf derives seek and queue capabilities from the action bits`() {
        val result =
            nowPlayingOf(
                metadata = fakeMediaMetadata(title = "Strobe"),
                playbackState =
                    fakePlaybackState(
                        PlaybackState.STATE_PLAYING,
                        actions = PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM,
                    ),
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertTrue(result.canSeek)
        assertTrue(result.canSkipToQueueItem)
        // Shuffle / repeat capability comes from the media3 controller, not the
        // action bits, so it stays false unless passed in.
        assertFalse(result.canShuffle)
        assertFalse(result.canRepeat)
    }

    @Test
    fun `nowPlayingOf reports no capabilities without matching action bits`() {
        val result =
            nowPlayingOf(
                metadata = fakeMediaMetadata(title = "Strobe"),
                playbackState = fakePlaybackState(
                    PlaybackState.STATE_PLAYING,
                    actions = PlaybackState.ACTION_PLAY_PAUSE,
                ),
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertFalse(result.canSeek)
        assertFalse(result.canSkipToQueueItem)
        assertFalse(result.canShuffle)
        assertFalse(result.canRepeat)
    }

    @Test
    fun `nowPlayingOf reports no capabilities when the playback state is null`() {
        val result =
            nowPlayingOf(
                metadata = fakeMediaMetadata(title = "Strobe"),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertFalse(result.canSeek)
        assertFalse(result.canSkipToQueueItem)
        assertFalse(result.canShuffle)
        assertFalse(result.canRepeat)
    }

    @Test
    fun `nowPlayingOf prefers full-size art over the album thumb`() {
        val thumb = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val full = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        val result =
            nowPlayingOf(
                metadata = fakeMediaMetadata(title = "Strobe", albumArt = thumb, art = full),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
            )

        assertEquals(4, result.albumArt?.width)
    }

    @Test
    fun `nowPlayingOf passes through shuffle, repeat, capabilities, and queue`() {
        val queue = listOf(QueueEntry(7L, "Next", null))

        val result =
            nowPlayingOf(
                metadata = fakeMediaMetadata(title = "Strobe"),
                playbackState = null,
                packageName = PACKAGE,
                fallbackTitle = { FALLBACK },
                canShuffle = true,
                canRepeat = true,
                shuffleOn = true,
                repeatMode = RepeatMode.ONE,
                queue = queue,
            )

        assertTrue(result.canShuffle)
        assertTrue(result.canRepeat)
        assertTrue(result.shuffleOn)
        assertEquals(RepeatMode.ONE, result.repeatMode)
        assertEquals(queue, result.queue)
    }

    private companion object {
        const val PACKAGE = "com.example.music"
        const val FALLBACK = "Now playing"
    }
}

/**
 * State table for the two predicates the card reasons in. [isPlayingOrPaused]
 * decides whether a session keeps the card (the platform `isActive` set plus
 * STATE_PAUSED); [isPlaying] decides whether playback is running right now,
 * which drives the Play / Pause affordance and, in SessionWatch, whether a push
 * is worth re-reading the session priority order for.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackStatePredicateTest(
    private val state: Int,
    private val keepsCard: Boolean,
    private val playing: Boolean,
) {
    @Test
    fun `keeps the card on screen exactly for playing-or-paused states`() {
        assertEquals(keepsCard, fakePlaybackState(state).isPlayingOrPaused())
    }

    @Test
    fun `reports playback running only for STATE_PLAYING`() {
        assertEquals(playing, fakePlaybackState(state).isPlaying())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "state={0} -> card={1} playing={2}")
        fun params(): List<Array<Any>> =
            listOf(
                arrayOf(PlaybackState.STATE_PLAYING, true, true),
                arrayOf(PlaybackState.STATE_PAUSED, true, false),
                arrayOf(PlaybackState.STATE_BUFFERING, true, false),
                arrayOf(PlaybackState.STATE_CONNECTING, true, false),
                arrayOf(PlaybackState.STATE_FAST_FORWARDING, true, false),
                arrayOf(PlaybackState.STATE_REWINDING, true, false),
                arrayOf(PlaybackState.STATE_SKIPPING_TO_PREVIOUS, true, false),
                arrayOf(PlaybackState.STATE_SKIPPING_TO_NEXT, true, false),
                arrayOf(PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM, true, false),
                arrayOf(PlaybackState.STATE_NONE, false, false),
                arrayOf(PlaybackState.STATE_STOPPED, false, false),
                arrayOf(PlaybackState.STATE_ERROR, false, false),
            )
    }
}
