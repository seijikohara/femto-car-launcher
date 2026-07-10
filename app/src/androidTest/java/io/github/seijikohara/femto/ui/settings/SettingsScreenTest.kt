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
    private val driverSideLabel = context.getString(R.string.settings_group_driver_side)
    private val motionLabel = context.getString(R.string.settings_group_motion)
    private val dockPositionLabel = context.getString(R.string.settings_group_dock_position)
    private val fullscreenLabel = context.getString(R.string.settings_group_fullscreen)
    private val themeLabel = context.getString(R.string.settings_group_theme)
    private val darkLabel = context.getString(R.string.settings_theme_dark)
    private val showSecondsLabel = context.getString(R.string.settings_group_clock_seconds)
    private val tealAccentLabel = context.getString(R.string.settings_accent_teal)
    private val resetLabel = context.getString(R.string.settings_reset_to_defaults)
    private val resetConfirmLabel = context.getString(R.string.settings_reset_confirm)
    private val resetDockLabel = context.getString(R.string.settings_reset_dock)
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
        setScreen(category = R.string.settings_section_screen)
        rule.onNodeWithText(fullscreenLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun appearance_category_is_selected_by_default() {
        // Appearance is the master-detail layout's default category (both the
        // wide rail and the narrow list start on it), so its rows show without
        // any navigation — no `category` passed to setScreen here.
        setScreen()
        rule.onNodeWithText(themeLabel).assertIsDisplayed()
    }

    @Test
    fun renders_screen_section() {
        setScreen(category = R.string.settings_section_screen)
        rule.onNodeWithText(dockPositionLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun toggling_fullscreen_dispatches_set_fullscreen_off() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it }, category = R.string.settings_section_screen)
        // Initial.fullscreen is now ON (the revised default), so tapping flips it off.
        rule.onNodeWithText(fullscreenLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetFullscreen(FullscreenSetting.OFF)), actions)
    }

    @Test
    fun toggling_show_seconds_dispatches_set_show_clock_seconds_on() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it }, category = R.string.settings_section_units)
        // Initial.showClockSeconds is now false (the revised default), so tapping
        // the row flips the switch on.
        rule.onNodeWithText(showSecondsLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetShowClockSeconds(true)), actions)
    }

    @Test
    fun tapping_an_accent_swatch_dispatches_set_accent_color() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it }, category = R.string.settings_section_appearance)
        // The accent swatches scroll horizontally; bring the Teal chip into view,
        // then tapping it reports the matching AccentColor.
        rule.onNodeWithContentDescription(tealAccentLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetAccentColor(AccentColor.TEAL)), actions)
    }

    @Test
    fun choosing_theme_option_dispatches_set_theme_mode() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it }, category = R.string.settings_section_appearance)
        // The Theme row opens a radio dialog; picking "Dark" reports the choice
        // and closes the dialog.
        rule.onNodeWithText(themeLabel).performClick()
        rule.onNodeWithText(darkLabel).performClick()
        assertEquals(listOf(SettingsAction.SetThemeMode(ThemeMode.DARK)), actions)
    }

    @Test
    fun confirming_reset_dispatches_reset_to_defaults() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it }, category = R.string.settings_group_system)
        // The reset row sits at the bottom of the System category; scroll it in,
        // tap to open the confirm dialog, then tap Reset to confirm.
        rule.onNodeWithText(resetLabel).performScrollTo().performClick()
        rule.onNodeWithText(resetConfirmLabel).performClick()
        assertEquals(listOf(SettingsAction.ResetToDefaults), actions)
    }

    @Test
    fun confirming_reset_dock_dispatches_reset_dock() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it }, category = R.string.settings_section_screen)
        // The Reset-dock row lives in the Screen category and opens the same confirm
        // dialog (ResetRow) as Reset-to-defaults; confirming dispatches ResetDock.
        rule.onNodeWithText(resetDockLabel).performScrollTo().performClick()
        rule.onNodeWithText(resetConfirmLabel).performClick()
        assertEquals(listOf(SettingsAction.ResetDock), actions)
    }

    @Test
    fun keep_screen_on_row_is_shown() {
        setScreen(category = R.string.settings_section_screen)
        rule.onNodeWithText(keepScreenOnLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun glass_blur_row_is_shown() {
        setScreen(category = R.string.settings_section_appearance)
        rule.onNodeWithText(glassBlurLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun location_interval_row_is_shown() {
        setScreen(category = R.string.settings_section_location)
        rule.onNodeWithText(locationIntervalLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun auto_map_style_shows_both_scheme_rows() {
        // AUTO can use either scheme (the system theme decides), so both rows show.
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.AUTO),
            category = R.string.settings_section_appearance,
        )
        rule.onNodeWithText(lightSchemeLabel).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(darkSchemeLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun light_map_style_hides_the_dark_scheme_row() {
        // A fixed LIGHT style never uses the dark scheme, so that row is hidden.
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.LIGHT),
            category = R.string.settings_section_appearance,
        )
        rule.onNodeWithText(lightSchemeLabel).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(darkSchemeLabel).assertDoesNotExist()
    }

    @Test
    fun dark_map_style_hides_the_light_scheme_row() {
        // A fixed DARK style never uses the light scheme, so that row is hidden.
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.DARK),
            category = R.string.settings_section_appearance,
        )
        rule.onNodeWithText(darkSchemeLabel).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(lightSchemeLabel).assertDoesNotExist()
    }

    @Test
    fun map_style_override_choice_hidden_when_matching_app_theme() {
        // mapStyle == AUTO means "match app theme" is on, so the override
        // Light/Dark choice row must not be present.
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.AUTO),
            category = R.string.settings_section_appearance,
        )
        rule.onNodeWithText(mapStyleLabel).assertDoesNotExist()
    }

    @Test
    fun map_style_override_choice_shown_and_dispatches_when_overridden() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.LIGHT),
            onAction = { actions += it },
            category = R.string.settings_section_appearance,
        )
        rule.onNodeWithText(mapStyleLabel).performScrollTo().performClick()
        rule.onNodeWithText(darkLabel).performClick()
        assertEquals(listOf(SettingsAction.SetMapStyle(MapStyleSetting.DARK)), actions)
    }

    @Test
    fun turning_off_match_app_theme_dispatches_set_map_style_dark_when_app_is_dark() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(darkTheme = true, onAction = { actions += it }, category = R.string.settings_section_appearance)
        // Initial.mapStyle is AUTO, so the toggle starts checked; tapping unchecks it.
        rule.onNodeWithText(matchAppThemeLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetMapStyle(MapStyleSetting.DARK)), actions)
    }

    @Test
    fun turning_off_match_app_theme_dispatches_set_map_style_light_when_app_is_light() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(darkTheme = false, onAction = { actions += it }, category = R.string.settings_section_appearance)
        rule.onNodeWithText(matchAppThemeLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetMapStyle(MapStyleSetting.LIGHT)), actions)
    }

    @Test
    fun turning_on_match_app_theme_dispatches_set_map_style_auto() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(
            uiState = SettingsUiState.Initial.copy(mapStyle = MapStyleSetting.DARK),
            onAction = { actions += it },
            category = R.string.settings_section_appearance,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_map,
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
            category = R.string.settings_section_panels,
        )
        // Scroll the "Visible calendars" row into view and tap to open the dialog.
        rule.onNodeWithText(visibleCalendarsLabel).performScrollTo().performClick()
        // Tapping the "Personal" calendar row (currently shown) should hide it.
        rule.onNodeWithText("Personal").performClick()
        assertEquals(listOf(SettingsAction.SetCalendarHidden(id = 1L, hidden = true)), actions)
    }

    @Test
    fun driver_side_and_motion_rows_are_absent_until_screen_is_selected() {
        // Only the SELECTED category's rows are ever composed — Appearance is the
        // default, not Screen, so the driver-side row starts absent. Navigating to
        // Screen (via the rail in the wide layout, or the list in the narrow
        // layout — navigateToCategory works either way) then shows the driver-side
        // and motion rows.
        setScreen()
        rule.onNodeWithText(driverSideLabel).assertDoesNotExist()
        navigateToCategory(R.string.settings_section_screen)
        rule.onNodeWithText(driverSideLabel).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(motionLabel).performScrollTo().assertIsDisplayed()
    }

    private fun setScreen(
        uiState: SettingsUiState = SettingsUiState.Initial,
        onAction: (SettingsAction) -> Unit = {},
        darkTheme: Boolean = false,
        // The category to navigate to right after composing, or null to leave the
        // screen on its initial state (the wide rail's default selection / the
        // narrow layout's category list).
        category: Int? = null,
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
        if (category != null) {
            navigateToCategory(category)
        }
    }

    // Selects [titleRes]'s category via its "Select <title>" click target
    // (SettingsCategoryList). Works in both master-detail shapes: in the wide
    // layout the rail is always visible, so this just changes the selection; in
    // the narrow layout the category list is showing until this tap, which also
    // navigates to the detail view — either way, the category's rows are
    // composed and reachable afterward.
    private fun navigateToCategory(titleRes: Int) {
        rule.onNodeWithContentDescription(selectDescription(titleRes)).performScrollTo().performClick()
    }

    private fun selectDescription(titleRes: Int) =
        context.getString(R.string.settings_category_select, context.getString(titleRes))
}
