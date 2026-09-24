package io.github.seijikohara.femto.data.update

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * Decision table for [afterUpdateAction]: the HOME role wins, since only the
 * home app may start an activity from the background; a notification serves
 * any other install that may post one; otherwise nothing is done.
 */
@RunWith(Parameterized::class)
internal class AfterUpdateActionTest(
    private val holdsHomeRole: Boolean,
    private val mayNotify: Boolean,
    private val expected: AfterUpdate,
) {
    @Test
    fun `decides how the launcher returns after an update`() {
        assertEquals(expected, afterUpdateAction(holdsHomeRole, mayNotify))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "home={0}, notify={1} -> {2}")
        fun cases(): List<Array<Any>> =
            listOf(
                arrayOf(true, true, AfterUpdate.OPEN_LAUNCHER),
                arrayOf(true, false, AfterUpdate.OPEN_LAUNCHER),
                arrayOf(false, true, AfterUpdate.NOTIFY),
                arrayOf(false, false, AfterUpdate.NOTHING),
            )
    }
}
