package io.github.seijikohara.femto.data.fonts

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FontSelectionTest {
    @Test
    fun `System selection has no google families`() {
        assertTrue(FontSelection.System.googleFamilies.isEmpty())
    }

    @Test
    fun `with sets only the targeted slot`() {
        val selection = FontSelection.System.with(FontSlot.LATIN, FontSource.GoogleFonts("Roboto"))
        assertEquals(FontSource.GoogleFonts("Roboto"), selection.latin)
        assertEquals(FontSource.SystemDefault, selection.cjk)
    }

    @Test
    fun `with SystemDefault clears the targeted slot`() {
        val selection =
            FontSelection(latin = FontSource.GoogleFonts("Roboto"), cjk = FontSource.GoogleFonts("Noto Sans JP"))
        assertEquals(FontSource.SystemDefault, selection.with(FontSlot.LATIN, FontSource.SystemDefault).latin)
    }

    @Test
    fun `sourceFor reads the matching slot`() {
        val selection = FontSelection(latin = FontSource.GoogleFonts("Inter"), cjk = FontSource.GoogleFonts("M PLUS 2"))
        assertEquals(FontSource.GoogleFonts("Inter"), selection.sourceFor(FontSlot.LATIN))
        assertEquals(FontSource.GoogleFonts("M PLUS 2"), selection.sourceFor(FontSlot.CJK))
    }

    @Test
    fun `googleFamilies deduplicates a face shared across both slots`() {
        val selection =
            FontSelection(latin = FontSource.GoogleFonts("Noto Sans JP"), cjk = FontSource.GoogleFonts("Noto Sans JP"))
        assertEquals(setOf("Noto Sans JP"), selection.googleFamilies)
    }

    @Test
    fun `googleFamilies excludes a system-installed selection`() {
        val selection =
            FontSelection(
                latin = FontSource.SystemFont("Roboto Condensed"),
                cjk = FontSource.GoogleFonts("Noto Sans JP"),
            )
        assertEquals(setOf("Noto Sans JP"), selection.googleFamilies)
    }
}
