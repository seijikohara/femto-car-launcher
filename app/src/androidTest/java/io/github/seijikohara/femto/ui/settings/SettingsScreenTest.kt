package io.github.seijikohara.femto.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
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
    private val fullscreenLabel = context.getString(R.string.settings_group_fullscreen)
    private val themeLabel = context.getString(R.string.settings_group_theme)
    private val darkLabel = context.getString(R.string.settings_theme_dark)
    private val showSecondsLabel = context.getString(R.string.settings_group_clock_seconds)
    private val tealAccentLabel = context.getString(R.string.settings_accent_teal)
    private val resetLabel = context.getString(R.string.settings_reset_to_defaults)
    private val resetConfirmLabel = context.getString(R.string.settings_reset_confirm)
    private val keepScreenOnLabel = context.getString(R.string.settings_keep_screen_on)
    private val lightSchemeLabel = context.getString(R.string.settings_group_map_scheme_light)
    private val darkSchemeLabel = context.getString(R.string.settings_group_map_scheme_dark)
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

    @Test
    fun renders_fullscreen_row() {
        setScreen()
        rule.onNodeWithText(fullscreenLabel).assertIsDisplayed()
    }

    @Test
    fun toggling_fullscreen_dispatches_set_fullscreen_off() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it })
        // Initial.fullscreen is now ON (the revised default), so tapping flips it off.
        rule.onNodeWithText(fullscreenLabel).performClick()
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
    fun selecting_mapbox_with_no_token_opens_token_dialog() {
        // Selecting Mapbox when no token is stored must open the entry dialog instead
        // of switching the backend — verified by the dialog title appearing.
        setScreen(uiState = SettingsUiState.Initial.copy(mapboxAccessToken = ""))
        rule.onNodeWithText(mapBackendLabel).performScrollTo().performClick()
        rule.onNodeWithText(mapboxLabel).performClick()
        rule.onNodeWithText(tokenLabel).assertIsDisplayed()
    }

    @Test
    fun saving_token_in_dialog_dispatches_single_atomic_action() {
        // Entering a token and tapping Save must dispatch exactly one
        // SaveMapboxToken action (the ViewModel persists the token and selects the
        // Mapbox backend together, so the gate never sees a blank-token MAPBOX).
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
        assertEquals(listOf(SettingsAction.SaveMapboxToken("pk.test")), actions)
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

    private fun setScreen(
        uiState: SettingsUiState = SettingsUiState.Initial,
        onAction: (SettingsAction) -> Unit = {},
    ) {
        rule.setContent {
            FemtoTheme {
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
    }
}
