package io.github.seijikohara.femto.ui.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.voice.VoiceState
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// Drives the delegation fallback in isolation: with VoiceState.Unavailable the
// in-launcher voice surface is hidden, so only the three system-intent rows
// render and their dispatch can be asserted without a recognizer.
class AssistantScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assistantLabel = context.getString(R.string.assistant_option_assistant)
    private val voiceCommandLabel = context.getString(R.string.assistant_option_voice_command)
    private val voiceSearchLabel = context.getString(R.string.assistant_option_voice_search)

    @Test
    fun renders_all_three_delegation_options() {
        rule.setContent {
            FemtoTheme {
                AssistantScreen(
                    uiState = AssistantUiState(voice = VoiceState.Unavailable),
                    onMicTap = {},
                    onReset = {},
                    onSubmitQuery = {},
                    onLaunchOption = {},
                )
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
                AssistantScreen(
                    uiState = AssistantUiState(voice = VoiceState.Unavailable),
                    onMicTap = {},
                    onReset = {},
                    onSubmitQuery = {},
                    onLaunchOption = { launched = it },
                )
            }
        }
        rule.onNodeWithText(assistantLabel).performClick()
        assertEquals(AssistantOption.ASSISTANT, launched)
    }

    @Test
    fun tapping_voice_search_dispatches_voice_search_option() {
        var launched: AssistantOption? = null
        rule.setContent {
            FemtoTheme {
                AssistantScreen(
                    uiState = AssistantUiState(voice = VoiceState.Unavailable),
                    onMicTap = {},
                    onReset = {},
                    onSubmitQuery = {},
                    onLaunchOption = { launched = it },
                )
            }
        }
        rule.onNodeWithText(voiceSearchLabel).performClick()
        assertEquals(AssistantOption.VOICE_SEARCH, launched)
    }
}
