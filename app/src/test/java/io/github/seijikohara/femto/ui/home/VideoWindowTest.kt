package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.fakeVideoUiState
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.DashboardTags
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.video.VideoAction
import io.github.seijikohara.femto.ui.video.VideoUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The dashboard's video window and its full panel (issue #390): what each
 * state shows, and what each control reports. The player's surface is a
 * tagged stand-in, so "no frame at all" reads as the stand-in's absence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class VideoWindowTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<VideoAction>()

    private fun setDashboard(
        video: VideoUiState,
        pictureVisible: Boolean = true,
    ) {
        actions.clear()
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    uiState = DashboardFixtures.state,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                    clock = DashboardFixtures.fixedClock,
                    mapSurface = { _, _ -> Box(Modifier.fillMaxSize()) },
                    video = video,
                    videoPictureVisible = pictureVisible,
                    onVideoAction = { actions += it },
                    videoSurface = { modifier -> Box(modifier.testTag(SURFACE_TAG)) },
                )
            }
        }
    }

    // Unmerged: the window is one clickable node, which folds its children in.
    private fun picture() = rule.onAllNodesWithTag(SURFACE_TAG, useUnmergedTree = true)

    private fun hiddenLine() = rule.onAllNodesWithText(HIDDEN_LINE)

    @Test
    fun `a window turned off shows nothing`() {
        setDashboard(VideoUiState.Off)

        rule.onAllNodesWithTag(DashboardTags.VIDEO_WINDOW).assertCountEquals(0)
    }

    @Test
    fun `a window with no file asks for one`() {
        setDashboard(fakeVideoUiState(fileReady = false))

        rule.onNodeWithText("Pick a video").performClick()

        assertEquals(listOf<VideoAction>(VideoAction.PickFile), actions)
        picture().assertCountEquals(0)
    }

    @Test
    fun `a hidden picture draws no frame and says why`() {
        setDashboard(fakeVideoUiState(playing = true), pictureVisible = false)

        hiddenLine().assertCountEquals(1)
        picture().assertCountEquals(0)
    }

    @Test
    fun `a visible picture draws the surface`() {
        setDashboard(fakeVideoUiState(), pictureVisible = true)

        picture().assertCountEquals(1)
        hiddenLine().assertCountEquals(0)
    }

    @Test
    fun `the play button toggles playback`() {
        setDashboard(fakeVideoUiState(playing = false))

        rule.onNodeWithContentDescription("Play").performClick()

        assertEquals(listOf<VideoAction>(VideoAction.TogglePlayback), actions)
    }

    @Test
    fun `a playing window offers pause`() {
        setDashboard(fakeVideoUiState(playing = true))

        rule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun `a tap on the window opens the panel`() {
        setDashboard(fakeVideoUiState())

        rule.onNodeWithContentDescription("Open the video player").performClick()

        rule.onNodeWithContentDescription("Close the video window").assertIsDisplayed()
    }

    @Test
    fun `the panel picks another file`() {
        setDashboard(fakeVideoUiState())
        rule.onNodeWithContentDescription("Open the video player").performClick()

        rule.onNodeWithContentDescription("Pick another video").performClick()

        assertEquals(listOf<VideoAction>(VideoAction.PickFile), actions)
    }

    @Test
    fun `closing from the panel turns the window off and collapses the panel`() {
        setDashboard(fakeVideoUiState())
        rule.onNodeWithContentDescription("Open the video player").performClick()

        rule.onNodeWithContentDescription("Close the video window").performClick()

        assertEquals(listOf<VideoAction>(VideoAction.Close), actions)
        rule.onAllNodesWithTag(DashboardTags.VIDEO_PANEL).assertCountEquals(0)
    }

    @Test
    fun `the panel keeps the picture hidden while driving`() {
        setDashboard(fakeVideoUiState(), pictureVisible = false)
        rule.onNodeWithContentDescription("Open the video player").performClick()

        // The panel stands in for the window while it is open, so the one
        // line on screen is the panel's.
        rule.onAllNodesWithTag(DashboardTags.VIDEO_WINDOW).assertCountEquals(0)
        picture().assertCountEquals(0)
        hiddenLine().assertCountEquals(1)
    }

    private companion object {
        const val SURFACE_TAG = "videoSurface"
        const val HIDDEN_LINE = "Picture hidden while driving · Audio keeps playing"
    }
}
