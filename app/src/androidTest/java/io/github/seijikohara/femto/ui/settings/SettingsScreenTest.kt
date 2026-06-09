package io.github.seijikohara.femto.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.AccentColor
import io.github.seijikohara.femto.data.FullscreenSetting
import io.github.seijikohara.femto.data.ThemeMode
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

    @Test
    fun renders_fullscreen_row() {
        setScreen()
        rule.onNodeWithText(fullscreenLabel).assertIsDisplayed()
    }

    @Test
    fun toggling_fullscreen_dispatches_set_fullscreen_on() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it })
        // Initial.fullscreen is OFF, so tapping the row flips the switch to ON.
        rule.onNodeWithText(fullscreenLabel).performClick()
        assertEquals(listOf(SettingsAction.SetFullscreen(FullscreenSetting.ON)), actions)
    }

    @Test
    fun toggling_show_seconds_dispatches_set_show_clock_seconds_off() {
        val actions = mutableListOf<SettingsAction>()
        setScreen(onAction = { actions += it })
        // The row sits in the Units section, below the fold on a short head unit, so
        // scroll it into view first. Initial.showClockSeconds is true, so tapping the
        // row flips the switch off.
        rule.onNodeWithText(showSecondsLabel).performScrollTo().performClick()
        assertEquals(listOf(SettingsAction.SetShowClockSeconds(false)), actions)
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

    private fun setScreen(onAction: (SettingsAction) -> Unit = {}) {
        rule.setContent {
            FemtoTheme {
                SettingsScreen(
                    uiState = SettingsUiState.Initial,
                    onAction = onAction,
                    onBack = {},
                    onOpenNotificationAccess = {},
                    onOpenSystemSettings = {},
                )
            }
        }
    }
}
