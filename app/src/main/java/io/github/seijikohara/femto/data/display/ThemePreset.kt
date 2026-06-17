package io.github.seijikohara.femto.data.display

/**
 * A named bundle of the "look" settings — the data-driven separation of theme
 * from the dashboard layout code (the CSS-to-HTML analogy: a preset is style, the
 * Compose tree stays structure). Applying a preset writes its fields into
 * [DisplaySettings] so the UI accent and both map colour schemes restyle at once.
 *
 * A preset carries only visual fields. Structural settings (dock edge, panel
 * visibility, units) stay independent so a theme is a skin, not a relayout, and
 * the automotive floors (CLAUDE.md#automotive-overrides) are never part of a
 * preset — they are code-enforced and must not be themeable.
 */
internal data class ThemePreset(
    val key: String,
    val accentColor: AccentColor,
    val mapSchemeLight: MapColorScheme,
    val mapSchemeDark: MapColorScheme,
)

/**
 * The shipped theme registry. Mass-producing a theme is one entry here plus its
 * display-name string; the settings picker renders [all] and applying writes the
 * bundle through [DisplaySettingsStore.applyThemePreset].
 */
internal object ThemePresets {
    // Dynamic mirrors DisplaySettings.Default (wallpaper accent + adaptive map).
    val Dynamic = ThemePreset("dynamic", AccentColor.DYNAMIC, MapColorScheme.ACCENT, MapColorScheme.ACCENT)
    val Ocean = ThemePreset("ocean", AccentColor.BLUE, MapColorScheme.POSITRON, MapColorScheme.DARK_MATTER)
    val Forest = ThemePreset("forest", AccentColor.GREEN, MapColorScheme.LIBERTY, MapColorScheme.FIORD)
    val Dusk = ThemePreset("dusk", AccentColor.AMBER, MapColorScheme.BRIGHT, MapColorScheme.DARK)

    val all: List<ThemePreset> = listOf(Dynamic, Ocean, Forest, Dusk)

    /**
     * The preset whose bundle matches the given look fields, or null when the
     * user has fine-tuned away from every preset. Lets the picker highlight the
     * active preset without storing a separate "selected preset" key.
     */
    fun matchingOrNull(
        accentColor: AccentColor,
        mapSchemeLight: MapColorScheme,
        mapSchemeDark: MapColorScheme,
    ): ThemePreset? =
        all.firstOrNull {
            it.accentColor == accentColor &&
                it.mapSchemeLight == mapSchemeLight &&
                it.mapSchemeDark == mapSchemeDark
        }
}
