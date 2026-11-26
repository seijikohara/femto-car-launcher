package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.RouteProgress
import io.github.seijikohara.femto.data.routing.RouteRowTime
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.DashboardTags
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.home.components.travelTimeText
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The speed readout's route row under the Navigation setting "Time shown for a route": the arrival time by default, the
 * travel time left on request, and a reroute's status in place of either. Laid out on the head unit (853×512 dp) at the
 * default scale, the geometry the route row was measured on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
class RouteRowTimeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `by default the row shows the arrival time`() {
        show(DashboardFixtures.routeState)

        rule.onNodeWithText("ETA 10:26").assertIsDisplayed()
        rule.onNodeWithText("18 min").assertDoesNotExist()
    }

    @Test
    fun `with time remaining the row shows the travel time left`() {
        show(DashboardFixtures.routeRemainingState)

        rule.onNodeWithText("18 min").assertIsDisplayed()
        rule.onNodeWithText("ETA 10:26").assertDoesNotExist()
    }

    // The duration takes its words from the device's language; an English word beside it would mix two languages.
    @Test
    @Config(qualifiers = "+ja")
    fun `under a Japanese locale the time left is the bare duration`() {
        show(DashboardFixtures.routeRemainingState)

        rule
            .onNodeWithText(
                travelTimeText(
                    checkNotNull(DashboardFixtures.routeRemainingState.activeRoute)
                        .progress.remainingSeconds
                        .roundToInt(),
                ),
            ).assertIsDisplayed()
    }

    @Test
    fun `a reroute's status takes the place of the time left`() {
        show(
            DashboardFixtures.routeRemainingState.copy(
                activeRoute = DashboardFixtures.routeRemainingState.activeRoute?.copy(
                    reroute = RerouteStatus.REROUTING,
                ),
            ),
        )

        rule.onNodeWithText("Rerouting…").assertIsDisplayed()
        rule.onNodeWithText("18 min").assertDoesNotExist()
    }

    // A day's drive with four digits of kilometres left: the widest time and distance the row plausibly shows. The
    // locale's short units fit, so the row never needs the narrow ones ("23h 59m").
    @Test
    fun `the longest plausible time left fits the row whole in the short units`() {
        show(
            DashboardFixtures.routeRemainingState.copy(
                activeRoute =
                    DashboardFixtures.routeRemainingState.activeRoute?.copy(
                        progress = RouteProgress(remainingMeters = 1_888_000.0, remainingSeconds = LONGEST_SECONDS),
                    ),
            ),
        )

        val text = rule.onNodeWithText(LONGEST_TEXT, useUnmergedTree = true)
        val layout =
            assertNotNull(
                mutableListOf<TextLayoutResult>()
                    .also {
                        text
                            .fetchSemanticsNode()
                            .config
                            .getOrNull(SemanticsActions.GetTextLayoutResult)
                            ?.action
                            ?.invoke(it)
                    }.firstOrNull(),
            )
        assertFalse(layout.isLineEllipsized(0), "\"$LONGEST_TEXT\" is cut short")
        val overlay = rule.onNodeWithTag(DashboardTags.SPEED).getUnclippedBoundsInRoot()
        val row = text.getUnclippedBoundsInRoot()
        assertTrue(row.right <= overlay.right, "\"$LONGEST_TEXT\" $row runs past the speed readout $overlay")
    }

    private fun show(uiState: HomeUiState) {
        rule.setContent {
            FemtoTheme {
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
                    clock = DashboardFixtures.fixedClock,
                    mapSurface = { _, _ -> },
                )
            }
        }
        rule.waitForIdle()
    }

    private companion object {
        // 23 hours and 59 minutes.
        const val LONGEST_SECONDS = 86_340.0
        const val LONGEST_TEXT = "23 hr, 59 min"
    }
}
