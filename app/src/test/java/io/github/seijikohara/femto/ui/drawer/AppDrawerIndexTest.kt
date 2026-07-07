package io.github.seijikohara.femto.ui.drawer

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `withSectionHeaders inserts one header per run of a shared bucket`() {
        val entries = withSectionHeaders(listOf("alpha", "Amazon", "Bravo", "Charlie")) { it }

        assertEquals(
            listOf(
                DrawerListEntry.Header("A"),
                DrawerListEntry.App("alpha"),
                DrawerListEntry.App("Amazon"),
                DrawerListEntry.Header("B"),
                DrawerListEntry.App("Bravo"),
                DrawerListEntry.Header("C"),
                DrawerListEntry.App("Charlie"),
            ),
            entries,
        )
    }

    @Test
    fun `withSectionHeaders never emits a header for a bucket with no items`() {
        val entries = withSectionHeaders(listOf("Alpha", "Zebra")) { it }

        assertEquals(
            listOf(
                DrawerListEntry.Header("A"),
                DrawerListEntry.App("Alpha"),
                DrawerListEntry.Header("Z"),
                DrawerListEntry.App("Zebra"),
            ),
            entries,
        )
    }

    @Test
    fun `withSectionHeaders returns an empty list for an empty input`() =
        assertEquals(emptyList(), withSectionHeaders(emptyList<String>()) { it })

    @Test
    fun `availableSectionKeys lists only the buckets present, in first-appearance order`() =
        assertEquals(
            listOf("A", NON_LETTER_SECTION_KEY, "Z"),
            availableSectionKeys(listOf("Alpha", "Amazon", "1Password", "Zebra")) { it },
        )

    @Test
    fun `headerIndexOf finds the flattened index of a bucket's header`() {
        val entries = withSectionHeaders(listOf("Alpha", "Bravo", "Charlie")) { it }

        // Header(A)=0, App(Alpha)=1, Header(B)=2, App(Bravo)=3, Header(C)=4, App(Charlie)=5.
        assertEquals(0, headerIndexOf(entries, "A"))
        assertEquals(2, headerIndexOf(entries, "B"))
        assertEquals(4, headerIndexOf(entries, "C"))
    }

    @Test
    fun `headerIndexOf returns null for an absent bucket`() {
        val entries = withSectionHeaders(listOf("Alpha")) { it }

        assertNull(headerIndexOf(entries, "Z"))
    }

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
