package io.github.seijikohara.femto.ui.settings.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.testfixtures.routingOfferedSettingsState
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.settings.SettingsScreen
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.settings.visibleSettingsCategories
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The update dot on the Settings category list and rail reaches the badged
 * entry, read the way an accessibility service reads it: "Select Updates"
 * followed by "Update available". The screenshot goldens cannot tell the dot
 * is announced, or on which entry. The rail items also keep the automotive tap
 * target and stay reachable on a head-unit-height screen, all nine of them
 * where routing is offered. Navigation is listed only there
 * (visibleSettingsCategories), in the rail, the narrow list and the detail
 * pane alike.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class SettingsCategoryListTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val selectUpdates =
        context.getString(R.string.settings_category_select, context.getString(R.string.settings_section_updates))
    private val updateAvailable = context.getString(R.string.update_available)

    private val selectNavigation = selectDescription(SettingsCategoryId.NAVIGATION)

    private val routingOffered = routingOfferedSettingsState()

    private fun setList(
        badged: Set<SettingsCategoryId> = emptySet(),
        categories: List<SettingsCategoryId> = SettingsCategoryId.entries,
    ) {
        rule.setContent {
            FemtoTheme {
                SettingsCategoryList(
                    categories = categories,
                    selectedId = SettingsCategoryId.APPEARANCE,
                    onSelect = {},
                    badged = badged,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a badged Updates row announces the update after its own label`() {
        setList(badged = setOf(SettingsCategoryId.UPDATES))

        rule.onNode(hasContentDescription(selectUpdates) and hasContentDescription(updateAvailable)).assertExists()
    }

    @Test
    fun `no row announces an update without the badge`() {
        setList(badged = emptySet())

        rule.onNodeWithContentDescription(updateAvailable).assertDoesNotExist()
    }

    @Test
    fun `a badged Updates rail item announces the update after its own label`() {
        setRail(badged = setOf(SettingsCategoryId.UPDATES))

        rule.onNode(hasContentDescription(selectUpdates) and hasContentDescription(updateAvailable)).assertExists()
    }

    // Every category, Navigation included: the nine entries a build that offers routing lists, the most the rail
    // ever holds, so the last one (System) is reached by scrolling at the head unit's height.
    @Test
    fun `every rail item keeps the automotive tap target and selects its category`() {
        val selected = mutableListOf<SettingsCategoryId>()
        setRail(badged = emptySet(), onSelect = { selected += it })

        SettingsCategoryId.entries.forEach { id ->
            rule
                .onNodeWithContentDescription(selectDescription(id))
                .performScrollTo()
                .assertHeightIsAtLeast(FemtoDimens.MinTouchTarget)
                .performClick()
        }

        assertEquals(SettingsCategoryId.entries.toList(), selected)
    }

    @Test
    fun `Navigation is not listed while routing is not offered`() {
        val categories = visibleSettingsCategories(SettingsUiState.Initial)

        assertEquals(SettingsCategoryId.entries - SettingsCategoryId.NAVIGATION, categories)
    }

    @Test
    fun `Navigation is listed after Map where routing is offered`() {
        val categories = visibleSettingsCategories(routingOffered)

        assertEquals(SettingsCategoryId.entries.toList(), categories)
        assertEquals(SettingsCategoryId.MAP, categories[categories.indexOf(SettingsCategoryId.NAVIGATION) - 1])
    }

    @Test
    fun `the rail leaves Navigation out while routing is not offered`() {
        setRail(categories = visibleSettingsCategories(SettingsUiState.Initial))

        rule.onNodeWithContentDescription(selectNavigation).assertDoesNotExist()
    }

    @Test
    fun `the narrow list leaves Navigation out while routing is not offered`() {
        setList(categories = visibleSettingsCategories(SettingsUiState.Initial))

        rule.onNodeWithContentDescription(selectNavigation).assertDoesNotExist()
    }

    @Test
    fun `the rail shows Navigation where routing is offered`() {
        setRail(categories = visibleSettingsCategories(routingOffered))

        rule.onNodeWithContentDescription(selectNavigation).performScrollTo().assertExists()
    }

    @Test
    fun `the narrow list shows Navigation where routing is offered`() {
        setList(categories = visibleSettingsCategories(routingOffered))

        rule.onNodeWithContentDescription(selectNavigation).performScrollTo().assertExists()
    }

    // Navigation selected while it is not listed (a restored selection before the routing data summary has arrived,
    // or a summary that could not be read): the screen shows the first category rather than a detail pane for a
    // category it does not list.
    @Test
    fun `a selected Navigation that stops showing gives way to the first category`() {
        var uiState by mutableStateOf(routingOffered)
        rule.setContent {
            FemtoTheme {
                SettingsScreen(
                    uiState = uiState,
                    onAction = {},
                    onBack = {},
                    onOpenNotificationAccess = {},
                    onOpenSystemSettings = {},
                    onOpenFontPicker = {},
                    onOpenOfflineRegions = {},
                    onOpenRoutingData = {},
                    onOpenDiagnostics = {},
                    onOpenLicenses = {},
                    onOpenDocument = {},
                    initialCategory = SettingsCategoryId.NAVIGATION,
                )
            }
        }
        rule.onNodeWithText("Reroute automatically").assertExists()

        uiState = SettingsUiState.Initial
        rule.waitForIdle()

        rule.onNodeWithText("Reroute automatically").assertDoesNotExist()
        rule.onNodeWithContentDescription(selectNavigation).assertDoesNotExist()
        rule.onNodeWithText("Theme").assertExists()
    }

    private fun setRail(
        badged: Set<SettingsCategoryId> = emptySet(),
        onSelect: (SettingsCategoryId) -> Unit = {},
        categories: List<SettingsCategoryId> = SettingsCategoryId.entries,
    ) {
        rule.setContent {
            FemtoTheme {
                SettingsCategoryRail(
                    categories = categories,
                    selectedId = SettingsCategoryId.APPEARANCE,
                    onSelect = onSelect,
                    badged = badged,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun selectDescription(id: SettingsCategoryId) =
        context.getString(R.string.settings_category_select, context.getString(id.titleRes))
}
