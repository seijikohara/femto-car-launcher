package io.github.seijikohara.femto.data.display

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drift guard for the section → key mapping: a [DisplayPreferences] key that
 * is not assigned to any [SettingsSectionId] would silently survive every
 * "reset this section" tap, so this test fails the moment such a key exists —
 * forcing a new persisted setting to be assigned to a section instead of
 * fixing this test with a workaround.
 */
class SettingsSectionIdTest {
    @Test
    fun `the union of every section's displayKeys equals every persisted DisplayPreferences key`() {
        val unionOfSectionKeys = SettingsSectionId.entries.flatMap { it.displayKeys }.toSet()
        assertEquals(DisplayPreferences.ALL_KEYS, unionOfSectionKeys)
    }

    @Test
    fun `no DisplayPreferences key is owned by more than one section`() {
        val allAssignments = SettingsSectionId.entries.flatMap { section -> section.displayKeys.map { it to section } }
        val ownersByKey = allAssignments.groupBy({ it.first }, { it.second })
        val keysWithMoreThanOneOwner = ownersByKey.filterValues { it.size > 1 }
        assertTrue(
            keysWithMoreThanOneOwner.isEmpty(),
            "keys owned by more than one section: $keysWithMoreThanOneOwner",
        )
    }
}
