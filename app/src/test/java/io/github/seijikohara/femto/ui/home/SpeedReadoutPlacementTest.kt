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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
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
import kotlin.math.abs
import kotlin.test.assertEquals

/**
 * Where the speed readout sits across the dashboard geometry matrix ([DashboardGeometries]), at every Display size,
 * on both driver sides and with the dock on each edge: its centre line is the vehicle marker's, and in portrait it
 * ends where the card band begins, so the band's own padding is the one gap between it and the cards. A Display size
 * multiplies the density, so a screen at a scale lays out like the same screen with its sides divided by the scale's
 * factor; each case is laid out at that size inside one window larger than all of them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w3000dp-h2880dp-mdpi")
class SpeedReadoutPlacementTest {
    @get:Rule
    val rule = createComposeRule()

    private var width by mutableStateOf(800.dp)
    private var height by mutableStateOf(480.dp)
    private var driverSide by mutableStateOf(DriverSide.RIGHT)
    private var dockPosition by mutableStateOf(DockPosition.BOTTOM)

    // The map placement the dashboard hands the map, which puts the chevron.
    private var mapConfig = MapConfig()

    // One pass for both rules: laying out the 480 arrangements is the cost, not the checks.
    @Test
    fun `the readout is centred on the chevron, and in portrait it ends where the card band begins`() {
        setDashboard()
        assertEquals(
            emptyList(),
            eachArrangement { case -> listOfNotNull(offCentre(case), if (height > width) offTheBand(case) else null) },
        )
    }

    private fun setDashboard() =
        rule.setContent {
            FemtoTheme {
                Box(modifier = Modifier.size(width, height)) {
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

    private fun offCentre(case: String): String? {
        val speed = bounds(DashboardTags.SPEED)
        val chevron = selfMarkerChevronBounds(mapConfig, width, height)
        val off = (speed.left + speed.right) / 2 - (chevron.left + chevron.right) / 2
        return "$case: the readout's centre is ${off.shown()} from the chevron's".takeIf { off.isOff() }
    }

    private fun offTheBand(case: String): String? {
        val off = bounds(DashboardTags.CARDS).top - bounds(DashboardTags.SPEED).bottom
        return "$case: the readout ends ${off.shown()} above the card band".takeIf { off.isOff() }
    }

    private fun Dp.isOff(): Boolean = abs(value) > TOLERANCE_DP

    private fun Dp.shown(): String = "%.1f dp".format(value)

    private companion object {
        // Rounding to whole pixels on each side, at the fractional sizes the Display sizes give.
        const val TOLERANCE_DP = 1f
    }
}
