package io.github.seijikohara.femto.data

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleFontFamilyTest {
    @Test
    fun `japanese subset marks the family CJK-capable`() {
        val family = GoogleFontFamily("Noto Sans JP", subsets = listOf("latin", "japanese"))
        assertTrue(family.supportsJapanese)
        assertTrue(family.supportsCjk)
    }

    @Test
    fun `korean and chinese subsets are CJK-capable`() {
        assertTrue(GoogleFontFamily("Noto Sans KR", subsets = listOf("korean")).supportsCjk)
        assertTrue(GoogleFontFamily("Noto Sans SC", subsets = listOf("chinese-simplified")).supportsCjk)
    }

    @Test
    fun `a latin-only family is not CJK-capable`() {
        val family = GoogleFontFamily("Roboto", subsets = listOf("latin", "latin-ext"))
        assertFalse(family.supportsCjk)
    }

    @Test
    fun `every family fits the Latin slot`() {
        assertTrue(GoogleFontFamily("Roboto", subsets = listOf("latin")).fits(FontSlot.LATIN))
    }

    @Test
    fun `only CJK-capable families fit the CJK slot`() {
        assertFalse(GoogleFontFamily("Roboto", subsets = listOf("latin")).fits(FontSlot.CJK))
        assertTrue(GoogleFontFamily("Noto Sans JP", subsets = listOf("japanese")).fits(FontSlot.CJK))
    }
}
