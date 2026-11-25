package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.testfixtures.routingOfferedSettingsState
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.settings.SettingsScreen
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.settings.visibleSettingsCategories
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The "Reset to defaults" checklist: a ticked row per category Settings lists, the entered values unticked, Reset
 * only while a row is ticked, and an action that carries exactly the ticked rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h1600dp-mdpi")
class ResetSettingsDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<SettingsAction>()

    private fun setDialog(uiState: SettingsUiState = SettingsUiState.Initial) {
        rule.setContent {
            FemtoTheme {
                ResetSettingsDialog(
                    categories = visibleSettingsCategories(uiState),
                    onReset = { actions += it },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `every category is ticked and the entered values are not`() {
        setDialog()

        CATEGORIES.forEach { rule.onNodeWithText(it).assertIsOn() }
        rule.onNodeWithText(ENTERED_VALUES).assertIsOff()
        rule.onNodeWithText(ENTERED_VALUES_DESC).assertExists()
        rule.onNodeWithText(NOT_INCLUDED).assertExists()
    }

    @Test
    fun `the Navigation row shows only where Settings lists the category`() {
        setDialog()

        rule.onNodeWithText(NAVIGATION).assertDoesNotExist()
        rule.onNodeWithText(SYSTEM).assertDoesNotExist()
    }

    @Test
    fun `the Navigation row is ticked where Settings lists the category`() {
        setDialog(routingOfferedSettingsState())

        rule.onNodeWithText(NAVIGATION).assertIsOn()
    }

    @Test
    fun `Reset is disabled while nothing is ticked`() {
        setDialog()

        CATEGORIES.forEach { rule.onNodeWithText(it).performClick() }
        rule.onNodeWithText(RESET).assertIsNotEnabled()

        rule.onNodeWithText(ENTERED_VALUES).performClick()
        rule.onNodeWithText(RESET).assertIsEnabled()
    }

    @Test
    fun `Reset with the default ticks resets every listed category and keeps the entered values`() {
        setDialog()

        rule.onNodeWithText(RESET).performClick()

        assertEquals(
            listOf<SettingsAction>(SettingsAction.ResetSettings(LISTED_SECTIONS, deleteEnteredValues = false)),
            actions,
        )
    }

    @Test
    fun `Reset carries exactly the ticked rows`() {
        setDialog(routingOfferedSettingsState())

        rule.onNodeWithText("Map").performClick()
        rule.onNodeWithText("Updates").performClick()
        rule.onNodeWithText(ENTERED_VALUES).performClick()
        rule.onNodeWithText(RESET).performClick()

        assertEquals(
            listOf<SettingsAction>(
                SettingsAction.ResetSettings(
                    LISTED_SECTIONS + SettingsSectionId.NAVIGATION - SettingsSectionId.MAP - SettingsSectionId.UPDATES,
                    deleteEnteredValues = true,
                ),
            ),
            actions,
        )
    }

    // The System row hands the dialog the categories the screen lists.
    @Test
    fun `the System row's dialog follows the categories Settings lists`() {
        var uiState by mutableStateOf(SettingsUiState.Initial)
        rule.setContent {
            FemtoTheme {
                SettingsScreen(
                    uiState = uiState,
                    onAction = { actions += it },
                    onBack = {},
                    onOpenNotificationAccess = {},
                    onOpenSystemSettings = {},
                    onOpenFontPicker = {},
                    onOpenOfflineRegions = {},
                    onOpenRoutingData = {},
                    onOpenDiagnostics = {},
                    onOpenLicenses = {},
                    onOpenDocument = {},
                    initialCategory = SettingsCategoryId.SYSTEM,
                )
            }
        }

        rule.onNodeWithText(RESET_TO_DEFAULTS).performClick()
        rule.onNode(hasText(NAVIGATION) and isToggleable()).assertDoesNotExist()

        uiState = routingOfferedSettingsState()
        rule.onNode(hasText(NAVIGATION) and isToggleable()).assertIsOn()
    }

    private companion object {
        val CATEGORIES = listOf("Appearance", "Screen", "Units", "Map", "Location", "Panels", "Updates")
        val LISTED_SECTIONS =
            setOf(
                SettingsSectionId.APPEARANCE,
                SettingsSectionId.SCREEN,
                SettingsSectionId.UNITS,
                SettingsSectionId.MAP,
                SettingsSectionId.LOCATION,
                SettingsSectionId.PANELS,
                SettingsSectionId.UPDATES,
            )
        const val NAVIGATION = "Navigation"
        const val SYSTEM = "System"
        const val RESET = "Reset"
        const val RESET_TO_DEFAULTS = "Reset to defaults"
        const val ENTERED_VALUES = "Keys and addresses you entered"
        const val ENTERED_VALUES_DESC = "Google Maps key, Map ID, custom style URL, tile host"
        const val NOT_INCLUDED =
            "The dock, pinned and hidden apps, saved places and the track history are not reset here."
    }
}
