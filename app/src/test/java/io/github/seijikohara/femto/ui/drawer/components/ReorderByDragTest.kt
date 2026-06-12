package io.github.seijikohara.femto.ui.drawer.components

import org.junit.Assert.assertEquals
import org.junit.Test

private const val STEP = 100f

class ReorderByDragTest {
    private val items = listOf("a", "b", "c", "d")

    @Test
    fun `a small delta moves nothing and keeps the residual`() {
        val (reordered, residual) = reorderByDrag(items, fromIndex = 1, dragDelta = 40f, stepPx = STEP)
        assertEquals(items, reordered)
        assertEquals(40f, residual)
    }

    @Test
    fun `crossing half a slot to the right swaps one position and rebases the delta`() {
        val (reordered, residual) = reorderByDrag(items, fromIndex = 1, dragDelta = 60f, stepPx = STEP)
        assertEquals(listOf("a", "c", "b", "d"), reordered)
        assertEquals(-40f, residual)
    }

    @Test
    fun `crossing half a slot to the left swaps one position`() {
        val (reordered, residual) = reorderByDrag(items, fromIndex = 2, dragDelta = -60f, stepPx = STEP)
        assertEquals(listOf("a", "c", "b", "d"), reordered)
        assertEquals(40f, residual)
    }

    @Test
    fun `a fast fling crosses multiple slots in one frame`() {
        // 260 px crosses the half-slot boundaries at 50/150/250 px: three swaps,
        // leaving the item rebased 40 px short of its new slot centre.
        val (reordered, residual) = reorderByDrag(items, fromIndex = 0, dragDelta = 260f, stepPx = STEP)
        assertEquals(listOf("b", "c", "d", "a"), reordered)
        assertEquals(-40f, residual)
    }

    @Test
    fun `the first item cannot move further left`() {
        val (reordered, residual) = reorderByDrag(items, fromIndex = 0, dragDelta = -500f, stepPx = STEP)
        assertEquals(items, reordered)
        assertEquals(-500f, residual)
    }

    @Test
    fun `the last item cannot move further right`() {
        val (reordered, residual) = reorderByDrag(items, fromIndex = 3, dragDelta = 500f, stepPx = STEP)
        assertEquals(items, reordered)
        assertEquals(500f, residual)
    }

    @Test
    fun `an out-of-range index is a no-op`() {
        val (reordered, residual) = reorderByDrag(items, fromIndex = 9, dragDelta = 500f, stepPx = STEP)
        assertEquals(items, reordered)
        assertEquals(500f, residual)
    }

    @Test
    fun `a non-positive step is a no-op`() {
        val (reordered, residual) = reorderByDrag(items, fromIndex = 1, dragDelta = 500f, stepPx = 0f)
        assertEquals(items, reordered)
        assertEquals(500f, residual)
    }
}
