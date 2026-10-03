package io.github.seijikohara.femto.data.video

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The videoDataStore delegate is a process-wide singleton bound to the first
// Application's filesDir, while Robolectric hands each test method a fresh
// Application (mirrors UpdatePreferencesTest), so every test clears the raw
// store first.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VideoPreferencesTest {
    @Test
    fun `an empty store reads the defaults`() =
        runTest {
            val store = clearedStore()

            assertEquals(VideoSettings.Default, store.settings.first())
        }

    @Test
    fun `the defaults keep the window off and the picture gate on`() {
        assertEquals(false, VideoSettings.Default.windowEnabled)
        assertEquals(true, VideoSettings.Default.hidePictureWhileDriving)
        assertNull(VideoSettings.Default.sourceUri)
    }

    @Test
    fun `every field round-trips`() =
        runTest {
            val store = clearedStore()

            store.setWindowEnabled(true)
            store.setHidePictureWhileDriving(false)
            store.setSourceUri(SOURCE)

            assertEquals(VideoSettings(true, false, SOURCE), store.settings.first())
        }

    @Test
    fun `a null source removes the record`() =
        runTest {
            val store = clearedStore()
            store.setSourceUri(SOURCE)

            store.setSourceUri(null)

            assertNull(store.settings.first().sourceUri)
        }

    @Test
    fun `a reset restores both switches and keeps the picked file`() =
        runTest {
            val store = clearedStore()
            store.setWindowEnabled(true)
            store.setHidePictureWhileDriving(false)
            store.setSourceUri(SOURCE)

            store.resetToDefaults()

            assertEquals(VideoSettings.Default.copy(sourceUri = SOURCE), store.settings.first())
        }

    private suspend fun clearedStore(): VideoPreferences {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.videoDataStore.edit { it.clear() }
        return VideoPreferences(context)
    }

    private companion object {
        const val SOURCE = "content://com.example.documents/document/video%3A42"
    }
}
