package io.github.seijikohara.femto.ui.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AssistantSheetContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assistantLabel = context.getString(R.string.assistant_option_assistant)
    private val voiceCommandLabel = context.getString(R.string.assistant_option_voice_command)
    private val voiceSearchLabel = context.getString(R.string.assistant_option_voice_search)

    @Test
    fun renders_all_three_options() {
        rule.setContent {
            FemtoTheme {
                AssistantSheetContent(onLaunchOption = {})
            }
        }
        rule.onNodeWithText(assistantLabel).assertIsDisplayed()
        rule.onNodeWithText(voiceCommandLabel).assertIsDisplayed()
        rule.onNodeWithText(voiceSearchLabel).assertIsDisplayed()
    }

    @Test
    fun tapping_assistant_dispatches_assistant_option() {
        var launched: AssistantOption? = null
        rule.setContent {
            FemtoTheme {
                AssistantSheetContent(onLaunchOption = { launched = it })
            }
        }
        rule.onNodeWithText(assistantLabel).performClick()
        assertEquals(AssistantOption.ASSISTANT, launched)
    }

    @Test
    fun tapping_voice_command_dispatches_voice_command_option() {
        var launched: AssistantOption? = null
        rule.setContent {
            FemtoTheme {
                AssistantSheetContent(onLaunchOption = { launched = it })
            }
        }
        rule.onNodeWithText(voiceCommandLabel).performClick()
        assertEquals(AssistantOption.VOICE_COMMAND, launched)
    }

    @Test
    fun tapping_voice_search_dispatches_voice_search_option() {
        var launched: AssistantOption? = null
        rule.setContent {
            FemtoTheme {
                AssistantSheetContent(onLaunchOption = { launched = it })
            }
        }
        rule.onNodeWithText(voiceSearchLabel).performClick()
        assertEquals(AssistantOption.VOICE_SEARCH, launched)
    }
}
