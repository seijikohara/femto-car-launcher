package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
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
import io.github.seijikohara.femto.data.DrawerIconSize
import io.github.seijikohara.femto.data.DrawerLayout
import io.github.seijikohara.femto.testfixtures.fakeAppEntry
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppDrawerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun loading_renders_progress_indicator() {
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Loading,
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithTag(APP_DRAWER_PROGRESS_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun content_renders_tiles_and_dispatches_component_name_on_tap() {
        var launched: ComponentName? = null
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(listOf(maps)),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = { launched = it },
                    onTogglePin = {},
                    onToggleLayout = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithText("Maps").assertIsDisplayed().performClick()
        assertEquals(maps.componentName, launched)
    }

    @Test
    fun long_press_dispatches_pin_for_an_unpinned_app() {
        var pinned: ComponentName? = null
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(listOf(maps)),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = {},
                    onTogglePin = { pinned = it },
                    onToggleLayout = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithText("Maps").performTouchInput { longClick() }
        rule.onNodeWithText("Pin").assertIsDisplayed().performClick()
        assertEquals(maps.componentName, pinned)
    }

    @Test
    fun layout_toggle_dispatches() {
        var toggled = false
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(listOf(maps)),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = { toggled = true },
                    onRetry = {},
                )
            }
        }
        // In grid layout the toggle offers the list layout.
        rule.onNodeWithContentDescription("List layout").performClick()
        assert(toggled)
    }

    @Test
    fun empty_content_shows_no_apps_message() {
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(emptyList()),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithText("No apps installed").assertIsDisplayed()
    }

    @Test
    fun search_filters_apps_by_label() {
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        val music = fakeAppEntry(packageName = "com.music", className = ".Main", label = "Music")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(listOf(maps, music)),
                    layout = DrawerLayout.LIST,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithTag(APP_DRAWER_SEARCH_TEST_TAG).performTextInput("mu")
        rule.onNodeWithText("Music").assertIsDisplayed()
        rule.onNodeWithText("Maps").assertDoesNotExist()
    }

    @Test
    fun search_with_no_match_shows_no_matches_message() {
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(listOf(maps)),
                    layout = DrawerLayout.LIST,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithTag(APP_DRAWER_SEARCH_TEST_TAG).performTextInput("zzz")
        rule.onNodeWithText("No apps match your search").assertIsDisplayed()
    }

    @Test
    fun pinned_apps_render_in_the_dock_and_launch_on_tap() {
        var launched: ComponentName? = null
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        val music = fakeAppEntry(packageName = "com.music", className = ".Main", label = "Music")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(listOf(maps, music)),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = listOf(music.componentName.flattenToString()),
                    onLaunch = { launched = it },
                    onTogglePin = {},
                    onToggleLayout = {},
                    onRetry = {},
                )
            }
        }
        // The pinned app appears twice: once in the grid, once in the dock.
        assertEquals(2, rule.onAllNodesWithText("Music").fetchSemanticsNodes().size)
        rule.onAllNodesWithText("Music").onLast().performClick()
        assertEquals(music.componentName, launched)
    }

    @Test
    fun error_shows_retry_and_dispatches_on_tap() {
        var retried = false
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Error,
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = {},
                    onRetry = { retried = true },
                )
            }
        }
        rule.onNodeWithText("Couldn't load apps").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assert(retried)
    }
}
