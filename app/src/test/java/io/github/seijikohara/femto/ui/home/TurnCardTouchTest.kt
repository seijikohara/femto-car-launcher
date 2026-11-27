package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * A tap on the turn card on the dashboard reaches the view model as the one action that switches the spoken prompts.
 *
 * Not covered here, and not working as far as this test could tell: the compact card's grown tap area. On the card alone
 * ([TurnCardTest]) a touch up to 4 dp beyond its edge toggles; on the dashboard (the phone in portrait with the video
 * window at the compact Display size, where the card is 47 px tall) a touch 1 px beyond its edge did nothing, and
 * touches inside it all toggled. No clip or pointer handler in DashboardScaffold explains it; the cause is not found.
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
}
