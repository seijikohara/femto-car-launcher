package io.github.seijikohara.femto.data.diagnostics

import android.app.ApplicationExitInfo
import org.junit.Test
import kotlin.test.assertEquals

class ExitReasonsTest {
    @Test
    fun `exitReasonName maps REASON_CRASH to its suffix name`() {
        assertEquals("CRASH", exitReasonName(ApplicationExitInfo.REASON_CRASH))
    }

    @Test
    fun `exitReasonName maps REASON_ANR to its suffix name`() {
        assertEquals("ANR", exitReasonName(ApplicationExitInfo.REASON_ANR))
    }

    @Test
    fun `exitReasonName maps REASON_LOW_MEMORY to its suffix name`() {
        assertEquals("LOW_MEMORY", exitReasonName(ApplicationExitInfo.REASON_LOW_MEMORY))
    }

    @Test
    fun `exitReasonName falls back to REASON_n for a value with no known constant`() {
        assertEquals("REASON_999", exitReasonName(999))
    }
}
