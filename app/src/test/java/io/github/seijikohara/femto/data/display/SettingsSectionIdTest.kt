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

    // Each entered value still belongs to the section whose rows show it; only
    // its deletion is held back from that section's reset.
    @Test
    fun `every entered value belongs to a section`() {
        val unionOfSectionKeys = SettingsSectionId.entries.flatMap { it.displayKeys }.toSet()
        assertTrue(unionOfSectionKeys.containsAll(SettingsSectionId.ENTERED_VALUE_KEYS))
    }

    // Pinned because the reset dialog names each entered value in
    // settings_reset_entered_values_desc ("Google Maps key, Map ID, custom style
    // URL, tile host"): a fifth key belongs in that string before it goes here.
    @Test
    fun `the entered values are exactly the four the reset dialog names`() {
        assertEquals(
            setOf(
                DisplayPreferences.GOOGLE_MAPS_API_KEY_KEY,
                DisplayPreferences.GOOGLE_MAPS_MAP_ID_KEY,
                DisplayPreferences.MAP_CUSTOM_STYLE_URL_KEY,
                DisplayPreferences.MAP_TILE_HOST_KEY,
            ),
            SettingsSectionId.ENTERED_VALUE_KEYS,
        )
    }

    @Test
    fun `the Map section owns the offline storage limit so Reset Map clears it`() {
        assertTrue(DisplayPreferences.OFFLINE_STORAGE_LIMIT_KEY in SettingsSectionId.MAP.displayKeys)
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
