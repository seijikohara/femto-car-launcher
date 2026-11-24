package io.github.seijikohara.femto.data.music

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.FakeLastPlayedStore
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedTrack
import io.github.seijikohara.femto.testfixtures.grantNotificationListenerAccess
import io.github.seijikohara.femto.testfixtures.revokeNotificationListenerAccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Turning notification access off forgets the last played track at once, through the listener's disconnect, rather
 * than when the dashboard is next shown; a disconnect with access still on keeps it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MusicSessionListenerServiceTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a disconnect after access was turned off forgets the track`() =
        runTest {
            val store = FakeLastPlayedStore(fakeLastPlayedTrack())
            revokeNotificationListenerAccess(application)

            forgetLastPlayedWithoutAccess(application, store)

            assertNull(store.current)
        }

    @Test
    fun `a disconnect with access still on keeps the track`() =
        runTest {
            val store = FakeLastPlayedStore(fakeLastPlayedTrack())
            grantNotificationListenerAccess(application)

            forgetLastPlayedWithoutAccess(application, store)

            assertEquals(fakeLastPlayedTrack(), store.current)
        }

    @Test
    fun `the listener's disconnect clears the stored track`() {
        // The real store and the real service: the clear runs past the
        // callback, so the test waits for the store to read empty.
        val store = LastPlayedPreferences(application)
        runBlocking {
            application.lastPlayedDataStore.edit { it.clear() }
            store.save(fakeLastPlayedTrack())
        }
        revokeNotificationListenerAccess(application)

        Robolectric
            .buildService(MusicSessionListenerService::class.java)
            .create()
            .get()
            .onListenerDisconnected()

        assertNull(runBlocking { withTimeout(STORE_WAIT_MS) { store.track.first { it == null } } })
    }

    private companion object {
        const val STORE_WAIT_MS = 5_000L
    }
}
