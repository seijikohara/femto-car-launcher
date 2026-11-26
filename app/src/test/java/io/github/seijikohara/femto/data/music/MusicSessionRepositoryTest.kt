package io.github.seijikohara.femto.data.music

import android.app.Application
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.FakeLastPlayedStore
import io.github.seijikohara.femto.testfixtures.addFakeSession
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedKeeper
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedTrack
import io.github.seijikohara.femto.testfixtures.fakeSessionTrace
import io.github.seijikohara.femto.testfixtures.grantNotificationListenerAccess
import io.github.seijikohara.femto.testfixtures.pushPlaybackState
import io.github.seijikohara.femto.testfixtures.startPlaying
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowMediaSessionManager
import kotlin.test.assertEquals

/**
 * Drives [MusicSessionRepository] against a real [MediaSessionManager], so the
 * watch policy is exercised where it lives: the call site inside SessionWatch,
 * which decides which sessions carry a callback and when the platform's session
 * priority order is re-read. The pure helpers those decisions rest on are
 * covered on their own in MusicSessionMappingTest.
 *
 * The repository confines its session work to Main and hands the result to
 * Dispatchers.Default, so the test needs a main looper that runs by itself:
 * INSTRUMENTATION_TEST mode gives it one on its own thread, as a device does,
 * and leaves the test thread free to drive the sessions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class MusicSessionRepositoryTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val sessionManager: MediaSessionManager = checkNotNull(application.getSystemService())

    // Without the grant the card short-circuits to NeedsPermission and never
    // inspects a session.
    @Before
    fun grantAccess() = grantNotificationListenerAccess(application)

    @Test
    fun `a session the card would not show still reports the moment it starts playing`() =
        runTest {
            // Both sessions are active but stopped, so the display policy picks
            // neither. Watching only the selected session therefore left no
            // callback registered anywhere, and nothing reported the start
            // (issue #358).
            addSession(IDLE_PACKAGE, PlaybackState.STATE_STOPPED)
            val resumed = addSession(RESUMED_PACKAGE, PlaybackState.STATE_STOPPED)

            repository().stateFlow().test {
                assertEquals(MusicCardState.NoActiveSession, awaitItem())

                resumed.startPlaying()

                assertEquals(RESUMED_PACKAGE, playingPackage(awaitItem()))
            }
        }

    @Test
    fun `a resumed session takes the card from the paused one that outranked it`() =
        runTest {
            // The head-unit case: a paused session sits ahead of a second one in
            // priority order, so it owns the card while that one is idle.
            val paused = addSession(PAUSED_PACKAGE, PlaybackState.STATE_PAUSED)
            val resumed = addSession(RESUMED_PACKAGE, PlaybackState.STATE_STOPPED)

            repository().stateFlow().test {
                assertEquals(PAUSED_PACKAGE, playingPackage(awaitItem()))

                // The user resumes the second session from its own app: the
                // platform re-ranks it first but pushes no session-set change,
                // so the launcher sees nothing except the playback state.
                rerank(resumed, paused)
                resumed.startPlaying()

                assertEquals(RESUMED_PACKAGE, playingPackage(awaitItem()))
            }
        }

    @Test
    fun `a playing session without metadata keeps the remembered track off the card`() =
        runTest {
            // The session plays but publishes nothing to show, so the card has
            // no live track; the remembered one must still stay away from it.
            sessionManager.addFakeSession(application, RESUMED_PACKAGE, PlaybackState.STATE_PLAYING, metadata = null)
            val remembering = fakeLastPlayedKeeper(store = FakeLastPlayedStore(fakeLastPlayedTrack()))

            MusicSessionRepository(application, remembering, fakeSessionTrace()).stateFlow().test {
                assertEquals(MusicCardState.NoActiveSession, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the trace lists every session in order and marks the playing one as on the card`() =
        runTest {
            addSession(PLAYING_PACKAGE, PlaybackState.STATE_PLAYING)
            addSession(PAUSED_PACKAGE, PlaybackState.STATE_PAUSED)
            val trace = fakeSessionTrace()

            repository(trace).stateFlow().test {
                awaitCard(PLAYING_PACKAGE)

                assertEquals(listOf(PLAYING_PACKAGE to true, PAUSED_PACKAGE to false), latestSessions(trace))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the trace puts the paused session on the card once the playing one stops`() =
        runTest {
            val playing = addSession(PLAYING_PACKAGE, PlaybackState.STATE_PLAYING)
            addSession(PAUSED_PACKAGE, PlaybackState.STATE_PAUSED)
            val trace = fakeSessionTrace()

            repository(trace).stateFlow().test {
                awaitCard(PLAYING_PACKAGE)

                playing.pushPlaybackState(PlaybackState.STATE_STOPPED)
                awaitCard(PAUSED_PACKAGE)

                assertEquals(listOf(PLAYING_PACKAGE to false, PAUSED_PACKAGE to true), latestSessions(trace))
                cancelAndIgnoreRemainingEvents()
            }
        }

    // The last played track is kept in memory here: these tests are about
    // which session reaches the card, and a remembered track would carry over
    // from one test to the next through the real store. Each test gets a trace
    // of its own for the same reason: the process-wide one outlives the test.
    private fun repository(trace: SessionTrace = fakeSessionTrace()) =
        MusicSessionRepository(application, fakeLastPlayedKeeper(), trace)

    // Skip the emissions before the card shows [packageName]: the session watch
    // re-emits on callbacks this test does not drive, such as a media3 connect.
    private tailrec suspend fun ReceiveTurbine<MusicCardState>.awaitCard(packageName: String): Unit =
        if (playingPackage(awaitItem()) == packageName) Unit else awaitCard(packageName)

    // The latest snapshot's sessions in order, each with whether it is the one
    // on the card.
    private fun latestSessions(trace: SessionTrace): List<Pair<String, Boolean>> =
        trace.snapshots().last().let { snapshot ->
            snapshot.sessions.map { it.packageName to (it.session == snapshot.cardSession) }
        }

    private fun playingPackage(state: MusicCardState): String? =
        (state as? MusicCardState.Playing)?.nowPlaying?.packageName

    // Every session here publishes the same track (toNowPlaying degrades a
    // session with no metadata to NoActiveSession): these tests are about which
    // session reaches the card, not about what it renders.
    private fun addSession(
        packageName: String,
        state: Int,
    ): MediaController = sessionManager.addFakeSession(application, packageName, state)

    /**
     * Re-rank the active sessions without telling anyone. Adding a controller
     * normally pushes an OnActiveSessionsChanged to every listener, which would
     * hand the launcher the new order for free; resetting first drops the
     * controllers and the listeners together, and silently, so the repository
     * keeps the order it last enumerated.
     */
    private fun rerank(vararg sessions: MediaController) {
        ShadowMediaSessionManager.reset()
        sessions.forEach { shadowOf(sessionManager).addController(it) }
    }

    private companion object {
        const val IDLE_PACKAGE = "com.example.idle"
        const val PAUSED_PACKAGE = "com.example.paused"
        const val PLAYING_PACKAGE = "com.example.playing"
        const val RESUMED_PACKAGE = "com.example.resumed"
    }
}
