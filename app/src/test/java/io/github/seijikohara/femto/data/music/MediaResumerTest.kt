package io.github.seijikohara.femto.data.music

import android.media.session.PlaybackState
import io.github.seijikohara.femto.testfixtures.FAKE_MUSIC_PACKAGE
import io.github.seijikohara.femto.testfixtures.FakeResumePort
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The resume chain on virtual time, over a fake port: which step runs when, what counts as success, and that the
 * browser connection is held until the app plays and released on every path. The waits are probed a millisecond on
 * each side of the windows they rest on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaResumerTest {
    private val port = FakeResumePort()
    private val outcomes = mutableListOf<ResumeOutcome>()
    private val forgotten = mutableListOf<String>()
    private val resumer = MediaResumer(port, forget = { forgotten += it }, record = { outcomes += it })

    // Start an attempt and run it up to its first wait.
    private fun TestScope.start(): Deferred<ResumeOutcome> =
        async {
            resumer.resume(FAKE_MUSIC_PACKAGE)
        }.also { runCurrent() }

    private fun TestScope.play(state: SessionPlayback) {
        port.play(state)
        runCurrent()
    }

    private fun started(step: ResumeStep) = ResumeOutcome.Started(FAKE_MUSIC_PACKAGE, step)

    private fun notStarted(reason: NotStartedReason) = ResumeOutcome.NotStarted(FAKE_MUSIC_PACKAGE, reason)

    private val disconnects get() = port.calls.count { it == "disconnect" }

    // -- step 1: the browser --------------------------------------------------

    @Test
    fun `an app the browser brings to PLAYING ends the chain at the browser step`() =
        runTest {
            val attempt = start()
            advanceTimeBy(2_000)
            play(SessionPlayback.PLAYING)

            assertEquals(started(ResumeStep.BROWSER), attempt.await())
            assertEquals(listOf("browser", "disconnect"), port.calls)
        }

    @Test
    fun `an idle session is not success`() =
        runTest {
            // Binding the service creates an idle session at once; the app never plays.
            val attempt = start()
            play(SessionPlayback.IDLE)
            advanceUntilIdle()

            assertEquals(notStarted(NotStartedReason.NO_PLAYBACK), attempt.await())
        }

    @Test
    fun `the media key waits out the browser step's window`() =
        runTest {
            start()
            advanceTimeBy(RESUME_BROWSER_WINDOW_MS - 1)
            runCurrent()
            assertFalse("media key" in port.calls)

            advanceTimeBy(2)
            assertTrue("media key" in port.calls)
        }

    @Test
    fun `an app without a browser service goes straight to the media key`() =
        runTest {
            port.hasBrowser = false
            start()

            assertEquals(listOf("media key"), port.calls)
        }

    @Test
    fun `a refused or crashed browser is dropped and the media key follows at once`() =
        runTest {
            start()
            port.loseBrowser()
            runCurrent()

            assertEquals(listOf("browser", "disconnect", "media key"), port.calls)
            assertEquals(0L, currentTime)
        }

    // -- buffering ------------------------------------------------------------

    @Test
    fun `buffering extends the wait past the step's window`() =
        runTest {
            val attempt = start()
            advanceTimeBy(RESUME_BROWSER_WINDOW_MS / 2)
            play(SessionPlayback.BUFFERING)
            advanceTimeBy(RESUME_BROWSER_WINDOW_MS)
            play(SessionPlayback.PLAYING)

            assertEquals(started(ResumeStep.BROWSER), attempt.await())
            assertFalse("media key" in port.calls)
        }

    @Test
    fun `buffering that never plays moves on at the buffering limit`() =
        runTest {
            start()
            play(SessionPlayback.BUFFERING)
            advanceTimeBy(RESUME_BUFFERING_LIMIT_MS - 1)
            runCurrent()
            assertFalse("media key" in port.calls)

            advanceTimeBy(2)
            assertTrue("media key" in port.calls)
        }

    @Test
    fun `buffering that ends without playing moves on at once`() =
        runTest {
            // A prepare that ended paused: the media key is what starts it.
            start()
            play(SessionPlayback.BUFFERING)
            play(SessionPlayback.IDLE)

            assertEquals(listOf("browser", "media key"), port.calls)
        }

    // -- step 2 and 3: the media key, then the app ----------------------------

    @Test
    fun `a media key that starts playback ends the chain at that step`() =
        runTest {
            port.hasBrowser = false
            val attempt = start()
            advanceTimeBy(1_000)
            play(SessionPlayback.PLAYING)

            assertEquals(started(ResumeStep.MEDIA_KEY), attempt.await())
            assertEquals(listOf("media key"), port.calls)
        }

    @Test
    fun `an app that cold-starts slowly after the media key still plays before the app is opened`() =
        runTest {
            // A slow head unit just after boot: no state at all for 10 s after
            // the key, then PLAYING. The app must not be opened over it first.
            port.hasBrowser = false
            val attempt = start()
            advanceTimeBy(SLOW_COLD_START_MS)
            play(SessionPlayback.PLAYING)

            assertEquals(started(ResumeStep.MEDIA_KEY), attempt.await())
            assertFalse("open app" in port.calls)
        }

    @Test
    fun `the app opens only once the media key's window shows no progress`() =
        runTest {
            port.hasBrowser = false
            start()
            advanceTimeBy(RESUME_MEDIA_KEY_WINDOW_MS - 1)
            runCurrent()
            assertFalse("open app" in port.calls)

            advanceTimeBy(2)
            assertTrue("open app" in port.calls)
        }

    @Test
    fun `an app that plays once opened ends the chain at that step`() =
        runTest {
            port.hasBrowser = false
            val attempt = start()
            advanceTimeBy(RESUME_MEDIA_KEY_WINDOW_MS + 1_000)
            play(SessionPlayback.PLAYING)

            assertEquals(started(ResumeStep.OPEN_APP), attempt.await())
        }

    @Test
    fun `an app that never plays ends the chain after the three windows`() =
        runTest {
            val attempt = start()
            advanceUntilIdle()

            assertEquals(notStarted(NotStartedReason.NO_PLAYBACK), attempt.await())
            assertEquals(RESUME_BROWSER_WINDOW_MS + RESUME_MEDIA_KEY_WINDOW_MS + RESUME_OPEN_APP_WINDOW_MS, currentTime)
            assertEquals(listOf("browser", "media key", "open app", "disconnect"), port.calls)
        }

    // -- the browser connection -----------------------------------------------

    @Test
    fun `a silent browser stays connected until the app plays`() =
        runTest {
            val attempt = start()
            advanceTimeBy(RESUME_BROWSER_WINDOW_MS + 1_000)
            runCurrent()
            assertTrue(port.connected, "dropped while the media key could still reach its session")

            play(SessionPlayback.PLAYING)
            assertEquals(started(ResumeStep.MEDIA_KEY), attempt.await())
            assertFalse(port.connected)
        }

    @Test
    fun `cancelling the attempt disconnects the browser`() =
        runTest {
            val attempt = start()
            advanceTimeBy(1_000)
            attempt.cancel()
            runCurrent()

            assertFalse(port.connected)
            assertEquals(1, disconnects)
        }

    @Test
    fun `a session watch that fails ends the attempt and disconnects`() =
        runTest {
            val attempt = start()
            port.failWatch()
            runCurrent()

            assertEquals(notStarted(NotStartedReason.SESSIONS_UNREADABLE), attempt.await())
            assertFalse(port.connected)
        }

    @Test
    fun `a dropped browser is disconnected only once`() =
        runTest {
            val attempt = start()
            port.loseBrowser()
            advanceUntilIdle()
            attempt.await()

            assertEquals(1, disconnects)
        }

    // -- the driver left the dashboard -------------------------------------------

    @Test
    fun `a launcher no longer in front does not open the app`() =
        runTest {
            port.hasBrowser = false
            port.foreground = false
            val attempt = start()
            advanceUntilIdle()

            assertEquals(notStarted(NotStartedReason.LEFT_DASHBOARD), attempt.await())
            assertFalse("open app" in port.calls)
        }

    @Test
    fun `leaving the dashboard keeps a buffering browser until the app plays`() =
        runTest {
            // Only the last step asks whether the launcher is in front: dropping
            // the browser mid-buffer would stop a service that is only bound.
            port.foreground = false
            val attempt = start()
            play(SessionPlayback.BUFFERING)
            advanceTimeBy(RESUME_BROWSER_WINDOW_MS * 2)
            runCurrent()
            assertTrue(port.connected)

            play(SessionPlayback.PLAYING)
            assertEquals(started(ResumeStep.BROWSER), attempt.await())
        }

    // -- another app starts playing ----------------------------------------------

    @Test
    fun `another app playing after the media key ends the attempt without opening the remembered app`() =
        runTest {
            // The media key reached the platform's last media button receiver, not the remembered app.
            val attempt = start()
            advanceTimeBy(RESUME_BROWSER_WINDOW_MS + 1_000)
            port.otherAppPlays()
            runCurrent()

            assertEquals(notStarted(NotStartedReason.OTHER_APP_PLAYING), attempt.await())
            assertEquals(listOf("browser", "media key", "disconnect"), port.calls)
        }

    @Test
    fun `another app playing during the browser step ends the attempt before the media key`() =
        runTest {
            val attempt = start()
            advanceTimeBy(1_000)
            port.otherAppPlays()
            runCurrent()

            assertEquals(notStarted(NotStartedReason.OTHER_APP_PLAYING), attempt.await())
            assertFalse("media key" in port.calls)
        }

    @Test
    fun `the remembered app pausing after the browser's prepare does not end the attempt`() =
        runTest {
            // Prepared, then paused: the media key is what starts it, so the
            // attempt goes on to it instead of ending.
            val attempt = start()
            play(SessionPlayback.BUFFERING)
            play(SessionPlayback.IDLE)
            assertFalse(attempt.isCompleted)
            assertTrue("media key" in port.calls)

            play(SessionPlayback.PLAYING)
            assertEquals(started(ResumeStep.MEDIA_KEY), attempt.await())
        }

    @Test
    fun `the remembered app playing outranks another app playing in the same reading`() =
        runTest {
            val attempt = start()
            port.otherAppPlays()
            play(SessionPlayback.PLAYING)

            assertEquals(started(ResumeStep.BROWSER), attempt.await())
        }

    // -- a gone app -------------------------------------------------------------

    @Test
    fun `an uninstalled app ends the attempt untried and is forgotten`() =
        runTest {
            port.launchable = false
            val outcome = start().await()

            assertEquals(notStarted(NotStartedReason.APP_GONE), outcome)
            assertEquals(emptyList(), port.calls)
            assertEquals(listOf(FAKE_MUSIC_PACKAGE), forgotten)
        }

    @Test
    fun `an app that can no longer be opened is forgotten`() =
        runTest {
            port.hasBrowser = false
            port.opensApp = false
            val attempt = start()
            advanceUntilIdle()

            assertEquals(notStarted(NotStartedReason.APP_GONE), attempt.await())
            assertEquals(listOf(FAKE_MUSIC_PACKAGE), forgotten)
        }

    @Test
    fun `an app that did not play is not forgotten`() =
        runTest {
            val attempt = start()
            advanceUntilIdle()
            attempt.await()

            assertEquals(emptyList(), forgotten)
        }

    // -- the record -----------------------------------------------------------

    @Test
    fun `each outcome is recorded for diagnostics`() =
        runTest {
            val attempt = start()
            play(SessionPlayback.PLAYING)
            attempt.await()

            assertEquals(listOf<ResumeOutcome>(started(ResumeStep.BROWSER)), outcomes)
        }

    @Test
    fun `a cancelled attempt is recorded as interrupted`() =
        runTest {
            val attempt = start()
            attempt.cancel()
            runCurrent()

            assertEquals(listOf<ResumeOutcome>(notStarted(NotStartedReason.INTERRUPTED)), outcomes)
        }

    // -- reading an app's sessions ---------------------------------------------

    @Test
    fun `one playing session among idle ones reads as playing`() =
        assertEquals(
            SessionPlayback.PLAYING,
            sessionPlaybackOf(listOf(PlaybackState.STATE_STOPPED, PlaybackState.STATE_PLAYING, null)),
        )

    @Test
    fun `buffering and connecting read as progress`() =
        assertEquals(
            listOf(SessionPlayback.BUFFERING, SessionPlayback.BUFFERING),
            listOf(PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING).map { sessionPlaybackOf(listOf(it)) },
        )

    @Test
    fun `a paused, stopped or stateless session reads as idle`() =
        assertEquals(
            SessionPlayback.IDLE,
            sessionPlaybackOf(
                listOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, null),
            ),
        )

    @Test
    fun `no session reads as idle`() = assertEquals(SessionPlayback.IDLE, sessionPlaybackOf(emptyList()))

    @Test
    fun `another app's playing session is seen apart from the remembered app's`() =
        assertEquals(
            ResumeWatch(target = SessionPlayback.BUFFERING, otherPlaying = true),
            resumeWatchOf(
                FAKE_MUSIC_PACKAGE,
                listOf(
                    FAKE_MUSIC_PACKAGE to PlaybackState.STATE_BUFFERING,
                    OTHER_PACKAGE to PlaybackState.STATE_PLAYING,
                ),
            ),
        )

    @Test
    fun `another app's paused or buffering session does not count as playing`() =
        assertEquals(
            ResumeWatch(),
            resumeWatchOf(
                FAKE_MUSIC_PACKAGE,
                listOf(OTHER_PACKAGE to PlaybackState.STATE_PAUSED, OTHER_PACKAGE to PlaybackState.STATE_BUFFERING),
            ),
        )

    private companion object {
        const val OTHER_PACKAGE = "com.example.radio"

        // About three times the 3 s an emulator on a fast host took from the key to PLAYING.
        const val SLOW_COLD_START_MS = 10_000L
    }
}
