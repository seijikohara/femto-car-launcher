package io.github.seijikohara.femto.data.display

import io.github.seijikohara.femto.testfixtures.FakeDisplaySettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ThemePresetTest {
    @Test
    fun default_settings_match_the_dynamic_preset() {
        // The picker highlights Dynamic out of the box, so its bundle must equal
        // DisplaySettings.Default — keep the two in step when either changes.
        assertEquals(
            ThemePresets.Dynamic,
            ThemePresets.matchingOrNull(
                accentColor = DisplaySettings.Default.accentColor,
                mapSchemeLight = DisplaySettings.Default.mapSchemeLight,
                mapSchemeDark = DisplaySettings.Default.mapSchemeDark,
            ),
        )
    }

    @Test
    fun applying_a_preset_writes_its_look_bundle() =
        runTest {
            val store = FakeDisplaySettingsStore()
            store.applyThemePreset(ThemePresets.Ocean)
            val settings = store.settings.first()
            assertEquals(ThemePresets.Ocean.accentColor, settings.accentColor)
            assertEquals(ThemePresets.Ocean.mapSchemeLight, settings.mapSchemeLight)
            assertEquals(ThemePresets.Ocean.mapSchemeDark, settings.mapSchemeDark)
        }

    @Test
    fun applying_a_preset_leaves_structural_settings_untouched() =
        runTest {
            // A theme is a skin, not a relayout: dock edge + panel visibility stay put.
            val store = FakeDisplaySettingsStore()
            store.applyThemePreset(ThemePresets.Forest)
            val settings = store.settings.first()
            assertEquals(DisplaySettings.Default.dockPosition, settings.dockPosition)
            assertEquals(DisplaySettings.Default.showCalendar, settings.showCalendar)
        }

    @Test
    fun fine_tuning_away_from_a_preset_matches_nothing() {
        // BLUE accent with the default (ACCENT) map schemes is not any preset's
        // exact bundle, so the picker shows no selection.
        assertNull(
            ThemePresets.matchingOrNull(
                accentColor = AccentColor.BLUE,
                mapSchemeLight = MapColorScheme.ACCENT,
                mapSchemeDark = MapColorScheme.ACCENT,
            ),
        )
    }
}
