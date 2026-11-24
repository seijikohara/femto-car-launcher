package io.github.seijikohara.femto.data.places

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The inbox between the entry activity and the dashboard holds one place until
 * the dashboard takes it, and nothing after: a recreated dashboard reads the
 * same inbox and must find it empty.
 */
class ReceivedPlaceInboxTest {
    private val inbox = ReceivedPlaceInbox()

    @Test
    fun `a place waits until the dashboard takes it`() {
        inbox.offer(CASTLE)

        assertEquals(CASTLE, inbox.pending.value)
    }

    @Test
    fun `a taken place is gone for good`() {
        inbox.offer(CASTLE)

        inbox.handled(CASTLE)

        assertNull(inbox.pending.value)
    }

    @Test
    fun `a second place replaces one not yet taken`() {
        inbox.offer(CASTLE)

        inbox.offer(TOWER)

        assertEquals(TOWER, inbox.pending.value)
    }

    @Test
    fun `taking the older place keeps a newer one that arrived meanwhile`() {
        inbox.offer(CASTLE)
        inbox.offer(TOWER)

        inbox.handled(CASTLE)

        assertEquals(TOWER, inbox.pending.value)
    }

    private companion object {
        val CASTLE = ReceivedPlace.Found(PlaceTarget.Query("Matsuyama Castle"))
        val TOWER = ReceivedPlace.Found(PlaceTarget.Point(35.6586, 139.7454), "Tokyo Tower")
    }
}
