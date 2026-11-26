package io.github.seijikohara.femto.data.music

import io.github.seijikohara.femto.testfixtures.fakeTrackIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The cache is the seam that makes the watch's binder reads observable without a binder: a counting `read` stands in
 * for `MediaController.getMetadata`, so a test sees how many reads a number of passes cost.
 */
class TrackIdentityCacheTest {
    private val cache = TrackIdentityCache<String>()
    private var reads = 0

    @Test
    fun `a second pass reads no metadata for a session already cached`() {
        repeat(3) { read("radio") }

        assertEquals(1, reads)
    }

    @Test
    fun `each session is read once`() {
        listOf("radio", "podcast", "radio", "podcast").forEach { read(it) }

        assertEquals(2, reads)
    }

    @Test
    fun `a session that published no metadata is cached as such`() {
        repeat(2) { read("radio", identity = null) }

        assertEquals(1, reads)
    }

    @Test
    fun `a cached session answers with what it published`() {
        read("radio", identity = fakeTrackIdentity("First"))

        assertEquals(fakeTrackIdentity("First"), read("radio", identity = fakeTrackIdentity("Second")))
    }

    @Test
    fun `after an invalidation every session is read once more, and with its new metadata`() {
        listOf("radio", "podcast").forEach { read(it, identity = fakeTrackIdentity("First")) }
        cache.invalidate()
        val answers = listOf("radio", "podcast", "radio").map { read(it, identity = fakeTrackIdentity("Second")) }

        assertEquals(4, reads)
        assertEquals(List(3) { fakeTrackIdentity("Second") }, answers)
    }

    @Test
    fun `an invalidation keeps no entry, not even a null one`() {
        read("radio", identity = null)
        cache.invalidate()

        assertEquals(fakeTrackIdentity("Now"), read("radio", identity = fakeTrackIdentity("Now")))
    }

    @Test
    fun `a session never read answers with what the read gives, null included`() {
        assertNull(read("radio", identity = null))
    }

    private fun read(
        key: String,
        identity: TrackIdentity? = fakeTrackIdentity(),
    ): TrackIdentity? =
        cache.identityOf(key) {
            reads++
            identity
        }
}
