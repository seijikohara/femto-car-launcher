package io.github.seijikohara.femto.data.music

import android.media.MediaMetadata
import android.media.session.PlaybackState
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.FAKE_MUSIC_PACKAGE
import io.github.seijikohara.femto.testfixtures.FAKE_OWN_PACKAGE
import io.github.seijikohara.femto.testfixtures.FakeLastPlayedStore
import io.github.seijikohara.femto.testfixtures.FakeMediaApps
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedKeeper
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedState
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedTrack
import io.github.seijikohara.femto.testfixtures.fakeMediaMetadata
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakePlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the music card remembers of the sessions it watches, and when it lets go. The readings are built from real
 * platform [PlaybackState] / [MediaMetadata] values (Robolectric), the way the repository builds them from a live
 * session, and pass through the same recorder the repository's session flow runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LastPlayedTest {
    private val store = FakeLastPlayedStore()
    private val apps = FakeMediaApps()
    private val keeper = fakeLastPlayedKeeper(store, apps)

    private suspend fun record(vararg readings: SessionsReading) =
        flowOf(*readings).recordingLastPlayed(keeper).collect()

    // A reading as the repository builds one with access granted: the session's
    // own fields, before any of the card's fallbacks.
    private fun reading(
        state: Int,
        metadata: MediaMetadata = TRACK,
        packageName: String = FAKE_MUSIC_PACKAGE,
        positionMs: Long = 0L,
    ): SessionsReading =
        fakePlaybackState(state, positionMs = positionMs).let { playback ->
            SessionsReading(
                hasPermission = true,
                nowPlaying = nowPlayingOf(metadata, playback, packageName, fallbackTitle = { "Now playing" }),
                playing = lastPlayedTrackOrNull(packageName, playback, metadata),
            )
        }

    @Test
    fun `a playing session with a raw title is remembered`() =
        runTest {
            record(reading(PlaybackState.STATE_PLAYING))

            assertEquals(fakeLastPlayedTrack(), store.current)
        }

    @Test
    fun `a paused session is not`() =
        runTest {
            record(reading(PlaybackState.STATE_PAUSED))

            assertNull(store.current)
        }

    @Test
    fun `a session whose raw title is blank is not, though its card title falls back`() =
        runTest {
            val untitled =
                reading(PlaybackState.STATE_PLAYING, fakeMediaMetadata(title = " ", displayTitle = "Morning Show"))

            record(untitled)

            // The card still has a line to show; the memory has no track to name.
            assertEquals("Morning Show", untitled.nowPlaying?.title)
            assertNull(store.current)
        }

    @Test
    fun `position ticks do not write`() =
        runTest {
            record(
                reading(PlaybackState.STATE_PLAYING, positionMs = 1_000L),
                reading(PlaybackState.STATE_PLAYING, positionMs = 2_000L),
                reading(PlaybackState.STATE_PLAYING, positionMs = 3_000L),
            )

            assertEquals(1, store.saves)
        }

    @Test
    fun `the launcher's own package is never remembered`() =
        runTest {
            apps.launchable += FAKE_OWN_PACKAGE to "Launcher"

            record(reading(PlaybackState.STATE_PLAYING, packageName = FAKE_OWN_PACKAGE))

            assertNull(store.current)
        }

    @Test
    fun `a package without a launch intent is not remembered`() =
        runTest {
            record(reading(PlaybackState.STATE_PLAYING, packageName = HEADLESS_PACKAGE))

            assertNull(store.current)
        }

    @Test
    fun `the last track is kept when its session goes away`() =
        runTest {
            record(reading(PlaybackState.STATE_PLAYING), SessionsReading(hasPermission = true))

            assertEquals(fakeLastPlayedTrack(), store.current)
        }

    @Test
    fun `an uninstalled package is dropped`() =
        runTest {
            store.save(fakeLastPlayedTrack())
            apps.launchable = emptyMap()

            assertNull(keeper.remembered.first())
            assertNull(store.current)
        }

    @Test
    fun `a track whose app is still installed stays remembered`() =
        runTest {
            store.save(fakeLastPlayedTrack())

            assertEquals(fakeLastPlayedTrack(), keeper.remembered.first())
        }

    @Test
    fun `revoking notification access clears it`() =
        runTest {
            record(reading(PlaybackState.STATE_PLAYING), SessionsReading(hasPermission = false))

            assertNull(store.current)
        }

    @Test
    fun `a blank artist is remembered as none`() =
        assertNull(
            lastPlayedTrackOrNull(
                FAKE_MUSIC_PACKAGE,
                fakePlaybackState(PlaybackState.STATE_PLAYING),
                fakeMediaMetadata(title = "Headlights", artist = " "),
            )?.artist,
        )

    // -- what the card shows -----------------------------------------------------

    private fun noSession() = SessionsReading(hasPermission = true)

    private fun liveSession() = SessionsReading(hasPermission = true, nowPlaying = fakeNowPlaying(isPlaying = false))

    @Test
    fun `the remembered track shows while no session is live`() =
        runTest {
            store.save(fakeLastPlayedTrack())

            assertEquals(fakeLastPlayedState(), flowOf(noSession()).withLastPlayed(keeper).first())
        }

    @Test
    fun `a session live at the start never shows the remembered track first`() =
        runTest {
            store.save(fakeLastPlayedTrack())

            flowOf(liveSession()).withLastPlayed(keeper).test {
                assertEquals(MusicCardState.Playing(fakeNowPlaying(isPlaying = false)), awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the card takes a live session as soon as one appears`() =
        runTest {
            store.save(fakeLastPlayedTrack())
            val readings = MutableStateFlow(noSession())

            readings.withLastPlayed(keeper).test {
                assertEquals(fakeLastPlayedState(), awaitItem())
                readings.value = liveSession()
                assertEquals(MusicCardState.Playing(fakeNowPlaying(isPlaying = false)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a live session without metadata hides the remembered track`() =
        runTest {
            store.save(fakeLastPlayedTrack())
            val metadataless = SessionsReading(hasPermission = true, sessionLive = true)

            assertEquals(MusicCardState.NoActiveSession, flowOf(metadataless).withLastPlayed(keeper).first())
        }

    @Test
    fun `without notification access the card asks for it instead`() =
        runTest {
            store.save(fakeLastPlayedTrack())

            assertEquals(
                MusicCardState.NeedsPermission,
                flowOf(SessionsReading(hasPermission = false)).withLastPlayed(keeper).first(),
            )
        }

    @Test
    fun `a track that just played is offered once its session goes away`() =
        runTest {
            // Nothing is remembered at the start, so the offer can only be the
            // track the playing reading recorded.
            assertEquals(
                fakeLastPlayedState(),
                flowOf(reading(PlaybackState.STATE_PLAYING), noSession())
                    .withLastPlayed(keeper)
                    .first { it is MusicCardState.LastPlayed },
            )
        }

    @Test
    fun `nothing remembered leaves the card empty`() =
        runTest {
            assertEquals(MusicCardState.NoActiveSession, flowOf(noSession()).withLastPlayed(keeper).first())
        }

    private companion object {
        const val HEADLESS_PACKAGE = "com.example.headless"

        // The fixture track of fakeNowPlaying, as its session publishes it.
        val TRACK: MediaMetadata =
            fakeNowPlaying().let { fakeMediaMetadata(title = it.title, artist = it.artist, album = it.album) }
    }
}
