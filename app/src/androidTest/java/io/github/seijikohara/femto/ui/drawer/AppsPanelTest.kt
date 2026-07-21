package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
import io.github.seijikohara.femto.testfixtures.fakeAppEntry
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppsPanelTest {
    @get:Rule
    val rule = createComposeRule()

    // Render AppsPanel with sensible no-op defaults; each test overrides only the
    // callback it asserts on (testing.md: no per-test panel-arg boilerplate).
    private fun setPanel(
        uiState: AppDrawerUiState,
        layout: DrawerLayout = DrawerLayout.GRID,
        iconSize: DrawerIconSize = DrawerIconSize.MEDIUM,
        pinned: List<String> = emptyList(),
        onLaunch: (ComponentName) -> Unit = {},
        onTogglePin: (ComponentName) -> Unit = {},
        onOpenAppInfo: (ComponentName) -> Unit = {},
        onRequestUninstall: (ComponentName) -> Unit = {},
        onToggleLayout: () -> Unit = {},
        onSelectIconSize: (DrawerIconSize) -> Unit = {},
        onReorderPins: (List<String>) -> Unit = {},
        onRetry: () -> Unit = {},
        onClose: () -> Unit = {},
    ) = rule.setContent {
        FemtoTheme {
            AppsPanel(
                uiState = uiState,
                layout = layout,
                iconSize = iconSize,
                pinned = pinned,
                onLaunch = onLaunch,
                onTogglePin = onTogglePin,
                onOpenAppInfo = onOpenAppInfo,
                onRequestUninstall = onRequestUninstall,
                onToggleLayout = onToggleLayout,
                onSelectIconSize = onSelectIconSize,
                onReorderPins = onReorderPins,
                onRetry = onRetry,
                onClose = onClose,
            )
        }
    }

    // Reveal the search field, hidden behind the top bar's search toggle.
    private fun openSearch() = rule.onNodeWithContentDescription("Search").performClick()

    // Open the display-options overflow (layout toggle + icon-size presets).
    private fun openDisplayOptions() = rule.onNodeWithTag(APP_DRAWER_ICON_SIZE_TEST_TAG).performClick()

    private val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
    private val music = fakeAppEntry(packageName = "com.music", className = ".Main", label = "Music")

    @Test
    fun loading_renders_progress_indicator() {
        setPanel(uiState = AppDrawerUiState.Loading)
        rule.onNodeWithTag(APP_DRAWER_PROGRESS_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun collapse_button_dispatches_on_close() {
        var closed = false
        setPanel(uiState = AppDrawerUiState.Content(listOf(maps)), onClose = { closed = true })
        rule.onNodeWithContentDescription("Collapse").performClick()
        assert(closed)
    }

    @Test
    fun content_renders_tiles_and_dispatches_component_name_on_tap() {
        var launched: ComponentName? = null
        setPanel(uiState = AppDrawerUiState.Content(listOf(maps)), onLaunch = { launched = it })
        rule.onNodeWithText("Maps").assertIsDisplayed().performClick()
        assertEquals(maps.componentName, launched)
    }

    @Test
    fun long_press_dispatches_pin_for_an_unpinned_app() {
        var pinned: ComponentName? = null
        setPanel(uiState = AppDrawerUiState.Content(listOf(maps)), onTogglePin = { pinned = it })
        rule.onNodeWithText("Maps").performTouchInput { longClick() }
        rule.onNodeWithText("Pin").assertIsDisplayed().performClick()
        assertEquals(maps.componentName, pinned)
    }

    @Test
    fun layout_toggle_in_the_display_menu_dispatches() {
        var toggled = false
        setPanel(uiState = AppDrawerUiState.Content(listOf(maps)), onToggleLayout = { toggled = true })
        openDisplayOptions()
        // In grid layout the menu offers the list layout.
        rule.onNodeWithText("List layout").performClick()
        assert(toggled)
    }

    @Test
    fun empty_content_shows_no_apps_message() {
        setPanel(uiState = AppDrawerUiState.Content(emptyList()))
        rule.onNodeWithText("No apps installed").assertIsDisplayed()
    }

    @Test
    fun search_filters_apps_by_label() {
        setPanel(uiState = AppDrawerUiState.Content(listOf(maps, music)), layout = DrawerLayout.LIST)
        openSearch()
        rule.onNodeWithTag(APP_DRAWER_SEARCH_TEST_TAG).performTextInput("mu")
        rule.onNodeWithText("Music").assertIsDisplayed()
        rule.onNodeWithText("Maps").assertDoesNotExist()
    }

    @Test
    fun search_with_no_match_shows_no_matches_message() {
        setPanel(uiState = AppDrawerUiState.Content(listOf(maps)), layout = DrawerLayout.LIST)
        openSearch()
        rule.onNodeWithTag(APP_DRAWER_SEARCH_TEST_TAG).performTextInput("zzz")
        rule.onNodeWithText("No apps match your search").assertIsDisplayed()
    }

    @Test
    fun pinned_apps_render_in_the_dock_and_launch_on_tap() {
        var launched: ComponentName? = null
        setPanel(
            uiState = AppDrawerUiState.Content(listOf(maps, music)),
            pinned = listOf(music.componentName.flattenToString()),
            onLaunch = { launched = it },
        )
        // The pinned app appears twice: once in the grid, once in the dock.
        assertEquals(2, rule.onAllNodesWithText("Music").fetchSemanticsNodes().size)
        rule.onAllNodesWithText("Music").onLast().performClick()
        assertEquals(music.componentName, launched)
    }

    @Test
    fun error_shows_retry_and_dispatches_on_tap() {
        var retried = false
        setPanel(uiState = AppDrawerUiState.Error, onRetry = { retried = true })
        rule.onNodeWithText("Couldn't load apps").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assert(retried)
    }

    @Test
    fun icon_size_menu_dispatches_the_selected_preset() {
        var selected: DrawerIconSize? = null
        setPanel(uiState = AppDrawerUiState.Content(listOf(maps)), onSelectIconSize = { selected = it })
        openDisplayOptions()
        rule.onNodeWithText("Large").assertIsDisplayed().performClick()
        assertEquals(DrawerIconSize.LARGE, selected)
    }

    @Test
    fun content_renders_the_recent_row_and_launches_a_recent_on_tap() {
        var launched: ComponentName? = null
        // The recent app is not in the grid, so its tile is the only "Music" node.
        setPanel(
            uiState = AppDrawerUiState.Content(apps = listOf(maps), recentApps = listOf(music)),
            onLaunch = { launched = it },
        )
        rule.onNodeWithText("Recent").assertIsDisplayed()
        rule.onNodeWithText("Music").assertIsDisplayed().performClick()
        assertEquals(music.componentName, launched)
    }

    @Test
    fun the_recent_row_is_hidden_while_a_search_query_is_active() {
        setPanel(uiState = AppDrawerUiState.Content(apps = listOf(maps, music), recentApps = listOf(maps)))
        // Recents show while browsing...
        rule.onNodeWithText("Recent").assertIsDisplayed()
        // ...and step aside once a query is active (the filtered list is the signal).
        openSearch()
        rule.onNodeWithTag(APP_DRAWER_SEARCH_TEST_TAG).performTextInput("ma")
        rule.onNodeWithText("Recent").assertDoesNotExist()
    }

    @Test
    fun tapping_the_alphabet_rail_jumps_to_that_section() {
        // One app per letter A..Z: more than one bucket (so the rail shows) and a
        // list tall enough that the Z section starts well below the fold.
        val apps =
            ('A'..'Z').map { letter ->
                fakeAppEntry(packageName = "com.app$letter", className = ".Main", label = "$letter-app")
            }
        setPanel(uiState = AppDrawerUiState.Content(apps), layout = DrawerLayout.LIST)
        // The list starts at the top; the Z section is off-screen, so the only "Z" on
        // screen is the rail's own letter (the Z inline marker is not composed yet).
        rule.onNodeWithText("A-app").assertIsDisplayed()
        // A touch at the rail's "Z" letter resolves to the Z bucket (the rail scrubs
        // proportionally over its full height) and scrolls that section's app into view.
        rule.onNodeWithText("Z").performTouchInput {
            down(center)
            up()
        }
        rule.onNodeWithText("Z-app").assertIsDisplayed()
    }
}
