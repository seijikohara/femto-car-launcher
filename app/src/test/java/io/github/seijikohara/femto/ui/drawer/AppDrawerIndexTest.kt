package io.github.seijikohara.femto.ui.drawer

import org.junit.Test
import kotlin.test.assertEquals

class AppDrawerIndexTest {
    @Test
    fun `sectionKeyOf uppercases the leading letter`() {
        assertEquals("M", sectionKeyOf("maps"))
        assertEquals("M", sectionKeyOf("Music"))
    }

    @Test
    fun `sectionKeyOf buckets a label with no leading letter under the shared key`() {
        assertEquals(NON_LETTER_SECTION_KEY, sectionKeyOf("1Password"))
        assertEquals(NON_LETTER_SECTION_KEY, sectionKeyOf("#Hashtag"))
        assertEquals(NON_LETTER_SECTION_KEY, sectionKeyOf(""))
    }

    @Test
    fun `sectionKeyOf trims leading whitespace before bucketing`() = assertEquals("M", sectionKeyOf("  Maps"))

    @Test
    fun `sectionStartIndices maps each bucket to its first item's flat index`() {
        val indices = sectionStartIndices(listOf("alpha", "Amazon", "Bravo", "Charlie")) { it }

        assertEquals(mapOf("A" to 0, "B" to 2, "C" to 3), indices)
    }

    @Test
    fun `sectionStartIndices orders keys by first appearance, not alphabetically`() {
        val indices = sectionStartIndices(listOf("Zebra", "Alpha", "Amazon")) { it }

        assertEquals(listOf("Z", "A"), indices.keys.toList())
    }

    @Test
    fun `sectionStartIndices never emits a bucket with no items`() {
        val indices = sectionStartIndices(listOf("Alpha", "Zebra")) { it }

        assertEquals(mapOf("A" to 0, "Z" to 1), indices)
    }

    @Test
    fun `sectionStartIndices returns an empty map for an empty input`() =
        assertEquals(emptyMap(), sectionStartIndices(emptyList<String>()) { it })

    @Test
    fun `sectionStartIndices buckets a label with no leading letter under the shared key`() =
        assertEquals(
            mapOf("A" to 0, NON_LETTER_SECTION_KEY to 1, "Z" to 2),
            sectionStartIndices(listOf("Alpha", "1Password", "Zebra")) { it },
        )

    @Test
    fun `letterIndexForOffset maps a proportional position to a letter index`() {
        val letters = 4
        val heightPx = 400f

        assertEquals(0, letterIndexForOffset(offsetY = 0f, heightPx = heightPx, letterCount = letters))
        assertEquals(0, letterIndexForOffset(offsetY = 50f, heightPx = heightPx, letterCount = letters))
        assertEquals(1, letterIndexForOffset(offsetY = 150f, heightPx = heightPx, letterCount = letters))
        assertEquals(2, letterIndexForOffset(offsetY = 250f, heightPx = heightPx, letterCount = letters))
        assertEquals(3, letterIndexForOffset(offsetY = 350f, heightPx = heightPx, letterCount = letters))
    }

    @Test
    fun `letterIndexForOffset clamps an offset past either end of the rail`() {
        assertEquals(0, letterIndexForOffset(offsetY = -20f, heightPx = 400f, letterCount = 4))
        assertEquals(3, letterIndexForOffset(offsetY = 4000f, heightPx = 400f, letterCount = 4))
        // Exactly the bottom edge must still resolve to the last letter, not roll over.
        assertEquals(3, letterIndexForOffset(offsetY = 400f, heightPx = 400f, letterCount = 4))
    }

    @Test
    fun `letterIndexForOffset degrades to 0 for a not-yet-measured rail or no letters`() {
        assertEquals(0, letterIndexForOffset(offsetY = 100f, heightPx = 0f, letterCount = 4))
        assertEquals(0, letterIndexForOffset(offsetY = 100f, heightPx = 400f, letterCount = 0))
    }
}
