package io.github.seijikohara.femto.data.video

import io.github.seijikohara.femto.testfixtures.FakeVideoSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeVideoSourceGrants
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

    private companion object {
        const val FIRST = "content://com.example.documents/document/video%3A1"
        const val SECOND = "content://com.example.documents/document/video%3A2"
    }
}
