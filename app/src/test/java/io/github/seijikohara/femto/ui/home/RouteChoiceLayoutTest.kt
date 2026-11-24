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
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.DashboardTags
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Where the route choice card sits across the dashboard geometry matrix ([DashboardGeometries]) on both driver sides,
 * at its tallest (three routes): never over the compass, the map control column, the clock, the speed readout or the
 * cards, inside the screen, and always tall enough for its header, so Done stays in reach. Each geometry is laid out
 * at its own size inside one window larger than all of them, as in VideoWindowLayoutTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w2000dp-h1920dp-mdpi")
class RouteChoiceLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private var geometry by mutableStateOf(DashboardGeometries.all.first())
    private var driverSide by mutableStateOf(DriverSide.RIGHT)

    @Test
    fun `the card clears every dashboard overlay at every geometry`() {
        rule.setContent {
            FemtoTheme {
                Box(modifier = Modifier.size(geometry.widthDp.dp, geometry.heightDp.dp)) {
                    DashboardScaffold(
                        uiState = DashboardFixtures.routeChoiceState,
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
        val card = bounds(DashboardTags.ROUTE_CHOICE)
        val overlaps =
            listOf(
                DashboardTags.COMPASS,
                DashboardTags.MAP_CONTROLS,
                DashboardTags.CLOCK,
                DashboardTags.SPEED,
                DashboardTags.CARDS,
            ).filter { card.overlaps(bounds(it)) }
                .map { "$case: card $card covers $it ${bounds(it)}" }
        return overlaps +
            listOfNotNull(
                "$case: card $card is shorter than its header".takeIf { card.height < FemtoDimens.MinTouchTarget },
                "$case: card $card leaves the screen".takeIf {
                    card.left < 0.dp || card.right > geometry.widthDp.dp || card.top < 0.dp
                },
            )
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}
