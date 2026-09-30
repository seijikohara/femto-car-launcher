package io.github.seijikohara.femto.data.update

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * Decision table for [afterUpdateAction]: the home app tries to come back on
 * screen, and every install that may post a notification announces the
 * update. Android can refuse the home app's start without an error, so the
 * home app announces it too when it may; otherwise nothing is done.
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
                arrayOf(true, true, AfterUpdate(openLauncher = true, notify = true)),
                arrayOf(true, false, AfterUpdate(openLauncher = true, notify = false)),
                arrayOf(false, true, AfterUpdate(openLauncher = false, notify = true)),
                arrayOf(false, false, AfterUpdate(openLauncher = false, notify = false)),
            )
    }
}
