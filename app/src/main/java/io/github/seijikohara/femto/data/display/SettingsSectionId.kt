package io.github.seijikohara.femto.data.display

import androidx.datastore.preferences.core.Preferences

/**
 * One entry per collapsible Settings-screen section, paired with the exact
 * [DisplayPreferences] keys it owns. This is the single mapping "reset this
 * section" reads from: `SettingsViewModel.onAction(ResetSection)` drives
 * [DisplaySettingsStore.resetKeys] with a section's [displayKeys], plus any
 * owned other-store reset the section needs (the font store for
 * [APPEARANCE], the location store for [LOCATION], the calendar store's
 * hidden-ID set for [PANELS] — see `SettingsViewModel` for the wiring).
 *
 * `SettingsSectionIdTest` asserts the union of every entry's [displayKeys]
 * equals [DisplayPreferences.ALL_KEYS], so a new persisted key that is not
 * assigned to a section fails that test instead of silently surviving every
 * section reset unnoticed.
 *
 * `System` has no entry here: it holds only action links and the global
 * "reset to defaults", no section-local settings of its own to reset.
 */
internal enum class SettingsSectionId(
    val displayKeys: Set<Preferences.Key<*>>,
) {
    APPEARANCE(
        setOf(
            DisplayPreferences.THEME_KEY,
            DisplayPreferences.ACCENT_KEY,
            DisplayPreferences.MAP_STYLE_KEY,
            DisplayPreferences.MAP_SCHEME_LIGHT_KEY,
            DisplayPreferences.MAP_SCHEME_DARK_KEY,
            DisplayPreferences.GLASS_BLUR_KEY,
            DisplayPreferences.GLASS_TINT_KEY,
            DisplayPreferences.FONT_BASE_SIZE_KEY,
            DisplayPreferences.FONT_WEIGHT_STEP_KEY,
            DisplayPreferences.FONT_LETTER_SPACING_KEY,
        ),
    ),
    SCREEN(
        setOf(
            DisplayPreferences.UI_SCALE_KEY,
            DisplayPreferences.ORIENTATION_KEY,
            DisplayPreferences.FULLSCREEN_KEY,
            DisplayPreferences.KEEP_SCREEN_ON_KEY,
            DisplayPreferences.DOCK_POSITION_KEY,
            DisplayPreferences.DRIVER_SIDE_KEY,
            DisplayPreferences.ASSISTANT_LAUNCH_KEY,
            DisplayPreferences.MOTION_TIER_KEY,
        ),
    ),
    UNITS(
        setOf(
            DisplayPreferences.SPEED_KEY,
            DisplayPreferences.TEMPERATURE_KEY,
            DisplayPreferences.CLOCK_KEY,
            DisplayPreferences.SHOW_CLOCK_SECONDS_KEY,
        ),
    ),
    MAP(
        setOf(
            DisplayPreferences.MAP_BACKEND_KEY,
            DisplayPreferences.MAPBOX_STYLE_KEY,
            DisplayPreferences.MAPBOX_TRAFFIC_KEY,
            DisplayPreferences.MAPBOX_ACCESS_TOKEN_KEY,
            DisplayPreferences.GOOGLE_MAPS_API_KEY_KEY,
            DisplayPreferences.GOOGLE_MAPS_MAP_ID_KEY,
            DisplayPreferences.GOOGLE_MAPS_MAP_TYPE_KEY,
            DisplayPreferences.GOOGLE_MAPS_TRAFFIC_KEY,
            DisplayPreferences.MAP_3D_BUILDINGS_KEY,
            DisplayPreferences.MAP_TERRAIN_KEY,
            DisplayPreferences.MAP_TILT_KEY,
            DisplayPreferences.MAP_ZOOM_KEY,
            DisplayPreferences.MAP_NORTH_UP_KEY,
            DisplayPreferences.MAP_MARKER_POS_KEY,
        ),
    ),

    // Every Location-section row (quality, interval, min distance, background
    // ranging) persists in the LOCATION store, not DisplayPreferences — an
    // empty set here; SettingsViewModel resets it entirely via the other-store
    // call for this section.
    LOCATION(emptySet()),

    PANELS(
        setOf(
            DisplayPreferences.SHOW_CALENDAR_KEY,
            DisplayPreferences.SHOW_WEATHER_KEY,
            DisplayPreferences.SHOW_MUSIC_KEY,
            DisplayPreferences.MUSIC_SPECTRUM_KEY,
            DisplayPreferences.MUSIC_SHOW_ALBUM_KEY,
            DisplayPreferences.MUSIC_SHOW_ART_KEY,
        ),
    ),
}
