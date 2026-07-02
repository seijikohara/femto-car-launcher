package io.github.seijikohara.femto.data.music

import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayQueueTest {
    @Test
    fun `nextRepeatMode cycles none to all to one and back`() {
        assertEquals(RepeatMode.ALL, nextRepeatMode(RepeatMode.NONE))
        assertEquals(RepeatMode.ONE, nextRepeatMode(RepeatMode.ALL))
        assertEquals(RepeatMode.NONE, nextRepeatMode(RepeatMode.ONE))
    }

    @Test
    fun `upcomingQueue returns the entries after the active item`() {
        val entries = (1L..5L).map { QueueEntry(it, "Track $it", null) }

        assertEquals(listOf(4L, 5L), upcomingQueue(entries, activeQueueItemId = 3L).map { it.id })
    }

    @Test
    fun `upcomingQueue falls back to the queue head when the active id is unknown`() {
        val entries = (1L..3L).map { QueueEntry(it, "Track $it", null) }

        assertEquals(listOf(1L, 2L, 3L), upcomingQueue(entries, activeQueueItemId = -1L).map { it.id })
    }

    @Test
    fun `upcomingQueue caps the slice at the limit`() {
        val entries = (1L..40L).map { QueueEntry(it, "Track $it", null) }

        assertEquals(QUEUE_UPCOMING_LIMIT, upcomingQueue(entries, activeQueueItemId = 1L).size)
    }

    @Test
    fun `upcomingQueue is empty for an empty queue`() {
        assertEquals(emptyList(), upcomingQueue(emptyList(), activeQueueItemId = -1L))
    }

    @Test
    fun `shuffle mode maps to the boolean toggle`() {
        assertTrue(isShuffleOn(PlaybackStateCompat.SHUFFLE_MODE_ALL))
        assertTrue(isShuffleOn(PlaybackStateCompat.SHUFFLE_MODE_GROUP))
        assertFalse(isShuffleOn(PlaybackStateCompat.SHUFFLE_MODE_NONE))
        assertFalse(isShuffleOn(PlaybackStateCompat.SHUFFLE_MODE_INVALID))
    }

    @Test
    fun `repeat mode maps compat ints and back`() {
        assertEquals(RepeatMode.ONE, repeatModeOf(PlaybackStateCompat.REPEAT_MODE_ONE))
        assertEquals(RepeatMode.ALL, repeatModeOf(PlaybackStateCompat.REPEAT_MODE_ALL))
        assertEquals(RepeatMode.ALL, repeatModeOf(PlaybackStateCompat.REPEAT_MODE_GROUP))
        assertEquals(RepeatMode.NONE, repeatModeOf(PlaybackStateCompat.REPEAT_MODE_NONE))
        assertEquals(RepeatMode.NONE, repeatModeOf(PlaybackStateCompat.REPEAT_MODE_INVALID))
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ONE, RepeatMode.ONE.toCompatMode())
        assertEquals(PlaybackStateCompat.REPEAT_MODE_ALL, RepeatMode.ALL.toCompatMode())
        assertEquals(PlaybackStateCompat.REPEAT_MODE_NONE, RepeatMode.NONE.toCompatMode())
    }

    @Test
    fun `shuffleModeFor maps the toggle to compat ints`() {
        assertEquals(PlaybackStateCompat.SHUFFLE_MODE_ALL, shuffleModeFor(true))
        assertEquals(PlaybackStateCompat.SHUFFLE_MODE_NONE, shuffleModeFor(false))
    }
}
