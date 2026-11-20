package io.github.seijikohara.femto.ui.home.components

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * Decision table for [transportGapPx] at the transport row's 64 + 72 + 64
 * buttons (200 px at mdpi) and its 24 px preferred gap: the preferred gap
 * while there is room, an exact fit below that, and the plain-row fallback
 * (null) once the buttons alone overflow.
 */
@RunWith(Parameterized::class)
internal class TransportGapTest(
    private val availablePx: Int,
    private val expected: Int?,
) {
    @Test
    fun `fits the transport buttons into the available width`() {
        assertEquals(expected, transportGapPx(availablePx = availablePx, buttonsPx = 200, preferredGapPx = 24))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "available={0} -> gap={1}")
        fun cases(): List<Array<Any?>> =
            listOf(
                arrayOf(Int.MAX_VALUE, 24),
                arrayOf(400, 24),
                arrayOf(248, 24),
                arrayOf(247, 23),
                arrayOf(220, 10),
                arrayOf(201, 0),
                arrayOf(200, 0),
                arrayOf(199, null),
                arrayOf(0, null),
            )
    }
}
