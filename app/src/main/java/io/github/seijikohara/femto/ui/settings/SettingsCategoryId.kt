package io.github.seijikohara.femto.ui.settings

import androidx.annotation.StringRes
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.SettingsSectionId

/**
 * One entry per category in the Settings master-detail layout (the left rail
 * in the wide layout, the list in the narrow list-detail layout): display
 * order, title, and the [SettingsSectionId] whose keys the category's
 * detail-pane reset affordance clears.
 *
 * [SYSTEM] has no [SettingsSectionId] counterpart — mirrors that enum's own
 * KDoc: System holds only action links and the global "reset to defaults",
 * no section-local settings of its own — so its [sectionId] is `null` and the
 * detail pane omits the per-category reset icon for it (see
 * `SettingsScreen`'s use of [sectionId] to build that icon's `onClick`).
 */
internal enum class SettingsCategoryId(
    @StringRes val titleRes: Int,
    val sectionId: SettingsSectionId?,
) {
    APPEARANCE(R.string.settings_section_appearance, SettingsSectionId.APPEARANCE),
    SCREEN(R.string.settings_section_screen, SettingsSectionId.SCREEN),
    UNITS(R.string.settings_section_units, SettingsSectionId.UNITS),
    MAP(R.string.settings_section_map, SettingsSectionId.MAP),
    LOCATION(R.string.settings_section_location, SettingsSectionId.LOCATION),
    PANELS(R.string.settings_section_panels, SettingsSectionId.PANELS),
    SYSTEM(R.string.settings_group_system, null),
}
