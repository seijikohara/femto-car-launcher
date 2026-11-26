package io.github.seijikohara.femto.data.music

import android.media.MediaMetadata
import io.github.seijikohara.femto.testfixtures.FAKE_MUSIC_PACKAGE
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedState
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedTrack
import io.github.seijikohara.femto.testfixtures.fakeMediaMetadata
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The track identity a session's metadata, the card and the remembered track each yield, which must agree. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackIdentityTest {
    @Test
    fun `the title is the title key when it is not blank`() {
        assertEquals(
            "Headlights",
            fakeMediaMetadata(title = "Headlights", displayTitle = "Other").trackIdentity().title,
        )
    }

    @Test
    fun `a blank title falls back to the display title, as the card's does`() {
        assertEquals("Shown", fakeMediaMetadata(title = "  ", displayTitle = "Shown").trackIdentity().title)
    }

    @Test
    fun `a blank title and a blank display title leave no title`() {
        assertNull(fakeMediaMetadata(title = "", displayTitle = " ").trackIdentity().title)
    }

    @Test
    fun `a title on neither key leaves no title`() {
        assertNull(fakeMediaMetadata().trackIdentity().title)
    }

    @Test
    fun `a blank artist reads as none, as the remembered track's does`() {
        assertNull(fakeMediaMetadata(title = "Headlights", artist = " ").trackIdentity().artist)
    }

    @Test
    fun `the card's title rule and the identity's agree`() {
        val metadata = fakeMediaMetadata(title = "", displayTitle = "Shown")

        assertEquals(metadata.trackIdentity().title, nowPlayingTitle(metadata))
    }

    @Test
    fun `a playing card shows the track of its title and artist`() {
        val nowPlaying = fakeNowPlaying()

        assertEquals(
            ShownCard(CardKind.PLAYING, FAKE_MUSIC_PACKAGE, trackIdentityOf(nowPlaying.title, nowPlaying.artist)),
            MusicCardState.Playing(nowPlaying).shown(),
        )
    }

    @Test
    fun `a resume offer shows the remembered track of its title and artist`() {
        val track = fakeLastPlayedTrack()

        assertEquals(
            ShownCard(CardKind.RESUME_OFFERED, FAKE_MUSIC_PACKAGE, trackIdentityOf(track.title, track.artist)),
            fakeLastPlayedState(track).shown(),
        )
    }

    @Test
    fun `a card with no track and the permission prompt show no app and no track`() {
        assertEquals(
            listOf(ShownCard(CardKind.NOTHING_PLAYING), ShownCard(CardKind.NO_ACCESS)),
            listOf(MusicCardState.NoActiveSession.shown(), MusicCardState.NeedsPermission.shown()),
        )
    }

    private fun nowPlayingTitle(metadata: MediaMetadata): String =
        nowPlayingOf(
            metadata = metadata,
            playbackState = null,
            packageName = FAKE_MUSIC_PACKAGE,
            fallbackTitle = { "fallback" },
        ).title
}
