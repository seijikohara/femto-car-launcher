package io.github.seijikohara.femto.data.fonts

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class GroupIntoFamiliesTest {
    @Test
    fun `files sharing a name-table family are grouped together`() {
        val regular = File("/fonts/Roboto-Regular.ttf")
        val bold = File("/fonts/Roboto-Bold.ttf")

        val groups = groupIntoFamilies(listOf(regular, bold)) { "Roboto" }

        assertEquals(mapOf("Roboto" to listOf(regular, bold)), groups)
    }

    @Test
    fun `a file the name reader cannot parse falls back to a cleaned filename`() {
        val file = File("/fonts/SomeFace-Bold.ttf")

        val groups = groupIntoFamilies(listOf(file)) { null }

        assertEquals(setOf("SomeFace"), groups.keys)
    }

    @Test
    fun `fallback filename cleaning strips style tokens but keeps an unrecognised name stable`() {
        val condensed = File("/fonts/RobotoCondensed-Italic.ttf")

        val groups = groupIntoFamilies(listOf(condensed)) { null }

        assertEquals(setOf("RobotoCondensed"), groups.keys)
    }

    @Test
    fun `a blank name-table read also falls back to the filename`() {
        val file = File("/fonts/Lobster-Regular.ttf")

        val groups = groupIntoFamilies(listOf(file)) { "   " }

        assertEquals(setOf("Lobster"), groups.keys)
    }
}
