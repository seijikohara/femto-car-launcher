package io.github.seijikohara.femto.ui.settings.components

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.fonts.FontSource
import io.github.seijikohara.femto.data.location.LocationQualitySetting
import io.github.seijikohara.femto.data.location.LocationSettings
import io.github.seijikohara.femto.data.routing.NavigationSettings
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.video.VideoSettings
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.FakeCalendarPreferencesStore
import io.github.seijikohara.femto.testfixtures.FakeDisplaySettingsStore
import io.github.seijikohara.femto.testfixtures.FakeFontSelectionStore
import io.github.seijikohara.femto.testfixtures.FakeLocationSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeNavigationSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeUpdateSettingsStore
import io.github.seijikohara.femto.testfixtures.FakeVideoSettingsStore
import io.github.seijikohara.femto.testfixtures.fakeSettingsViewModel
import io.github.seijikohara.femto.testfixtures.mutateAllAwayFromDefault
import io.github.seijikohara.femto.testfixtures.routingOfferedSettingsState
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.settings.SettingsScreen
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.settings.visibleSettingsCategories
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Reset to defaults" checklist: a ticked cell per category Settings lists, the entered values unticked, Reset
 * only while a row is ticked, every row in view on the head unit, and the reset it sends: every section while every
 * listed category is ticked, else exactly the ticked ones. The last tests send that reset to a view model on fake
 * stores, the path a tap on Reset takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h1600dp-mdpi")
class ResetSettingsDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<SettingsAction>()

    private fun setDialog(
        uiState: SettingsUiState = SettingsUiState.Initial,
        onReset: (SettingsAction.ResetSettings) -> Unit = { actions += it },
    ) {
        rule.setContent {
            FemtoTheme {
                ResetSettingsDialog(
                    categories = visibleSettingsCategories(uiState),
                    onReset = onReset,
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

    // Navigation is not listed here, and is reset all the same.
    @Test
    fun `Reset with every listed category ticked resets every section and keeps the entered values`() {
        setDialog()

        rule.onNodeWithText(RESET).performClick()

        assertEquals(
            listOf<SettingsAction>(
                SettingsAction.ResetSettings(SettingsSectionId.entries.toSet(), deleteEnteredValues = false),
            ),
            actions,
        )
    }

    @Test
    fun `Reset with a listed category unticked carries exactly the ticked rows`() {
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

    @Test
    fun `Reset with a listed category unticked leaves out a category that is not listed`() {
        setDialog()

        rule.onNodeWithText("Map").performClick()
        rule.onNodeWithText(RESET).performClick()

        assertEquals(
            listOf<SettingsAction>(
                SettingsAction.ResetSettings(LISTED_SECTIONS - SettingsSectionId.MAP, deleteEnteredValues = false),
            ),
            actions,
        )
    }

    // The head unit's reference geometry, at the default display size and font,
    // with the most categories Settings ever lists.
    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun `on the head unit every row, the note and both buttons show without scrolling`() {
        setDialog(routingOfferedSettingsState())

        (CATEGORIES + NAVIGATION).forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText(ENTERED_VALUES).assertIsDisplayed()
        rule.onNodeWithText(NOT_INCLUDED).assertIsDisplayed()
        rule.onNodeWithText(RESET).assertIsDisplayed()
        rule.onNodeWithText(CANCEL).assertIsDisplayed()
        // Displayed only means partly in view; a scroll range of zero means the
        // rows fit whole, the entered values' row included.
        assertEquals(0f, scrollRange().maxValue())
    }

    @Test
    @Config(qualifiers = "w853dp-h360dp-mdpi")
    fun `on a display too short for the rows they scroll`() {
        setDialog()

        assertTrue(scrollRange().maxValue() > 0f)
        rule.onNodeWithText(ENTERED_VALUES).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(RESET).assertIsDisplayed()
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

    // The old "Reset to defaults" emptied the display store and reset the font,
    // location, calendar, update, video and navigation stores. Every row ticked
    // must leave each of them as that did, Navigation included where it is not
    // listed.
    @Test
    fun `every row ticked restores every store as the full reset did`() {
        val stores = StoresAwayFromDefault()
        setDialog(onReset = stores.viewModel::onAction)

        rule.onNodeWithText(ENTERED_VALUES).performClick()
        rule.onNodeWithText(RESET).performClick()
        settle()

        runBlocking {
            assertEquals(DisplaySettings.Default, stores.display.settings.first())
            assertEquals(
                FontSource.SystemDefault,
                stores.fonts.selection
                    .first()
                    .latin,
            )
            assertEquals(
                FontSource.SystemDefault,
                stores.fonts.selection
                    .first()
                    .cjk,
            )
            assertEquals(LocationSettings.Default, stores.location.settings.first())
            assertEquals(emptySet(), stores.calendar.hiddenCalendarIds.first())
            assertEquals(UpdateSettings.Default.autoCheck, stores.update.current.autoCheck)
            assertEquals(VideoSettings.Default, stores.video.current)
            assertEquals(NavigationSettings.Default, stores.navigation.current)
        }
    }

    @Test
    fun `with Navigation not listed, every category ticked resets the navigation settings too`() {
        val stores = StoresAwayFromDefault()
        setDialog(onReset = stores.viewModel::onAction)

        rule.onNodeWithText(NAVIGATION).assertDoesNotExist()
        rule.onNodeWithText(RESET).performClick()
        settle()

        assertEquals(NavigationSettings.Default, stores.navigation.current)
    }

    @Test
    fun `with a listed category unticked, the navigation settings that are not listed stay`() {
        val stores = StoresAwayFromDefault()
        setDialog(onReset = stores.viewModel::onAction)

        rule.onNodeWithText("Map").performClick()
        rule.onNodeWithText(RESET).performClick()
        settle()

        assertEquals(false, stores.navigation.current.rerouteAutomatically)
        // The ticked categories were reset all the same.
        assertEquals(LocationSettings.Default, runBlocking { stores.location.settings.first() })
    }

    // Lets the view model's reset coroutine, started on the main thread, finish.
    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun scrollRange() =
        rule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]

    // Every store a reset touches, each moved off its defaults, behind one view model.
    private class StoresAwayFromDefault {
        val display = FakeDisplaySettingsStore()
        val fonts = FakeFontSelectionStore()
        val location = FakeLocationSettingsStore()
        val calendar = FakeCalendarPreferencesStore(initialHidden = setOf(1L))
        val update = FakeUpdateSettingsStore()
        val navigation = FakeNavigationSettingsStore()
        val video =
            FakeVideoSettingsStore(
                initial =
                    VideoSettings.Default.copy(
                        windowEnabled = !VideoSettings.Default.windowEnabled,
                        hidePictureWhileDriving = !VideoSettings.Default.hidePictureWhileDriving,
                    ),
            )
        val viewModel =
            fakeSettingsViewModel(
                displayPreferences = display,
                fontPreferences = fonts,
                locationPreferences = location,
                calendarPreferences = calendar,
                updatePreferences = update,
                videoPreferences = video,
                navigationPreferences = navigation,
            )

        init {
            runBlocking {
                display.mutateAllAwayFromDefault()
                fonts.setSource(FontSlot.LATIN, FontSource.GoogleFonts("Roboto Slab"))
                fonts.setSource(FontSlot.CJK, FontSource.GoogleFonts("Noto Serif"))
                location.setQuality(LocationQualitySetting.LOW_POWER)
                update.setAutoCheck(false)
                navigation.setRerouteAutomatically(false)
            }
        }
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
        const val CANCEL = "Cancel"
        const val RESET_TO_DEFAULTS = "Reset to defaults"
        const val ENTERED_VALUES = "Keys and addresses you entered"
        const val ENTERED_VALUES_DESC = "Google Maps key, Map ID, custom style URL, tile host"
        const val NOT_INCLUDED =
            "The dock, pinned and hidden apps, saved places and the track history are not reset here."
    }
}
