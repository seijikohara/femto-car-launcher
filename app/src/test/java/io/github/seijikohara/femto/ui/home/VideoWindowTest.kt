package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import io.github.seijikohara.femto.ui.video.VideoFileState
import io.github.seijikohara.femto.ui.video.VideoUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    // Held in snapshot state, so a test can move the window to a new state
    // after it is on screen, the way the ViewModel's flow would.
    private var video by mutableStateOf(VideoUiState.Off)

    private fun setDashboard(
        initial: VideoUiState,
        pictureVisible: Boolean = true,
    ) {
        actions.clear()
        video = initial
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
        setDashboard(fakeVideoUiState(file = VideoFileState.NONE))

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
    fun `closing from the panel never brings the small window back`() {
        setDashboard(fakeVideoUiState())
        rule.onNodeWithContentDescription("Open the video player").performClick()

        // The state has not caught up with the close yet, as while the
        // store's write is under way.
        rule.onNodeWithContentDescription("Close the video window").performClick()

        assertEquals(listOf<VideoAction>(VideoAction.Close), actions)
        rule.onAllNodesWithTag(DashboardTags.VIDEO_WINDOW).assertCountEquals(0)
    }

    @Test
    fun `the panel collapses once the window reads off`() {
        setDashboard(fakeVideoUiState())
        rule.onNodeWithContentDescription("Open the video player").performClick()
        rule.onNodeWithContentDescription("Close the video window").performClick()

        video = VideoUiState.Off
        rule.waitForIdle()

        rule.onAllNodesWithTag(DashboardTags.VIDEO_PANEL).assertCountEquals(0)
        rule.onAllNodesWithTag(DashboardTags.VIDEO_WINDOW).assertCountEquals(0)
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

    @Test
    fun `a refused pick says so in the window`() {
        setDashboard(fakeVideoUiState(file = VideoFileState.NONE, pickFailed = true))

        rule.onNodeWithText(PICK_FAILED).assertExists()
    }

    @Test
    fun `a refused pick says so in the panel`() {
        setDashboard(fakeVideoUiState(pickFailed = true))
        rule.onNodeWithContentDescription("Open the video player").performClick()

        rule.onNodeWithText(PICK_FAILED).assertExists()
    }

    @Test
    fun `an unavailable file says so above the pick action in the window`() {
        setDashboard(fakeVideoUiState(file = VideoFileState.UNAVAILABLE))

        val notice = rule.onNodeWithText(UNAVAILABLE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val prompt = rule.onNodeWithText("Pick a video", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(notice.bottom <= prompt.top, "notice $notice is not above the prompt $prompt")

        rule.onNodeWithText("Pick a video").performClick()
        assertEquals(listOf<VideoAction>(VideoAction.PickFile), actions)
    }

    @Test
    fun `a file that stops opening while the panel is open says so there`() {
        setDashboard(fakeVideoUiState())
        rule.onNodeWithContentDescription("Open the video player").performClick()

        video = fakeVideoUiState(file = VideoFileState.UNAVAILABLE)
        rule.waitForIdle()

        rule.onNodeWithText(UNAVAILABLE).assertExists()
        rule.onNodeWithText("Pick a video").performClick()
        assertEquals(listOf<VideoAction>(VideoAction.PickFile), actions)
    }

    @Test
    fun `a window with no file says nothing is wrong`() {
        setDashboard(fakeVideoUiState(file = VideoFileState.NONE))

        rule.onAllNodesWithText(UNAVAILABLE).assertCountEquals(0)
    }

    private companion object {
        const val UNAVAILABLE = "The picked file can't be opened. Pick it again."
        const val PICK_FAILED = "The app couldn't keep access to that file. Pick another."

        const val SURFACE_TAG = "videoSurface"
        const val HIDDEN_LINE = "Picture hidden while driving · Audio keeps playing"
    }
}
