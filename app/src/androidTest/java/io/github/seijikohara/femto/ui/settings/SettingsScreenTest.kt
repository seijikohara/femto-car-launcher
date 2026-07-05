package io.github.seijikohara.femto.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.GoogleMapType
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.testfixtures.fakeCalendarInfo
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appearanceSectionLabel = context.getString(R.string.settings_section_appearance)
    private val screenSectionLabel = context.getString(R.string.settings_section_screen)
    private val drivingSectionLabel = context.getString(R.string.settings_section_driving)
    private val driverSideLabel = context.getString(R.string.settings_group_driver_side)
    private val fullscreenLabel = context.getString(R.string.settings_group_fullscreen)
    private val themeLabel = context.getString(R.string.settings_group_theme)
    private val darkLabel = context.getString(R.string.settings_theme_dark)
    private val showSecondsLabel = context.getString(R.string.settings_group_clock_seconds)
    private val tealAccentLabel = context.getString(R.string.settings_accent_teal)
    private val customPresetLabel = context.getString(R.string.settings_theme_preset_custom)
    private val resetLabel = context.getString(R.string.settings_reset_to_defaults)
    private val resetConfirmLabel = context.getString(R.string.settings_reset_confirm)
    private val keepScreenOnLabel = context.getString(R.string.settings_keep_screen_on)
    private val lightSchemeLabel = context.getString(R.string.settings_group_map_scheme_light)
    private val darkSchemeLabel = context.getString(R.string.settings_group_map_scheme_dark)
    private val matchAppThemeLabel = context.getString(R.string.settings_map_match_theme)
    private val mapStyleLabel = context.getString(R.string.settings_group_map_style)
    private val mapBackendLabel = context.getString(R.string.settings_map_backend)
    private val mapboxLabel = context.getString(R.string.settings_map_backend_mapbox)
    private val tokenLabel = context.getString(R.string.settings_mapbox_token)
    private val tokenUnsetLabel = context.getString(R.string.settings_mapbox_token_unset)
    private val tokenHintLabel = context.getString(R.string.settings_mapbox_token_hint)
    private val tokenSaveLabel = context.getString(R.string.settings_mapbox_token_save)
    private val tokenClearLabel = context.getString(R.string.settings_mapbox_token_clear)
    private val glassBlurLabel = context.getString(R.string.settings_group_glass_blur)
    private val locationIntervalLabel = context.getString(R.string.settings_group_location_interval)
    private val visibleCalendarsLabel = context.getString(R.string.settings_visible_calendars)
    private val googleMapsLabel = context.getString(R.string.settings_map_backend_googlemaps)
    private val googleMapsKeyLabel = context.getString(R.string.settings_google_maps_key)
    private val googleMapsKeyUnsetLabel = context.getString(R.string.settings_google_maps_key_unset)
    private val googleMapsKeyHintLabel = context.getString(R.string.settings_google_maps_key_hint)
    private val googleMapsKeySaveLabel = context.getString(R.string.settings_google_maps_key_save)
    private val googleMapsKeyClearLabel = context.getString(R.string.settings_google_maps_key_clear)
    private val googleMapsTypeLabel = context.getString(R.string.settings_google_maps_type)
    private val googleMapsTrafficLabel = context.getString(R.string.settings_google_maps_traffic)
    private val googleMapsMapIdLabel = context.getString(R.string.settings_google_maps_map_id)
    private val googleMapsMapIdUnsetLabel = context.getString(R.string.settings_google_maps_map_id_unset)
    private val googleMapsMapIdHintLabel = context.getString(R.string.settings_google_maps_map_id_hint)
    private val googleMapsMapIdSaveLabel = context.getString(R.string.settings_google_maps_map_id_save)
    private val googleMapsMapIdClearLabel = context.getString(R.string.settings_google_maps_map_id_clear)
    private val accentOsmOnlyNoteLabel = context.getString(R.string.settings_map_accent_osm_only_note)
    private val mapRenderingSubheaderLabel = context.getString(R.string.settings_subheader_map_rendering)

    @Test
    fun renders_fullscreen_row() {
        setScreen()
        // The Screen section now sits below the larger Appearance section (which
        // absorbed the map-color rows), pushing Fullscreen below the fold on a
        // short head unit; scroll it into view first.
        rule.onNodeWithText(fullscreenLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun renders_appearance_section() {
        setScreen()
        rule.onNodeWithText(appearanceSectionLabel).assertIsDisplayed()
    }

    @Test
    fun renders_screen_section() {
        setScreen()
        rule.onNodeWithText(screenSectionLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun toggling_fullscreen_dispatches_set_fullscreen_off() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it })
        // Initial.fullscreen is now ON (the revised default), so tapping flips it off.
        // Scroll first: see the comment on renders_fullscreen_row.
        rule.onNodeWithText(fullscreenLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetFullscreen(FullscreenSetting.OFF)), actions)
    }

    @Test
    fun toggling_show_seconds_dispatches_set_show_clock_seconds_on() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it })
        // The row sits in the Units section, below the fold on a short head unit, so
        // scroll it into view first. Initial.showClockSeconds is now false (the revised
        // default), so tapping the row flips the switch on.
        rule.onNodeWithText(showSecondsLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetShowClockSeconds(true)), actions)
    }

    @Test
    fun tapping_an_accent_swatch_dispatches_set_accent_color() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it })
        // The accent swatches scroll horizontally; bring the Teal chip into view,
        // then tapping it reports the matching AccentColor.
        rule.onNodeWithContentDescription(tealAccentLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetAccentColor(AccentColor.TEAL)), actions)
    }

    @Test
    fun theme_preset_row_shows_no_custom_chip_when_matching_dynamic_default() {
        // SettingsUiState.Initial is DYNAMIC + ACCENT/ACCENT, which matches
        // ThemePresets.Dynamic exactly, so no Custom chip should render.
        setScreen()
        rule.onNodeWithText(customPresetLabel).assertDoesNotExist()
    }

    @Test
    fun theme_preset_row_shows_custom_chip_when_accent_diverges_from_every_preset() {
        // PINK is not any preset's accent seed, so the bundle can't match.
        setScreen(uiState = SettingsUiState.Initial.copy(accentColor = AccentColor.PINK))
        rule.onNodeWithText(customPresetLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosing_theme_option_dispatches_set_theme_mode() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it })
        // The Theme row opens a radio dialog; picking "Dark" reports the choice
        // and closes the dialog.
        rule.onNodeWithText(themeLabel).performClick()
        rule.onNodeWithText(darkLabel).performClick()
        assertEquals(listOf(SettingsAction.SetThemeMode(ThemeMode.DARK)), actions)
    }

    @Test
    fun confirming_reset_dispatches_reset_to_defaults() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it })
        // The reset row sits at the bottom (System section); scroll it in, tap to
        // open the confirm dialog, then tap Reset to confirm.
        rule.onNodeWithText(resetLabel).performScrollTo().performClick()
        rule.onNodeWithText(resetConfirmLabel).performClick()
        assertEquals(listOf(SettingsAction.ResetToDefaults), actions)
    }

    @Test
    fun keep_screen_on_row_is_shown() {
        setScreen()
        rule.onNodeWithText(keepScreenOnLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun glass_blur_row_is_shown() {
        setScreen()
        rule.onNodeWithText(glassBlurLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun location_interval_row_is_shown() {
        setScreen()
        rule.onNodeWithText(locationIntervalLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun auto_map_style_shows_both_scheme_rows() {
        // AUTO can use either scheme (the system theme decides), so both rows show.
        setScreen(uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.AUTO))
        rule.onNodeWithText(lightSchemeLabel).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(darkSchemeLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun light_map_style_hides_the_dark_scheme_row() {
        // A fixed LIGHT style never uses the dark scheme, so that row is hidden.
        setScreen(uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.LIGHT))
        rule.onNodeWithText(lightSchemeLabel).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(darkSchemeLabel).assertDoesNotExist()
    }

    @Test
    fun dark_map_style_hides_the_light_scheme_row() {
        // A fixed DARK style never uses the light scheme, so that row is hidden.
        setScreen(uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.DARK))
        rule.onNodeWithText(darkSchemeLabel).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(lightSchemeLabel).assertDoesNotExist()
    }

    @Test
    fun map_style_override_choice_hidden_when_matching_app_theme() {
        // mapStyle == AUTO means "match app theme" is on, so the override
        // Light/Dark choice row must not be present.
        setScreen(uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.AUTO))
        rule.onNodeWithText(mapStyleLabel).assertDoesNotExist()
    }

    @Test
    fun map_style_override_choice_shown_and_dispatches_when_overridden() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.LIGHT),
            onAction = { actions += it },
        )
        rule.onNodeWithText(mapStyleLabel).performScrollTo().performClick()
        rule.onNodeWithText(darkLabel).performClick()
        assertEquals(listOf(SettingsAction.SetMapStyle(MapStyleSetting.DARK)), actions)
    }

    @Test
    fun turning_off_match_app_theme_dispatches_set_map_style_dark_when_app_is_dark() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(darkTheme = true, onAction = { actions += it })
        // Initial.mapStyle is AUTO, so the toggle starts checked; tapping unchecks it.
        rule.onNodeWithText(matchAppThemeLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetMapStyle(MapStyleSetting.DARK)), actions)
    }

    @Test
    fun turning_off_match_app_theme_dispatches_set_map_style_light_when_app_is_light() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(darkTheme = false, onAction = { actions += it })
        rule.onNodeWithText(matchAppThemeLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetMapStyle(MapStyleSetting.LIGHT)), actions)
    }

    @Test
    fun turning_on_match_app_theme_dispatches_set_map_style_auto() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.DARK),
            onAction = { actions += it },
        )
        rule.onNodeWithText(matchAppThemeLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetMapStyle(MapStyleSetting.AUTO)), actions)
    }

    @Test
    fun mapbox_token_row_shows_masked_summary_when_token_is_set() {
        // A set token shows only the last four characters prefixed with bullets,
        // keeping the credential off a shared in-car display.
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.MAPBOX,
                    mapboxAccessToken = "pk.abc123",
                ),
        )
        // "pk.abc123".takeLast(4) == "c123"
        rule.onNodeWithText("••••c123").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mapbox_token_row_shows_unset_label_when_no_token() {
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.MAPBOX,
                    mapboxAccessToken = "",
                ),
        )
        rule.onNodeWithText(tokenUnsetLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun selecting_mapbox_with_no_token_switches_backend_and_opens_token_dialog() {
        // Selecting Mapbox when no token is stored must switch the backend immediately
        // (so the map area shows the missing-token notice) AND open the entry dialog.
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapboxAccessToken = ""),
            onAction = { actions += it },
        )
        rule.onNodeWithText(mapBackendLabel).performScrollTo().performClick()
        rule.onNodeWithText(mapboxLabel).performClick()
        rule.onNodeWithText(tokenLabel).assertIsDisplayed()
        assertEquals(listOf(SettingsAction.SetMapBackend(MapBackend.MAPBOX)), actions)
    }

    @Test
    fun saving_token_in_dialog_dispatches_set_backend_then_save_token() {
        // Selecting Mapbox with no token dispatches SetMapBackend(MAPBOX) immediately,
        // then entering a token and tapping Save dispatches SaveMapboxToken (which also
        // atomically persists the backend in the ViewModel — keeping both in sync).
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapboxAccessToken = ""),
            onAction = { actions += it },
        )
        rule.onNodeWithText(mapBackendLabel).performScrollTo().performClick()
        rule.onNodeWithText(mapboxLabel).performClick()
        // The OutlinedTextField shows its label text when the field is empty; type into it.
        rule.onNodeWithText(tokenHintLabel).performTextInput("pk.test")
        rule.onNodeWithText(tokenSaveLabel).performClick()
        assertEquals(
            listOf(SettingsAction.SetMapBackend(MapBackend.MAPBOX), SettingsAction.SaveMapboxToken("pk.test")),
            actions,
        )
    }

    @Test
    fun clear_in_token_dialog_dispatches_clear_mapbox_token() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.MAPBOX,
                    mapboxAccessToken = "pk.old",
                ),
            onAction = { actions += it },
        )
        rule.onNodeWithText(tokenLabel).performScrollTo().performClick()
        rule.onNodeWithText(tokenClearLabel).performClick()
        assertEquals(listOf(SettingsAction.ClearMapboxToken), actions)
    }

    @Test
    fun googlemaps_type_and_traffic_rows_shown_when_backend_googlemaps_with_key() {
        // The map-type and traffic rows are only visible when the Google Maps backend is
        // active; a non-blank key means the backend can be selected.
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaTestKey",
                ),
        )
        rule.onNodeWithText(googleMapsTypeLabel).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(googleMapsTrafficLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun accent_osm_only_note_shown_for_mapbox_backend() {
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.MAPBOX,
                    mapboxAccessToken = "pk.test",
                ),
        )
        rule.onNodeWithText(accentOsmOnlyNoteLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun accent_osm_only_note_shown_for_google_maps_backend() {
        // The note already shows for Mapbox (see the test above); Google Maps is
        // equally non-recolorable and must show the same explanation, not leave
        // the gap unexplained.
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaTestKey",
                ),
        )
        rule.onNodeWithText(accentOsmOnlyNoteLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun map_rendering_subheader_hidden_for_non_osm_backend() {
        // The Rendering subheader (3D Buildings / Terrain) only applies to the OSM
        // backend; Mapbox renders those effects natively, so neither the header nor
        // its switches should appear once a non-OSM backend is selected.
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.MAPBOX,
                    mapboxAccessToken = "pk.test",
                ),
        )
        rule.onNodeWithText(mapRenderingSubheaderLabel).assertDoesNotExist()
    }

    @Test
    fun selecting_googlemaps_with_no_key_switches_backend_and_opens_key_dialog() {
        // Selecting Google Maps when no key is stored must switch the backend immediately
        // (so the map area shows the missing-key notice) AND open the entry dialog.
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState = SettingsUiState.Initial.copy(googleMapsApiKey = ""),
            onAction = { actions += it },
        )
        rule.onNodeWithText(mapBackendLabel).performScrollTo().performClick()
        rule.onNodeWithText(googleMapsLabel).performClick()
        rule.onNodeWithText(googleMapsKeyHintLabel).assertIsDisplayed()
        assertEquals(listOf(SettingsAction.SetMapBackend(MapBackend.GOOGLEMAPS)), actions)
    }

    @Test
    fun google_maps_key_row_shows_masked_summary_when_key_is_set() {
        // A set key shows only the last four characters prefixed with bullets,
        // keeping the credential off a shared in-car display.
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaTestKey",
                ),
        )
        // "AIzaTestKey".takeLast(4) == "tKey"
        rule.onNodeWithText("••••tKey").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun google_maps_key_row_shows_unset_label_when_no_key() {
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "",
                ),
        )
        rule.onNodeWithText(googleMapsKeyUnsetLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun saving_google_key_in_dialog_dispatches_set_backend_then_save_key() {
        // Selecting Google Maps with no key dispatches SetMapBackend(GOOGLEMAPS)
        // immediately, then entering a key and tapping Save dispatches SaveGoogleMapsKey
        // (which also atomically persists the backend in the ViewModel).
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState = SettingsUiState.Initial.copy(googleMapsApiKey = ""),
            onAction = { actions += it },
        )
        rule.onNodeWithText(mapBackendLabel).performScrollTo().performClick()
        rule.onNodeWithText(googleMapsLabel).performClick()
        // The disclosure is now plain body text and the field label duplicates the
        // dialog title, so target the only editable node (the OutlinedTextField).
        rule.onNode(hasSetTextAction()).performTextInput("AIzaNewKey")
        rule.onNodeWithText(googleMapsKeySaveLabel).performClick()
        assertEquals(
            listOf(SettingsAction.SetMapBackend(MapBackend.GOOGLEMAPS), SettingsAction.SaveGoogleMapsKey("AIzaNewKey")),
            actions,
        )
    }

    @Test
    fun clear_in_google_key_dialog_dispatches_clear_google_maps_key() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaOld",
                ),
            onAction = { actions += it },
        )
        rule.onNodeWithText(googleMapsKeyLabel).performScrollTo().performClick()
        rule.onNodeWithText(googleMapsKeyClearLabel).performClick()
        assertEquals(listOf(SettingsAction.ClearGoogleMapsKey), actions)
    }

    @Test
    fun choosing_googlemaps_map_type_dispatches_set_google_maps_map_type() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaTestKey",
                ),
            onAction = { actions += it },
        )
        val terrainLabel = context.getString(R.string.settings_google_maps_type_terrain)
        rule.onNodeWithText(googleMapsTypeLabel).performScrollTo().performClick()
        rule.onNodeWithText(terrainLabel).performClick()
        assertEquals(listOf(SettingsAction.SetGoogleMapsMapType(GoogleMapType.TERRAIN)), actions)
    }

    @Test
    fun google_maps_map_id_row_shown_when_backend_googlemaps_with_key() {
        // The optional Map ID row sits under the Google Maps backend block, below the
        // API-key row; a non-blank key keeps that block visible.
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaTestKey",
                ),
        )
        rule.onNodeWithText(googleMapsMapIdLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun google_maps_map_id_row_shows_unset_label_when_no_map_id() {
        // The Map ID is not secret, so the unset row shows the plain "not set" hint
        // rather than a masked summary.
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaTestKey",
                    googleMapsMapId = "",
                ),
        )
        rule.onNodeWithText(googleMapsMapIdUnsetLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun google_maps_map_id_row_shows_raw_value_when_set() {
        // A set Map ID is shown verbatim (no masking) because it is not a credential.
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaTestKey",
                    googleMapsMapId = "MAP_ID_42",
                ),
        )
        rule.onNodeWithText("MAP_ID_42").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun saving_map_id_in_dialog_dispatches_set_google_maps_map_id() {
        // Entering a Map ID and tapping Save dispatches SetGoogleMapsMapId; unlike the
        // key, this does not switch the backend (the key already did).
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaTestKey",
                    googleMapsMapId = "",
                ),
            onAction = { actions += it },
        )
        rule.onNodeWithText(googleMapsMapIdLabel).performScrollTo().performClick()
        // The hint marks the dialog as open; the field label duplicates the row title,
        // so target the only editable node (the OutlinedTextField).
        rule.onNodeWithText(googleMapsMapIdHintLabel).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("MAP_ID_99")
        rule.onNodeWithText(googleMapsMapIdSaveLabel).performClick()
        assertEquals(listOf(SettingsAction.SetGoogleMapsMapId("MAP_ID_99")), actions)
    }

    @Test
    fun clear_in_map_id_dialog_dispatches_clear_google_maps_map_id() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    mapBackend = MapBackend.GOOGLEMAPS,
                    googleMapsApiKey = "AIzaTestKey",
                    googleMapsMapId = "MAP_ID_OLD",
                ),
            onAction = { actions += it },
        )
        rule.onNodeWithText(googleMapsMapIdLabel).performScrollTo().performClick()
        rule.onNodeWithText(googleMapsMapIdClearLabel).performClick()
        assertEquals(listOf(SettingsAction.ClearGoogleMapsMapId), actions)
    }

    @Test
    fun toggling_a_calendar_dispatches_SetCalendarHidden() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState =
                SettingsUiState.Initial.copy(
                    showCalendar = true,
                    hasCalendarAccess = true,
                    availableCalendars = listOf(fakeCalendarInfo(id = 1L, displayName = "Personal")),
                    hiddenCalendarIds = emptySet(),
                ),
            onAction = { actions += it },
        )
        // Scroll the "Visible calendars" row into view and tap to open the dialog.
        rule.onNodeWithText(visibleCalendarsLabel).performScrollTo().performClick()
        // Tapping the "Personal" calendar row (currently shown) should hide it.
        rule.onNodeWithText("Personal").performClick()
        assertEquals(listOf(SettingsAction.SetCalendarHidden(id = 1L, hidden = true)), actions)
    }

    @Test
    fun section_rows_are_absent_until_the_header_is_tapped() {
        // Sections are collapsed by default, so the driver-side row (which now lives
        // under Driving) is not composed until the Driving header is tapped.
        setScreen(expandSections = false)
        rule.onNodeWithText(driverSideLabel).assertDoesNotExist()
        rule
            .onNodeWithContentDescription(expandCd(R.string.settings_section_driving))
            .performScrollTo()
            .performClick()
        rule.onNodeWithText(driverSideLabel).performScrollTo().assertIsDisplayed()
    }

    private fun setScreen(
        uiState: SettingsUiState = SettingsUiState.Initial,
        onAction: (SettingsAction) -> Unit = {},
        darkTheme: Boolean = false,
        // Sections collapse by default; most tests interact with rows, so expand
        // every section after composing. The collapse-behavior test opts out.
        expandSections: Boolean = true,
    ) {
        rule.setContent {
            FemtoTheme(darkTheme = darkTheme) {
                SettingsScreen(
                    uiState = uiState,
                    onAction = onAction,
                    onBack = {},
                    onOpenNotificationAccess = {},
                    onOpenSystemSettings = {},
                    onOpenFontPicker = {},
                    onOpenDiagnostics = {},
                    onOpenLicenses = {},
                    onOpenPrivacyPolicy = {},
                )
            }
        }
        if (expandSections) {
            expandAllSections()
        }
    }

    // Expand every collapsible section so the existing row-level interactions find
    // their targets, then scroll back to the top so no-scroll clicks and header
    // assertions start from a fresh-screen position. Header taps toggle only local
    // expand state — they dispatch no SettingsAction — so this never pollutes a
    // captured-action assertion.
    private fun expandAllSections() {
        listOf(
            R.string.settings_section_appearance,
            R.string.settings_section_screen,
            R.string.settings_section_driving,
            R.string.settings_section_units,
            R.string.settings_section_map,
            R.string.settings_section_location,
            R.string.settings_section_panels,
            R.string.settings_group_system,
        ).forEach { titleRes ->
            rule.onNodeWithContentDescription(expandCd(titleRes)).performScrollTo().performClick()
        }
        rule.onNodeWithContentDescription(collapseCd(R.string.settings_section_appearance)).performScrollTo()
    }

    private fun expandCd(titleRes: Int) =
        context.getString(R.string.settings_section_expand, context.getString(titleRes))

    private fun collapseCd(titleRes: Int) =
        context.getString(R.string.settings_section_collapse, context.getString(titleRes))
}
