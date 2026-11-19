package io.github.seijikohara.femto.data.map.offline

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortbreadNamesTest {
    private fun names(
        name: String?,
        nameEn: String?,
        nameDe: String?,
    ): Map<String, TileValue> = nameProperties(name, nameEn, nameDe).associate { it.key to it.value }

    private fun text(value: String): TileValue = TileValue.Text(value)

    @Test
    fun `a text is Latin when every letter in it is of the Latin script`() {
        listOf("Matsuyama", "Škoda–1", "ＪＲ", "123", "", "Cafe 😀").forEach { assertTrue(isLatinText(it), "'$it'") }
        listOf("松山", "Αθήνα", "松山 Matsuyama", "𠮷野家").forEach { assertFalse(isLatinText(it), "'$it'") }
    }

    @Test
    fun `a missing or empty name yields no properties`() {
        assertEquals(emptyList(), nameProperties(null, "Matsuyama", "Matsuyama"))
        assertEquals(emptyList(), nameProperties("", "Matsuyama", "Matsuyama"))
    }

    @Test
    fun `a Latin name fills name latin and falls back for the other languages`() {
        assertEquals(
            mapOf(
                "name" to text("Lefkosia"),
                "name_en" to text("Lefkosia"),
                "name_de" to text("Lefkosia"),
                "name:latin" to text("Lefkosia"),
                "name_int" to text("Lefkosia"),
            ),
            names("Lefkosia", null, null),
        )
    }

    @Test
    fun `a non-Latin name with a Latin English name fills both scripts`() {
        assertEquals(
            mapOf(
                "name" to text("松山市"),
                "name_en" to text("Matsuyama"),
                "name_de" to text("松山市"),
                "name:nonlatin" to text("松山市"),
                "name:latin" to text("Matsuyama"),
                "name_int" to text("Matsuyama"),
                "name:en" to text("Matsuyama"),
            ),
            names("松山市", "Matsuyama", null),
        )
    }

    @Test
    fun `an English name that repeats a non-Latin name adds no Latin name`() {
        assertEquals(
            mapOf(
                "name" to text("松山市"),
                "name_en" to text("松山市"),
                "name_de" to text("松山市"),
                "name:nonlatin" to text("松山市"),
                "name_int" to text("松山市"),
            ),
            names("松山市", "松山市", null),
        )
    }

    @Test
    fun `a Latin name keeps its own Latin form when the English name differs`() {
        assertEquals(
            mapOf(
                "name" to text("Lefkosia"),
                "name_en" to text("Nicosia"),
                "name_de" to text("Lefkosia"),
                "name:latin" to text("Lefkosia"),
                "name_int" to text("Lefkosia"),
                "name:en" to text("Nicosia"),
            ),
            names("Lefkosia", "Nicosia", null),
        )
    }
}
