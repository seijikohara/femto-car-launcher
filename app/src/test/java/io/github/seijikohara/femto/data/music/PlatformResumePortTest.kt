package io.github.seijikohara.femto.data.music

import android.app.Application
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.FAKE_MUSIC_PACKAGE
import io.github.seijikohara.femto.testfixtures.FakeMediaApps
import io.github.seijikohara.femto.testfixtures.addFakeSession
import io.github.seijikohara.femto.testfixtures.grantNotificationListenerAccess
import io.github.seijikohara.femto.testfixtures.pushPlaybackState
import io.github.seijikohara.femto.testfixtures.revokeNotificationListenerAccess
import io.github.seijikohara.femto.testfixtures.startPlaying
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The production port's session watch against a real [MediaSessionManager]: what the resume chain counts as success
 * comes from here, so it must read the remembered app's progress from its own sessions only, see another app start
 * playing, hear a state change inside a session, and stop once notification access goes. Looper mode as in
 * MusicSessionRepositoryTest: the watch runs on Main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class PlatformResumePortTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val sessionManager: MediaSessionManager = checkNotNull(application.getSystemService())
    private val port = PlatformResumePort(application, FakeMediaApps())

    @Before
    fun grantAccess() = grantNotificationListenerAccess(application)

    @Test
    fun `another app's playing session is not the remembered app's progress`() =
        runTest {
            sessionManager.addFakeSession(application, OTHER_PACKAGE, PlaybackState.STATE_PLAYING)
            sessionManager.addFakeSession(application, FAKE_MUSIC_PACKAGE, PlaybackState.STATE_STOPPED)

            port.sessions(FAKE_MUSIC_PACKAGE).test {
                assertEquals(ResumeWatch(target = SessionPlayback.IDLE, otherPlaying = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the remembered app's session is heard starting to play`() =
        runTest {
            val session = sessionManager.addFakeSession(application, FAKE_MUSIC_PACKAGE, PlaybackState.STATE_STOPPED)

            port.sessions(FAKE_MUSIC_PACKAGE).test {
                assertEquals(ResumeWatch(), awaitItem())
                session.pushPlaybackState(PlaybackState.STATE_BUFFERING)
                assertEquals(ResumeWatch(target = SessionPlayback.BUFFERING), awaitItem())
                session.startPlaying()
                assertEquals(ResumeWatch(target = SessionPlayback.PLAYING), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `another app starting to play inside its session is heard`() =
        runTest {
            val other = sessionManager.addFakeSession(application, OTHER_PACKAGE, PlaybackState.STATE_PAUSED)

            port.sessions(FAKE_MUSIC_PACKAGE).test {
                assertEquals(ResumeWatch(), awaitItem())
                other.startPlaying()
                assertEquals(ResumeWatch(otherPlaying = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a session the app starts after the watch began is heard`() =
        runTest {
            port.sessions(FAKE_MUSIC_PACKAGE).test {
                assertEquals(ResumeWatch(), awaitItem())
                sessionManager.addFakeSession(application, FAKE_MUSIC_PACKAGE, PlaybackState.STATE_PLAYING)
                assertEquals(ResumeWatch(target = SessionPlayback.PLAYING), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `revoking notification access ends the watch`() =
        runTest {
            port.sessions(FAKE_MUSIC_PACKAGE).test {
                assertEquals(ResumeWatch(), awaitItem())
                revokeNotificationListenerAccess(application)
                assertIs<SecurityException>(awaitError())
            }
        }

    private companion object {
        const val OTHER_PACKAGE = "com.example.other"
    }
}
