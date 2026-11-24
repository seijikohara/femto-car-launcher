package io.github.seijikohara.femto.data.music

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.FakeLastPlayedStore
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedTrack
import io.github.seijikohara.femto.testfixtures.grantNotificationListenerAccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Turning notification access off forgets the last played track through the listener's disconnect, rather than when
 * the dashboard is next shown; a disconnect with access still on keeps it. The grant is read through a seam on virtual
 * time, so the order of the disconnect against the grant's withdrawal can be played both ways.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicSessionListenerServiceTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val store = FakeLastPlayedStore(fakeLastPlayedTrack())

    // Answers each read of the grant in turn; counts the reads.
    private class GrantReads(
        private vararg val answers: Boolean,
    ) : () -> Boolean {
        var reads = 0
            private set

        override fun invoke(): Boolean = answers[minOf(reads++, answers.lastIndex)]
    }

    @Test
    fun `a disconnect after access was withdrawn forgets the track at once`() =
        runTest {
            forgetLastPlayedWithoutAccess(store, GrantReads(false))

            assertNull(store.current)
            assertEquals(0L, currentTime)
        }

    @Test
    fun `the disconnect comes before the grant is updated`() =
        runTest {
            // The first read still lists the launcher; the grant goes a moment later.
            forgetLastPlayedWithoutAccess(store, GrantReads(true, false))

            assertNull(store.current)
            assertEquals(LISTENER_GRANT_RECHECK_MS, currentTime)
        }

    @Test
    fun `a disconnect with access still on keeps the track after the re-check`() =
        runTest {
            val grant = GrantReads(true)

            forgetLastPlayedWithoutAccess(store, grant)

            assertEquals(fakeLastPlayedTrack(), store.current)
            assertEquals(2, grant.reads, "one re-check, not a loop")
        }

    @Test
    fun `the listener's disconnect clears the stored track on the live grant`() {
        // The real service, store and grant read. The setting the dashboard
        // reads still lists the launcher, as it can at the disconnect, while
        // the system's live list (Robolectric's notification manager) no
        // longer does: only a read of the live list clears the track. The
        // clear runs past the callback, so the test waits for the store.
        val realStore = LastPlayedPreferences(application)
        runBlocking {
            application.lastPlayedDataStore.edit { it.clear() }
            realStore.save(fakeLastPlayedTrack())
        }
        grantNotificationListenerAccess(application)
        shadowOf(checkNotNull(application.getSystemService<NotificationManager>()))
            .setNotificationListenerAccessGranted(
                ComponentName(application, MusicSessionListenerService::class.java),
                false,
            )

        Robolectric
            .buildService(MusicSessionListenerService::class.java)
            .create()
            .get()
            .onListenerDisconnected()

        assertNull(runBlocking { withTimeout(STORE_WAIT_MS) { realStore.track.first { it == null } } })
    }

    private companion object {
        // Past LISTENER_GRANT_RECHECK_MS, so a slow store write does not fail the test.
        const val STORE_WAIT_MS = 5_000L
    }
}
