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
import androidx.compose.ui.unit.width
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import io.github.seijikohara.femto.testfixtures.fakeVideoUiState
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.DashboardTags
import io.github.seijikohara.femto.ui.home.components.GlassConfig
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
import kotlin.test.assertTrue

/**
 * Where the video window sits across the dashboard geometry matrix
 * ([DashboardGeometries]) on both driver sides: never over the compass, the
 * map control column, the clock, the speed readout or the cards, and in the
 * upper half of the screen, clear of the self-marker (which the map drops
 * below centre) and of the map credit in the bottom corner. Each geometry is
 * laid out at its own size inside one window larger than all of them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w2000dp-h1920dp-mdpi")
class VideoWindowLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private var geometry by mutableStateOf(DashboardGeometries.all.first())
    private var driverSide by mutableStateOf(DriverSide.RIGHT)

    @Test
    fun `the window clears every dashboard overlay at every geometry`() {
        rule.setContent {
            FemtoTheme {
                Box(modifier = Modifier.size(geometry.widthDp.dp, geometry.heightDp.dp)) {
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
                        driverSide = driverSide,
                        clock = DashboardFixtures.fixedClock,
                        mapSurface = { _, _ -> Box(Modifier.fillMaxSize()) },
                        video = fakeVideoUiState(),
                    )
                }
            }
        }
        val failures =
            DashboardGeometries.all.flatMap { candidate ->
                DriverSide.entries.flatMap { side ->
                    geometry = candidate
                    driverSide = side
                    rule.waitForIdle()
                    layoutFailures(candidate, side)
                }
            }
        assertEquals(emptyList(), failures)
    }

    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun layoutFailures(
        geometry: DashboardGeometry,
        side: DriverSide,
    ): List<String> {
        val case = "${geometry.id} ${side.name}"
        val window = bounds(DashboardTags.VIDEO_WINDOW)
        val overlaps =
            listOf(
                DashboardTags.COMPASS,
                DashboardTags.MAP_CONTROLS,
                DashboardTags.CLOCK,
                DashboardTags.SPEED,
                DashboardTags.CARDS,
            ).filter { window.overlaps(bounds(it)) }
                .map { "$case: window $window covers $it ${bounds(it)}" }
        val aspect = window.width / window.height
        return overlaps +
            listOfNotNull(
                "$case: window $window reaches below the screen's middle".takeIf {
                    window.bottom > (geometry.heightDp / 2).dp
                },
                "$case: window $window is not 16:9".takeIf { aspect !in 1.75f..1.80f },
                "$case: window $window leaves the screen".takeIf {
                    window.left < 0.dp || window.right > geometry.widthDp.dp || window.top < 0.dp
                },
            )
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    @Test
    fun `the overlap check catches an overlap`() {
        // Falsifiability guard for the helper the matrix test leans on.
        assertTrue(DpRect(0.dp, 0.dp, 10.dp, 10.dp).overlaps(DpRect(9.dp, 9.dp, 20.dp, 20.dp)))
        assertTrue(!DpRect(0.dp, 0.dp, 10.dp, 10.dp).overlaps(DpRect(10.dp, 0.dp, 20.dp, 10.dp)))
    }
}
