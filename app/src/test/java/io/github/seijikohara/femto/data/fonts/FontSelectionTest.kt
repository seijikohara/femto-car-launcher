package io.github.seijikohara.femto.data.fonts

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FontSelectionTest {
    @Test
    fun `System selection has no families`() {
        assertTrue(FontSelection.System.families.isEmpty())
    }

    @Test
    fun `with sets only the targeted slot`() {
        val selection = FontSelection.System.with(FontSlot.LATIN, "Roboto")
        assertEquals("Roboto", selection.latinFamily)
        assertNull(selection.cjkFamily)
    }

    @Test
    fun `with null clears the targeted slot`() {
        val selection = FontSelection(latinFamily = "Roboto", cjkFamily = "Noto Sans JP")
        assertNull(selection.with(FontSlot.LATIN, null).latinFamily)
    }

    @Test
    fun `familyFor reads the matching slot`() {
        val selection = FontSelection(latinFamily = "Inter", cjkFamily = "M PLUS 2")
        assertEquals("Inter", selection.familyFor(FontSlot.LATIN))
        assertEquals("M PLUS 2", selection.familyFor(FontSlot.CJK))
    }

    @Test
    fun `families deduplicates a face shared across both slots`() {
        val selection = FontSelection(latinFamily = "Noto Sans JP", cjkFamily = "Noto Sans JP")
        assertEquals(setOf("Noto Sans JP"), selection.families)
    }
}
