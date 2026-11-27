package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.TurnCardFixtures
import io.github.seijikohara.femto.testfixtures.fakeVideoUiState
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.DashboardTags
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.video.VideoUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * A tap on the turn card on the dashboard reaches the view model as the one action that switches the spoken prompts,
 * and so does a touch just beyond the compact card's top or bottom edge: the card is shorter than the touch floor
 * there, and its switch is a node that measures the floor tall around it. The phone in portrait with the video window
 * at the compact Display size takes the compact card (47 px tall in the window), the touch floor is 64 dp of that
 * Display size, and a touch farther out than the floor's reach is no hit.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w2000dp-h1920dp-mdpi")
class TurnCardTouchTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a tap on the card reaches the view model as one toggle`() {
        val actions = mutableListOf<HomeAction>()
        setDashboard(actions, "head-unit-853x512", UiScale.LARGE)

        rule.onNodeWithTag(DashboardTags.TURN_CARD, useUnmergedTree = true).performClick()
        rule.waitForIdle()

        assertEquals<List<HomeAction>>(listOf(HomeAction.ToggleSpokenPrompts), actions)
    }

    @Test
    fun `a touch just beyond the short compact card toggles, one beyond the floor does not`() {
        val actions = mutableListOf<HomeAction>()
        setDashboard(actions, "phone-portrait-412x915", UiScale.COMPACT, video = true)
        val card = rule.onNodeWithTag(DashboardTags.TURN_CARD, useUnmergedTree = true)
        val height = card.fetchSemanticsNode().size.height

        card.touchAt(-1)
        card.touchAt(-2)
        card.touchAt(height + 1)
        card.touchAt(height + 2)
        val near = actions.size
        card.touchAt(-FAR)
        card.touchAt(height + FAR)

        assertEquals(
            Triple(true, 0, Pair(4, 4)),
            Triple(
                height < FLOOR_PX,
                rule.onAllNodesWithText("Then", useUnmergedTree = true).fetchSemanticsNodes().size,
                near to actions.size,
            ),
            "card $height px in the window",
        )
    }

    // A touch [y] px below the card's top edge, in its own coordinates: negative above it.
    private fun SemanticsNodeInteraction.touchAt(y: Int) {
        performTouchInput { click(Offset(centerX, y.toFloat())) }
        rule.waitForIdle()
    }

    private fun setDashboard(
        actions: MutableList<HomeAction>,
        geometryId: String,
        scale: UiScale,
        video: Boolean = false,
    ) {
        val geometry = DashboardGeometries.all.first { it.id == geometryId }
        val state =
            DashboardFixtures.routeTurnThenState.let { state ->
                state.copy(
                    activeRoute =
                        state.activeRoute?.copy(
                            turnCard = TurnCardFixtures.withVoice(TurnCardFixtures.turnThen, on = true),
                        ),
                )
            }
        rule.setContent {
            Box(modifier = Modifier.size(geometry.widthDp.dp, geometry.heightDp.dp)) {
                FemtoTheme(uiScale = scale) {
                    DashboardScaffold(
                        uiState = state,
                        is24Hour = true,
                        showClockSeconds = true,
                        speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                        mapConfig = MapConfig(),
                        panels = PanelVisibility(),
                        glassConfig = GlassConfig(),
                        onAction = { actions += it },
                        modifier = Modifier.fillMaxSize(),
                        driverSide = DriverSide.RIGHT,
                        clock = DashboardFixtures.fixedClock,
                        mapSurface = { _, _ -> Box(Modifier.fillMaxSize()) },
                        video = if (video) fakeVideoUiState() else VideoUiState.Off,
                    )
                }
            }
        }
    }

    private companion object {
        // 64 dp of the compact Display size (5/6 of the window's density), rounded.
        const val FLOOR_PX = 54

        // Farther out than half the floor's excess over the card.
        const val FAR = 8
    }
}
