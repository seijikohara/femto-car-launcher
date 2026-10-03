package io.github.seijikohara.femto.data.video

import io.github.seijikohara.femto.testfixtures.FakeVideoSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeVideoSourceGrants
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoSourceTest {
    @Test
    fun `a picked file is kept with its read grant`() =
        runTest {
            val store = FakeVideoSettingsStore()
            val grants = FakeVideoSourceGrants()

            assertTrue(store.adoptSource(FIRST, grants))

            assertEquals(FIRST, store.current.sourceUri)
            assertEquals(setOf(FIRST), grants.held)
        }

    @Test
    fun `a new file releases the grant of the one it replaces`() =
        runTest {
            val store = FakeVideoSettingsStore(VideoSettings.Default.copy(sourceUri = FIRST))
            val grants = FakeVideoSourceGrants(held = setOf(FIRST))

            store.adoptSource(SECOND, grants)

            assertEquals(SECOND, store.current.sourceUri)
            assertEquals(setOf(SECOND), grants.held)
        }

    @Test
    fun `picking the same file again keeps its grant`() =
        runTest {
            val store = FakeVideoSettingsStore(VideoSettings.Default.copy(sourceUri = FIRST))
            val grants = FakeVideoSourceGrants(held = setOf(FIRST))

            store.adoptSource(FIRST, grants)

            assertEquals(setOf(FIRST), grants.held)
        }

    @Test
    fun `a file whose grant cannot be kept leaves the current one in place`() =
        runTest {
            val store = FakeVideoSettingsStore(VideoSettings.Default.copy(sourceUri = FIRST))
            val grants = FakeVideoSourceGrants(held = setOf(FIRST), grantable = setOf(FIRST))

            assertFalse(store.adoptSource(SECOND, grants))

            assertEquals(FIRST, store.current.sourceUri)
            assertEquals(setOf(FIRST), grants.held)
        }

    @Test
    fun `a lost write keeps the current file and its grant`() =
        runTest {
            val store = FakeVideoSettingsStore(VideoSettings.Default.copy(sourceUri = FIRST), dropsSourceWrites = true)
            val grants = FakeVideoSourceGrants(held = setOf(FIRST))

            assertFalse(store.adoptSource(SECOND, grants))

            assertEquals(FIRST, store.current.sourceUri)
            // The record still names the first file, so its grant stays, and
            // the grant just taken on a file nothing names is let go.
            assertEquals(setOf(FIRST), grants.held)
        }

    @Test
    fun `two picks at once leave exactly the recorded file's grant held`() =
        runTest {
            val store = FakeVideoSettingsStore(VideoSettings.Default.copy(sourceUri = FIRST))
            val grants = FakeVideoSourceGrants(held = setOf(FIRST))
            // The dashboard's pick is still writing when Settings' pick starts.
            val slowWrite = store.gateNextSourceWrite()
            val dashboardPick = async { store.adoptSource(SECOND, grants) }
            runCurrent()
            val settingsPick = async { store.adoptSource(THIRD, grants) }
            runCurrent()

            slowWrite.complete(Unit)
            advanceUntilIdle()
            dashboardPick.await()
            settingsPick.await()

            // Every grant the app keeps names the recorded file: none is left
            // orphaned to count against the persisted-grant limit.
            assertEquals(setOfNotNull(store.current.sourceUri), grants.held)
        }

    private companion object {
        const val FIRST = "content://com.example.documents/document/video%3A1"
        const val SECOND = "content://com.example.documents/document/video%3A2"
        const val THIRD = "content://com.example.documents/document/video%3A3"
    }
}
