package io.github.seijikohara.femto.data.music

import android.app.Application
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Binder
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeMediaMetadata
import io.github.seijikohara.femto.testfixtures.fakePlaybackState
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowMediaSessionManager
import java.lang.reflect.Proxy
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

    @Before
    fun grantNotificationListenerAccess() {
        // Without the grant the card short-circuits to NeedsPermission and
        // never inspects a session.
        Settings.Secure.putString(
            application.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS,
            ComponentName(application, MusicSessionListenerService::class.java).flattenToString(),
        )
    }

    @Test
    fun `a session the card would not show still reports the moment it starts playing`() =
        runTest {
            // Both sessions are active but stopped, so the display policy picks
            // neither. Watching only the selected session therefore left no
            // callback registered anywhere, and nothing reported the start
            // (issue #358).
            addSession(IDLE_PACKAGE, PlaybackState.STATE_STOPPED)
            val resumed = addSession(RESUMED_PACKAGE, PlaybackState.STATE_STOPPED)

            MusicSessionRepository(application).stateFlow().test {
                assertEquals(MusicCardState.NoActiveSession, awaitItem())

                startPlaying(resumed)

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

            MusicSessionRepository(application).stateFlow().test {
                assertEquals(PAUSED_PACKAGE, playingPackage(awaitItem()))

                // The user resumes the second session from its own app: the
                // platform re-ranks it first but pushes no session-set change,
                // so the launcher sees nothing except the playback state.
                rerank(resumed, paused)
                startPlaying(resumed)

                assertEquals(RESUMED_PACKAGE, playingPackage(awaitItem()))
            }
        }

    private fun playingPackage(state: MusicCardState): String? =
        (state as? MusicCardState.Playing)?.nowPlaying?.packageName

    /**
     * Register an active session with the platform, appended to the priority
     * order [MediaSessionManager.getActiveSessions] hands out.
     */
    private fun addSession(
        packageName: String,
        state: Int,
    ): MediaController =
        mediaController(packageName).also { controller ->
            shadowOf(controller).setPackageName(packageName)
            shadowOf(controller).setPlaybackState(fakePlaybackState(state))
            shadowOf(controller).setMetadata(TRACK)
            shadowOf(sessionManager).addController(controller)
        }

    /**
     * Push a playing state from [controller]'s own app, the way a resume from
     * outside the launcher arrives: the session's own callback fires and the
     * session set does not change.
     */
    private fun startPlaying(controller: MediaController) =
        shadowOf(controller).executeOnPlaybackStateChanged(fakePlaybackState(PlaybackState.STATE_PLAYING))

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

    /**
     * Build a platform [MediaController] over a stub session binder. A
     * controller cannot be built here the way the platform builds one — it
     * needs a [MediaSession.Token], and a token needs a session binder — so the
     * binder is a proxy over the hidden `ISessionController` interface. It
     * answers the two calls this path makes on it: `asBinder`, which the token
     * hashes and compares by (the watch set is keyed by token), and
     * `getPackageName`, which the media3 token lookup rejects when null.
     */
    private fun mediaController(packageName: String): MediaController =
        Class.forName("android.media.session.ISessionController").let { sessionBinderType ->
            val binder = Binder()
            val sessionBinder =
                Proxy.newProxyInstance(sessionBinderType.classLoader, arrayOf(sessionBinderType)) { _, method, _ ->
                    when (method.name) {
                        "asBinder" -> binder
                        "getPackageName" -> packageName
                        else -> unanswered(method.returnType)
                    }
                }
            MediaController(
                application,
                MediaSession.Token::class.java
                    .getDeclaredConstructor(Int::class.javaPrimitiveType, sessionBinderType)
                    .newInstance(0, sessionBinder),
            )
        }

    // An unanswered binder call still has to return something of the declared
    // shape: handing null back for a primitive return type throws on unboxing.
    private fun unanswered(returnType: Class<*>): Any? =
        when (returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            else -> null
        }

    private companion object {
        const val IDLE_PACKAGE = "com.example.idle"
        const val PAUSED_PACKAGE = "com.example.paused"
        const val RESUMED_PACKAGE = "com.example.resumed"

        // toNowPlaying degrades a session with no metadata to NoActiveSession,
        // so every session here publishes the same track: these tests are about
        // which session reaches the card, not about what it renders.
        val TRACK: MediaMetadata = fakeMediaMetadata(title = "Strobe")
    }
}
