package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                AppDrawerScreen(uiState = AppDrawerUiState.Loading, onLaunch = {}, onRetry = {})
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
                    onLaunch = { launched = it },
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithText("Maps").assertIsDisplayed().performClick()
        assertEquals(maps.componentName, launched)
    }

    @Test
    fun empty_content_shows_no_apps_message() {
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Content(emptyList()),
                    onLaunch = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithText("No apps installed").assertIsDisplayed()
    }

    @Test
    fun error_shows_retry_and_dispatches_on_tap() {
        var retried = false
        rule.setContent {
            FemtoTheme {
                AppDrawerScreen(
                    uiState = AppDrawerUiState.Error,
                    onLaunch = {},
                    onRetry = { retried = true },
                )
            }
        }
        rule.onNodeWithText("Couldn't load apps").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assert(retried)
    }
}
