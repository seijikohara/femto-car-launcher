package io.github.seijikohara.femto.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.FullscreenSetting
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val groupLabel = context.getString(R.string.settings_group_fullscreen)
    private val offLabel = context.getString(R.string.settings_fullscreen_off)
    private val onLabel = context.getString(R.string.settings_fullscreen_on)

    @Test
    fun renders_fullscreen_group_chips() {
        rule.setContent {
            FemtoTheme {
                SettingsScreen(
                    uiState = SettingsUiState.Initial,
                    onAction = {},
                    onBack = {},
                    onOpenNotificationAccess = {},
                    onOpenSystemSettings = {},
                )
            }
        }
        rule.onNodeWithText(groupLabel).assertIsDisplayed()
        rule.onNodeWithText(offLabel).assertIsDisplayed()
        rule.onNodeWithText(onLabel).assertIsDisplayed()
    }

    @Test
    fun tapping_on_dispatches_set_fullscreen_on() {
        val actions = mutableListOf<SettingsAction>()
        rule.setContent {
            FemtoTheme {
                SettingsScreen(
                    uiState = SettingsUiState.Initial,
                    onAction = { actions += it },
                    onBack = {},
                    onOpenNotificationAccess = {},
                    onOpenSystemSettings = {},
                )
            }
        }
        rule.onNodeWithText(onLabel).performClick()
        assertEquals(listOf(SettingsAction.SetFullscreen(FullscreenSetting.ON)), actions)
    }
}
