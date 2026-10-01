package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.settings.VideoFileSummary
import io.github.seijikohara.femto.ui.settings.VideoSettingsUi
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The Panels category's video rows (issue #390): the window switch, the file
 * row, and the picture gate, whose turning off always asks first. Settings
 * has no screenshot goldens, so nothing else pins these.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h1600dp-mdpi")
class PanelsSectionVideoTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<SettingsAction>()

    private fun setSection(video: VideoSettingsUi) {
        actions.clear()
        rule.setContent {
            FemtoTheme {
                PanelsSection(
                    uiState = SettingsUiState.Initial.copy(video = video),
                    onAction = { actions += it },
                    onOpenSystemSettings = {},
                )
            }
        }
    }

    private val enabled = VideoSettingsUi.Initial.copy(windowEnabled = true)

    @Test
    fun `the window switch is off by default and turns the window on`() {
        setSection(VideoSettingsUi.Initial)

        rule.onNodeWithText("Video window").assertIsOff().performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.SetVideoWindow(true)), actions)
    }

    @Test
    fun `the file and gate rows show only while the window is on`() {
        setSection(VideoSettingsUi.Initial)

        rule.onAllNodesWithText("Video file").assertCountEquals(0)
        rule.onAllNodesWithText(HIDE_PICTURE).assertCountEquals(0)
    }

    @Test
    fun `the gate is on by default`() {
        setSection(enabled)

        rule.onNodeWithText(HIDE_PICTURE).assertIsOn()
    }

    @Test
    fun `the file row names the picked file`() {
        setSection(enabled.copy(file = VideoFileSummary.Named("parked.mkv")))

        rule.onNodeWithText("parked.mkv").assertExists()
    }

    @Test
    fun `the file row says when no file is picked`() {
        setSection(enabled)

        rule.onNodeWithText("No file picked").assertExists()
    }

    @Test
    fun `the file row says when the picked file cannot be opened`() {
        setSection(enabled.copy(file = VideoFileSummary.Unavailable))

        rule.onNodeWithText("The picked file can't be opened. Pick it again.").assertExists()
    }

    @Test
    fun `turning the gate off asks first and does nothing on cancel`() {
        setSection(enabled)

        rule.onNodeWithText(HIDE_PICTURE).performClick()
        rule.onNodeWithText(CONFIRM_TITLE).assertExists()
        rule.onNodeWithText(CONFIRM_MESSAGE).assertExists()
        rule.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList(), actions)
        rule.onAllNodesWithText(CONFIRM_TITLE).assertCountEquals(0)
    }

    @Test
    fun `confirming turns the gate off`() {
        setSection(enabled)

        rule.onNodeWithText(HIDE_PICTURE).performClick()
        rule.onNodeWithText("Turn off").performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.SetVideoHidePicture(false)), actions)
    }

    @Test
    fun `turning the gate back on needs no confirmation`() {
        setSection(enabled.copy(hidePictureWhileDriving = false))

        rule.onNodeWithText(HIDE_PICTURE).performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.SetVideoHidePicture(true)), actions)
        rule.onAllNodesWithText(CONFIRM_TITLE).assertCountEquals(0)
    }

    private companion object {
        const val HIDE_PICTURE = "Hide the picture while driving"
        const val CONFIRM_TITLE = "Show the picture while driving?"
        const val CONFIRM_MESSAGE =
            "Many places ban video the driver can see while driving, including when stopped at lights or " +
                "in traffic. Some also require the engine to be off. This app can't tell whether your vehicle " +
                "is parked. Turn this off only if the screen is out of the driver's view or local law allows " +
                "it, and follow the law where you drive."
    }
}
