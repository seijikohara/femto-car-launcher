package io.github.seijikohara.femto.data.fonts

import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstalledFontFamiliesTest {
    @Test
    fun `a family fits Latin only when its representative file covers both probe letters`() =
        runTest {
            val file = File("/fonts/OnlyUppercase-Regular.ttf")
            val fileSource = SystemFontFileSource { listOf(file) }
            // Covers "A" but not "g": an uppercase-only display face should not
            // report Latin fitness.
            val checker = GlyphCoverageChecker { _, characters -> characters.filter { it == "A" }.toSet() }

            val families = installedFontFamilies(fileSource, checker) { "Only Uppercase" }

            assertFalse(families.single().supportsLatin)
        }

    @Test
    fun `a family fits CJK when its representative file covers just one CJK script`() =
        runTest {
            val file = File("/fonts/NotoSansJP-Regular.ttf")
            val fileSource = SystemFontFileSource { listOf(file) }
            // Only Hiragana ("あ"), no Han or Hangul — still a valid CJK fallback,
            // mirroring GoogleFontFamily.supportsCjk's per-subset "any" semantics.
            val checker = GlyphCoverageChecker { _, characters -> characters.filter { it == "あ" }.toSet() }

            val families = installedFontFamilies(fileSource, checker) { "Noto Sans JP" }

            assertTrue(families.single().supportsCjk)
        }

    @Test
    fun `families are sorted case-insensitively by name`() =
        runTest {
            val files = listOf(File("/fonts/zeta-Regular.ttf"), File("/fonts/Alpha-Regular.ttf"))
            val fileSource = SystemFontFileSource { files }
            val checker = GlyphCoverageChecker { _, _ -> emptySet() }

            val families =
                installedFontFamilies(fileSource, checker) { file -> file.nameWithoutExtension.substringBefore("-") }

            assertEquals(listOf("Alpha", "zeta"), families.map { it.familyName })
        }

    @Test
    fun `the representative file is the one closest to the normal weight`() =
        runTest {
            val bold = File("/fonts/Roboto-Bold.ttf")
            val regular = File("/fonts/Roboto-Regular.ttf")
            val fileSource = SystemFontFileSource { listOf(bold, regular) }
            val probed = mutableListOf<File>()
            val checker =
                GlyphCoverageChecker { file, characters ->
                    probed += file
                    characters.toSet()
                }

            installedFontFamilies(fileSource, checker) { "Roboto" }

            // Both files share one family; only the Regular weight should be
            // probed for glyph coverage, not every file in the family.
            assertEquals(listOf(regular), probed)
        }
}
