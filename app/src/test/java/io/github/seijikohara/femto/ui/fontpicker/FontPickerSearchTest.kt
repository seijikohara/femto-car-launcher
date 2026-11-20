package io.github.seijikohara.femto.ui.fontpicker

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract test for the font picker's expanded search: tapping the search bar
 * opens the search view with its field focused, and what the user types reaches
 * the view-model as [FontPickerAction.Search], the view-model being the owner of
 * the query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h915dp-mdpi")
class FontPickerSearchTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun typing_in_the_expanded_search_forwards_the_query_to_the_view_model() {
        val searches = mutableListOf<String>()
        rule.setContent {
            FemtoTheme {
                FontPickerScreen(
                    uiState = FontPickerUiState(slot = FontSlot.LATIN, status = PickerStatus.READY),
                    onAction = { action -> if (action is FontPickerAction.Search) searches += action.query },
                    onBack = {},
                )
            }
        }
        // The collapsed bar's field announces itself as "Search"; a tap expands it.
        rule.onNodeWithContentDescription("Search").performClick()
        rule.onNodeWithTag(FONT_PICKER_SEARCH_TEST_TAG).assertIsFocused().performTextInput("ro")
        rule.waitForIdle()
        assertEquals("ro", searches.last())
        // End with the search closed: a focused field left in the open search
        // window keeps the composition busy past the test and leaks into later
        // Robolectric tests in the same JVM.
        rule.onNodeWithContentDescription("Close search").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(FONT_PICKER_SEARCH_TEST_TAG).assertDoesNotExist()
    }
}
