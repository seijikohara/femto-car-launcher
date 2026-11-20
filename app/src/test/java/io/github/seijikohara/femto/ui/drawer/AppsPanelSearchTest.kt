package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
import io.github.seijikohara.femto.testfixtures.fakeAppEntry
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contract tests for the apps panel's expanded search: the top bar's search
 * button opens the full-screen search view with its field focused, the results
 * filter as the user types, and a launch or the close button collapses the view
 * and clears the query. The device-side counterpart is `AppsPanelTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class AppsPanelSearchTest {
    @get:Rule
    val rule = createComposeRule()

    private val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
    private val music = fakeAppEntry(packageName = "com.music", className = ".Main", label = "Music")

    private val launched = mutableListOf<ComponentName>()

    private fun setPanel() =
        rule.setContent {
            FemtoTheme {
                AppsPanel(
                    uiState = AppDrawerUiState.Content(apps = listOf(maps, music)),
                    layout = DrawerLayout.GRID,
                    iconSize = DrawerIconSize.MEDIUM,
                    pinned = emptyList(),
                    hidden = emptySet(),
                    onLaunch = { launched += it },
                    onTogglePin = {},
                    onToggleHide = {},
                    onOpenAppInfo = {},
                    onRequestUninstall = {},
                    onToggleLayout = {},
                    onSelectIconSize = {},
                    onReorderPins = {},
                    onRetry = {},
                    onClose = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

    // performTextInput bypasses the IME, so the focus assertion is what proves
    // the expanded view's field is the one taking input.
    private fun openSearch() {
        rule.onNodeWithContentDescription("Search").performClick()
        rule.onNodeWithTag(APP_DRAWER_SEARCH_TEST_TAG).assertIsFocused()
    }

    private fun inResults(matcher: SemanticsMatcher) =
        rule.onNode(matcher and hasAnyAncestor(hasTestTag(APP_DRAWER_SEARCH_RESULTS_TEST_TAG)))

    private fun assertSearchCollapsed() {
        rule.onNodeWithTag(APP_DRAWER_SEARCH_RESULTS_TEST_TAG).assertDoesNotExist()
        // The top bar is back to its eyebrow and search button.
        rule.onNodeWithText("APPS").assertIsDisplayed()
        rule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun tapping_a_result_launches_the_app_and_collapses_the_search() {
        setPanel()
        openSearch()
        rule.onNodeWithTag(APP_DRAWER_SEARCH_TEST_TAG).performTextInput("mu")
        inResults(hasText("Maps")).assertDoesNotExist()
        inResults(hasText("Music")).assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(listOf(music.componentName), launched)
        assertSearchCollapsed()
    }

    @Test
    fun the_close_button_collapses_the_search_and_clears_the_query() {
        setPanel()
        openSearch()
        rule.onNodeWithTag(APP_DRAWER_SEARCH_TEST_TAG).performTextInput("mu")
        closeSearch()
        // Reopening starts from an empty query, so every app is a result again.
        openSearch()
        rule
            .onNodeWithTag(APP_DRAWER_SEARCH_TEST_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        inResults(hasText("Maps")).assertIsDisplayed()
        closeSearch()
    }

    // End with the search closed: a focused field left in the open search window
    // keeps the composition busy past the test, which slows teardown and leaks
    // into later Robolectric tests in the same JVM.
    private fun closeSearch() {
        rule.onNodeWithContentDescription("Close search").performClick()
        rule.waitForIdle()
        assertSearchCollapsed()
    }
}
