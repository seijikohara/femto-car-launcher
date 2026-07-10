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
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
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
                    onSelectIconSize = {},
                    onReorderPins = {},
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
                    onSelectIconSize = {},
                    onReorderPins = {},
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
                    onSelectIconSize = {},
                    onReorderPins = {},
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
                    onSelectIconSize = {},
                    onReorderPins = {},
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
                    onSelectIconSize = {},
                    onReorderPins = {},
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
                    onSelectIconSize = {},
                    onReorderPins = {},
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
                    onSelectIconSize = {},
                    onReorderPins = {},
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
                    onSelectIconSize = {},
                    onReorderPins = {},
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
                    onSelectIconSize = {},
                    onReorderPins = {},
                    onRetry = { retried = true },
                )
            }
        }
        rule.onNodeWithText("Couldn't load apps").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assert(retried)
    }

    @Test
    fun compact_shows_pinned_dock_only_and_expands_via_all_apps_row() {
        var expanded = false
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        val music = fakeAppEntry(packageName = "com.music", className = ".Main", label = "Music")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(listOf(maps, music)),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = listOf("com.maps/.Main"),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = {},
                    onSelectIconSize = {},
                    onReorderPins = {},
                    onRetry = {},
                    compact = true,
                    onExpand = { expanded = true },
                )
            }
        }
        // Only the pinned app renders; the unpinned one stays out of the quick view.
        rule.onNodeWithText("Maps").assertIsDisplayed()
        rule.onNodeWithText("Music").assertDoesNotExist()
        rule.onNodeWithText("All apps").assertIsDisplayed().performClick()
        assert(expanded)
    }

    @Test
    fun compact_with_only_stale_pins_requests_expansion() {
        var expanded = false
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(listOf(maps)),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = listOf("com.uninstalled/.Main"),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = {},
                    onSelectIconSize = {},
                    onReorderPins = {},
                    onRetry = {},
                    compact = true,
                    onExpand = { expanded = true },
                )
            }
        }
        rule.waitForIdle()
        assert(expanded)
    }

    @Test
    fun icon_size_menu_dispatches_the_selected_preset() {
        var selected: DrawerIconSize? = null
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
                    onToggleLayout = {},
                    onSelectIconSize = { selected = it },
                    onReorderPins = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithTag(APP_DRAWER_ICON_SIZE_TEST_TAG).performClick()
        rule.onNodeWithText("Large").assertIsDisplayed().performClick()
        assertEquals(DrawerIconSize.LARGE, selected)
    }

    @Test
    fun content_renders_the_recent_row_and_launches_a_recent_on_tap() {
        var launched: ComponentName? = null
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        // The recent app is not in the grid, so its tile is the only "Music" node.
        val music = fakeAppEntry(packageName = "com.music", className = ".Main", label = "Music")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(apps = listOf(maps), recentApps = listOf(music)),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = { launched = it },
                    onTogglePin = {},
                    onToggleLayout = {},
                    onSelectIconSize = {},
                    onReorderPins = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithText("Recent").assertIsDisplayed()
        rule.onNodeWithText("Music").assertIsDisplayed().performClick()
        assertEquals(music.componentName, launched)
    }

    @Test
    fun the_recent_row_is_hidden_while_a_search_query_is_active() {
        val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
        val music = fakeAppEntry(packageName = "com.music", className = ".Main", label = "Music")
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(apps = listOf(maps, music), recentApps = listOf(maps)),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = {},
                    onSelectIconSize = {},
                    onReorderPins = {},
                    onRetry = {},
                )
            }
        }
        // Recents show while browsing...
        rule.onNodeWithText("Recent").assertIsDisplayed()
        // ...and step aside once a query is active (the filtered list is the signal).
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
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(apps),
                    layout = DrawerLayout.LIST,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    onLaunch = {},
                    onTogglePin = {},
                    onToggleLayout = {},
                    onSelectIconSize = {},
                    onReorderPins = {},
                    onRetry = {},
                )
            }
        }
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
