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
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.SelfMarkerAnchor
import io.github.seijikohara.femto.testfixtures.selfMarkerChevronBounds
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

/**
 * Where the vehicle's chevron sits across the dashboard geometry matrix ([DashboardGeometries]), at every Display size,
 * on both driver sides and with the dock on each edge, without a route and with one (the route row makes the speed
 * readout, and the bottom reserve with it, taller; the turn card is an overlay of its own and leaves the readout as
 * it is): above the speed readout's top edge and clear of the cards, also where the bottom overlays reach past the
 * middle of the map and the map raises the marker to their top edge. The map raises it no higher than
 * [SelfMarkerAnchor.maxMarkerRise], so where the overlays reach past that the chevron stays under them; those
 * arrangements are pinned in [RISE_CAPPED]. The cap is there to keep it from the clock and the compass; where the
 * chevron still meets one of them, the arrangement is pinned in [UNDER_TOP_OVERLAY]. Laid out as in
 * [SpeedReadoutPlacementTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w3000dp-h2880dp-mdpi")
class MarkerPlacementTest {
    @get:Rule
    val rule = createComposeRule()

    private var width by mutableStateOf(800.dp)
    private var height by mutableStateOf(480.dp)
    private var driverSide by mutableStateOf(DriverSide.RIGHT)
    private var dockPosition by mutableStateOf(DockPosition.BOTTOM)
    private var uiState by mutableStateOf(DashboardFixtures.state)

    // The map placement the dashboard hands the map, which puts the chevron.
    private var mapConfig = MapConfig()

    // One pass per state for both results: laying out the 480 arrangements is the cost, not the checks.
    @Test
    fun `the chevron sits above the speed readout and clear of the cards, except where the rise is capped`() {
        setDashboard()
        val capped = mutableListOf<String>()
        val underTop = mutableListOf<String>()
        val failures =
            STATES.flatMap { (name, state) ->
                uiState = state
                eachArrangement { arrangement ->
                    val case = "$name $arrangement"
                    underTop += topOverlays().filter { chevron().overlaps(bounds(it)) }.map { "$case $it" }
                    if (mapConfig.bottomSafeFraction > 0.5f + SelfMarkerAnchor.maxMarkerRise) {
                        capped += case
                        emptyList()
                    } else {
                        coverFailures(case)
                    }
                }
            }
        assertEquals(emptyList(), failures)
        assertEquals(RISE_CAPPED, capped.toList())
        assertEquals(UNDER_TOP_OVERLAY, underTop.toList())
    }

    // What the rise cap keeps the chevron away from at the top of the map.
    private fun topOverlays() = listOf(DashboardTags.CLOCK, DashboardTags.COMPASS)

    private fun setDashboard() =
        rule.setContent {
            FemtoTheme {
                Box(modifier = Modifier.size(width, height)) {
                    DashboardScaffold(
                        uiState = uiState,
                        is24Hour = true,
                        showClockSeconds = true,
                        speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                        mapConfig = MapConfig(),
                        panels = PanelVisibility(),
                        glassConfig = GlassConfig(),
                        onAction = {},
                        modifier = Modifier.fillMaxSize(),
                        dockPosition = dockPosition,
                        driverSide = driverSide,
                        clock = DashboardFixtures.fixedClock,
                        mapSurface = { _, config ->
                            mapConfig = config
                            Box(Modifier.fillMaxSize())
                        },
                    )
                }
            }
        }

    private fun eachArrangement(failures: (case: String) -> List<String>): List<String> =
        DashboardGeometries.all.flatMap { geometry ->
            UiScale.entries.flatMap { scale ->
                DriverSide.entries.flatMap { side ->
                    DockPosition.entries.flatMap { dock ->
                        width = (geometry.widthDp / scale.factor).dp
                        height = (geometry.heightDp / scale.factor).dp
                        driverSide = side
                        dockPosition = dock
                        rule.waitForIdle()
                        failures("${geometry.id} ${scale.name} ${side.name} ${dock.name}")
                    }
                }
            }
        }

    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun chevron(): DpRect = selfMarkerChevronBounds(mapConfig, width, height)

    private fun coverFailures(case: String): List<String> {
        val chevron = chevron()
        val speed = bounds(DashboardTags.SPEED)
        return listOfNotNull(
            "$case: chevron $chevron reaches below the speed readout's top ${speed.top}".takeIf {
                chevron.bottom > speed.top
            },
            "$case: chevron $chevron leaves the top of the screen".takeIf { chevron.top < 0.dp },
            "$case: chevron $chevron is under the cards".takeIf { chevron.overlaps(bounds(DashboardTags.CARDS)) },
        )
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private companion object {
        val STATES =
            listOf(
                "no route" to DashboardFixtures.state,
                "route" to DashboardFixtures.routeState,
            )

        // The arrangements whose bottom overlays reach past the rise cap, so the chevron stays under them: none,
        // without a route or with one, so the marker sits just above the speed readout everywhere.
        val RISE_CAPPED: List<String> = emptyList()

        // The arrangements that put the chevron under the clock or the compass. A phone in landscape at the largest
        // Display size with a route: the route row lifts the readout's top to about a quarter of the map's height,
        // inside the rise cap, and the marker raised to it meets the bottom corner of the clock beside the card
        // column (the chevron's box overlaps it by about 2 dp across and 1 dp down).
        val UNDER_TOP_OVERLAY: List<String> =
            listOf("RIGHT BOTTOM", "RIGHT TOP", "LEFT BOTTOM", "LEFT TOP").map {
                "route phone-landscape-915x412 LARGE $it ${DashboardTags.CLOCK}"
            }
    }
}
