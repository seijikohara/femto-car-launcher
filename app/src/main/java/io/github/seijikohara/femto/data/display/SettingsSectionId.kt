package io.github.seijikohara.femto.data.display

import androidx.datastore.preferences.core.Preferences

/**
 * One entry per collapsible Settings-screen section, paired with the exact
 * [DisplayPreferences] keys it owns. This is the single mapping a section reset
 * reads from: `SettingsViewModel` drives [DisplaySettingsStore.resetKeys] with
 * a section's [displayKeys] minus [ENTERED_VALUE_KEYS], plus any owned
 * other-store reset the section needs (the font store for [APPEARANCE], the
 * location store for [LOCATION], the calendar store's hidden-ID set for
 * [PANELS], the update store's auto-check setting for [UPDATES], the
 * navigation store for [NAVIGATION] — see `SettingsViewModel` for the wiring).
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
            DisplayPreferences.MAP_CUSTOM_STYLE_URL_KEY,
            DisplayPreferences.GLASS_BLUR_KEY,
            DisplayPreferences.GLASS_TINT_KEY,
            DisplayPreferences.GLASS_SHOW_BORDER_KEY,
            DisplayPreferences.GLASS_SHADOW_ENABLED_KEY,
            DisplayPreferences.GLASS_SHADOW_INTENSITY_KEY,
            DisplayPreferences.GLASS_SHADOW_SIZE_KEY,
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
            DisplayPreferences.DOCK_WIDTH_KEY,
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
            DisplayPreferences.GOOGLE_MAPS_API_KEY_KEY,
            DisplayPreferences.GOOGLE_MAPS_MAP_ID_KEY,
            DisplayPreferences.GOOGLE_MAPS_RENDERING_KEY,
            DisplayPreferences.GOOGLE_MAPS_MAP_TYPE_KEY,
            DisplayPreferences.GOOGLE_MAPS_TRAFFIC_KEY,
            DisplayPreferences.MAP_3D_BUILDINGS_KEY,
            DisplayPreferences.MAP_TERRAIN_KEY,
            DisplayPreferences.MAP_TILE_HOST_KEY,
            DisplayPreferences.OFFLINE_STORAGE_LIMIT_KEY,
            DisplayPreferences.MAP_TILT_KEY,
            DisplayPreferences.MAP_ZOOM_KEY,
            DisplayPreferences.MAP_NORTH_UP_KEY,
            DisplayPreferences.MAP_MARKER_POS_KEY,
        ),
    ),

    // Every Navigation-section row persists in the navigation store
    // (NavigationPreferences); SettingsViewModel resets that store for this section.
    NAVIGATION(emptySet()),

    // Every Location-section row persists in the LOCATION store (LocationPreferences),
    // not DisplayPreferences — an empty set here; SettingsViewModel resets it
    // entirely via the other-store call for this section.
    LOCATION(emptySet()),

    PANELS(
        setOf(
            DisplayPreferences.SHOW_CALENDAR_KEY,
            DisplayPreferences.SHOW_WEATHER_KEY,
            DisplayPreferences.SHOW_MUSIC_KEY,
            DisplayPreferences.CALENDAR_DATE_ONLY_KEY,
            DisplayPreferences.MUSIC_SPECTRUM_KEY,
            DisplayPreferences.MUSIC_SHOW_ALBUM_KEY,
            DisplayPreferences.MUSIC_SHOW_ART_KEY,
        ),
    ),

    // The Updates section's one setting, the daily check, persists in the
    // update store (UpdatePreferences), which also keeps the updater's
    // bookkeeping; SettingsViewModel resets only the setting there.
    UPDATES(emptySet()),
    ;

    internal companion object {
        /**
         * The keys that hold text the person typed in: the Google Maps key and Map
         * ID, the custom style URL and the tile host. Each still belongs to the
         * section whose rows show it, but no section reset deletes it: a value the
         * person typed in cannot be chosen again from a list, so only a reset that
         * asks for these values by name deletes them.
         *
         * The reset dialog names each of them in `settings_reset_entered_values_desc`;
         * change that string together with this set. `SettingsSectionIdTest` pins
         * the set, so a fifth key fails there until it is named in the string too.
         */
        val ENTERED_VALUE_KEYS: Set<Preferences.Key<*>> =
            setOf(
                DisplayPreferences.GOOGLE_MAPS_API_KEY_KEY,
                DisplayPreferences.GOOGLE_MAPS_MAP_ID_KEY,
                DisplayPreferences.MAP_CUSTOM_STYLE_URL_KEY,
                DisplayPreferences.MAP_TILE_HOST_KEY,
            )

        /**
         * What deleting the entered values deletes: [ENTERED_VALUE_KEYS] plus the
         * map provider. Without its key the Google Maps provider can only show its
         * missing-key notice, so the provider goes back to its default with them;
         * nothing else of [MAP] changes.
         */
        val ENTERED_VALUE_RESET_KEYS: Set<Preferences.Key<*>> =
            ENTERED_VALUE_KEYS + DisplayPreferences.MAP_BACKEND_KEY
    }
}
