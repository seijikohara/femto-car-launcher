package io.github.seijikohara.femto.data.music

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The lastPlayedDataStore delegate is a process-wide singleton bound to the
// first Application's filesDir, while Robolectric hands each test method a
// fresh Application (mirrors SavedPlacesPreferencesTest), so every test clears
// the raw store first.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LastPlayedPreferencesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private suspend fun clearedStore(): LastPlayedPreferences {
        context.lastPlayedDataStore.edit { it.clear() }
        return LastPlayedPreferences(context)
    }

    @Test
    fun a_saved_track_reads_back_from_a_fresh_instance() =
        runTest {
            clearedStore().save(fakeLastPlayedTrack())

            assertEquals(fakeLastPlayedTrack(), LastPlayedPreferences(context).track.first())
        }

    @Test
    fun a_track_without_an_artist_keeps_none() =
        runTest {
            val store = clearedStore()
            store.save(fakeLastPlayedTrack())
            store.save(fakeLastPlayedTrack(title = "Night Bus", artist = null))

            assertEquals(fakeLastPlayedTrack(title = "Night Bus", artist = null), store.track.first())
        }

    @Test
    fun forgetting_another_app_keeps_the_track() =
        runTest {
            val store = clearedStore()
            store.save(fakeLastPlayedTrack())
            store.forget("com.example.other")

            assertEquals(fakeLastPlayedTrack(), store.track.first())
        }

    @Test
    fun forgetting_its_app_removes_the_track() =
        runTest {
            val store = clearedStore()
            store.save(fakeLastPlayedTrack())
            store.forget(fakeLastPlayedTrack().packageName)

            assertNull(store.track.first())
        }

    @Test
    fun clearing_removes_the_track() =
        runTest {
            val store = clearedStore()
            store.save(fakeLastPlayedTrack())
            store.clear()

            assertNull(store.track.first())
        }
}
