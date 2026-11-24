package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedState
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MUSIC_CARD_TAG
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The music card offering the last played track, idle and resuming, keeps the Playing card's bounds on the dashboard
 * at every geometry and display size the dashboard goldens cover ([DashboardGeometries], plus the SMALL and LARGE
 * cases of DashboardScreenshotTest): the track coming and going never moves the card or the cards above it. Each
 * geometry is laid out at its own size inside one window larger than all of them, as in RouteChoiceLayoutTest, and the
 * state swaps in one composition, as on the live dashboard.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w2000dp-h1920dp-mdpi")
class MusicCardLastPlayedLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private var geometry by mutableStateOf(DashboardGeometries.all.first())
    private var uiScale by mutableStateOf(UiScale.MEDIUM)
    private var musicState by mutableStateOf(DashboardFixtures.state.musicState)

    @Test
    fun `the last played card keeps the playing card's bounds at every dashboard geometry`() {
        rule.setContent {
            Box(modifier = Modifier.size(geometry.widthDp.dp, geometry.heightDp.dp)) {
                FemtoTheme(uiScale = uiScale) {
                    DashboardScaffold(
                        uiState = DashboardFixtures.state.copy(musicState = musicState),
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
                    )
                }
            }
        }
        val failures =
            cases.flatMap { (candidate, scale) ->
                geometry = candidate
                uiScale = scale
                layoutFailures("${candidate.id} $scale")
            }
        assertEquals(emptyList(), failures)
    }

    private fun layoutFailures(case: String): List<String> {
        val playing = cardBoundsWith(DashboardFixtures.state.musicState)
        // Anti-vacuous guard: a card that never rendered would equal itself everywhere.
        val rendered =
            listOfNotNull("$case: the playing card did not render ($playing)".takeIf { playing.height <= 0.dp })
        return rendered +
            listOf(fakeLastPlayedState(), fakeLastPlayedState(resuming = true)).mapNotNull { state ->
                cardBoundsWith(state)
                    .takeIf { it != playing }
                    ?.let { "$case: resuming=${state.resuming} card $it, playing card $playing" }
            }
    }

    private fun cardBoundsWith(state: MusicCardState): DpRect {
        musicState = state
        rule.waitForIdle()
        return rule.onNodeWithTag(MUSIC_CARD_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
    }

    private companion object {
        // Every geometry at the default display size, and the two display-size
        // opt-ins the dashboard goldens render.
        val cases: List<Pair<DashboardGeometry, UiScale>> =
            DashboardGeometries.all.map { it to UiScale.MEDIUM } +
                listOf(
                    DashboardGeometries.all.single { it.qualifiers == DashboardGeometries.PHONE_LANDSCAPE_915X412 } to
                        UiScale.SMALL,
                    DashboardGeometries.all.single { it.qualifiers == DashboardGeometries.HEAD_UNIT_853X512 } to
                        UiScale.LARGE,
                )
    }
}
