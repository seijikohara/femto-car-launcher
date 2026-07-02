package io.github.seijikohara.femto.data.music

import androidx.media3.common.Player
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

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
    fun `repeat mode maps player ints and back`() {
        assertEquals(RepeatMode.ONE, repeatModeOf(Player.REPEAT_MODE_ONE))
        assertEquals(RepeatMode.ALL, repeatModeOf(Player.REPEAT_MODE_ALL))
        assertEquals(RepeatMode.NONE, repeatModeOf(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.ONE.toPlayerMode())
        assertEquals(Player.REPEAT_MODE_ALL, RepeatMode.ALL.toPlayerMode())
        assertEquals(Player.REPEAT_MODE_OFF, RepeatMode.NONE.toPlayerMode())
    }
}
