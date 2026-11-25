package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import io.github.seijikohara.femto.testfixtures.TurnCardFixtures
import io.github.seijikohara.femto.testfixtures.selfMarkerChevronBounds
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.DashboardTags
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.home.components.TurnCardMinWidth
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
 * Where the turn card (issue #389 phase 4) sits across the dashboard geometry matrix ([DashboardGeometries]) on both
 * driver sides, with its longest line and the turn after next showing: never over the compass, the map control
 * column, the clock, the speed readout, the cards or the vehicle's chevron at the default marker position, inside the
 * screen, no narrower than its minimum, and no line cut short. FitText cuts a line short only once even the 16 sp
 * floor would overflow, so a line not cut short is one at 16 sp or more. The largest Display size on the head unit
 * gets its own check: the card grows with the scale and must still end above the chevron. Each geometry is laid out at
 * its own size inside one window larger than all of them, as in RouteChoiceLayoutTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w2000dp-h1920dp-mdpi")
class TurnCardLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private var geometry by mutableStateOf(DashboardGeometries.all.first())
    private var driverSide by mutableStateOf(DriverSide.RIGHT)
    private var uiScale by mutableStateOf(UiScale.MEDIUM)

    // The map placement the dashboard hands the map, which puts the chevron.
    private var mapConfig = MapConfig()

    @Test
    fun `the card clears every dashboard overlay and the chevron, at every geometry`() {
        setDashboard()

        assertEquals(emptyList(), failuresOver(DashboardGeometries.all))
    }

    // A phone in portrait gives the card the least width of any geometry, about its minimum.
    @Test
    fun `on a phone in portrait the card keeps its minimum width and its text whole`() {
        setDashboard()

        assertEquals(emptyList(), failuresOver(DashboardGeometries.all.filter { it.id == "phone-portrait-412x915" }))
    }

    // The largest Display size scales every dp, the card's height with it, on a screen whose dp viewport shrinks by
    // the same factor: the card, grown with it, still ends above the chevron and inside the screen.
    @Test
    fun `at the largest display size on the head unit the card still clears the chevron`() {
        uiScale = UiScale.entries.maxBy { it.factor }
        setDashboard()

        assertEquals(
            emptyList(),
            failuresOver(DashboardGeometries.all.filter { it.id == "head-unit-853x512" }, everyOverlay = false),
        )
    }

    private fun setDashboard() =
        rule.setContent {
            // The geometry's box sits outside the theme, so the display size scales the dashboard inside it, not the
            // screen.
            Box(modifier = Modifier.size(geometry.widthDp.dp, geometry.heightDp.dp)) {
                FemtoTheme(uiScale = uiScale) {
                    DashboardScaffold(
                        uiState = LONGEST_TURN_STATE,
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
                        mapSurface = { _, config ->
                            mapConfig = config
                            Box(Modifier.fillMaxSize())
                        },
                    )
                }
            }
        }

    private fun failuresOver(
        geometries: List<DashboardGeometry>,
        everyOverlay: Boolean = true,
    ): List<String> =
        geometries.flatMap { candidate ->
            DriverSide.entries.flatMap { side ->
                geometry = candidate
                driverSide = side
                rule.waitForIdle()
                layoutFailures(candidate, side, everyOverlay)
            }
        }

    // In the dashboard's own dp, which the display size scales: a node's bounds come in the density it was laid out
    // with.
    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    // [everyOverlay] off checks only the chevron, the screen's edges and the text.
    private fun layoutFailures(
        geometry: DashboardGeometry,
        side: DriverSide,
        everyOverlay: Boolean,
    ): List<String> {
        val case = "${geometry.id} ${side.name} ${uiScale.name}"
        val card = bounds(DashboardTags.TURN_CARD)
        val screenWidth = geometry.widthDp.dp / uiScale.factor
        val screenHeight = geometry.heightDp.dp / uiScale.factor
        val chevron = selfMarkerChevronBounds(mapConfig, screenWidth, screenHeight)
        val overlaps =
            listOf(
                DashboardTags.COMPASS,
                DashboardTags.MAP_CONTROLS,
                DashboardTags.CLOCK,
                DashboardTags.SPEED,
                DashboardTags.CARDS,
            ).filter { everyOverlay && card.overlaps(bounds(it)) }
                .map { "$case: card $card covers $it ${bounds(it)}" }
        return overlaps + cutLines(case, card) +
            listOfNotNull(
                "$case: card $card covers the chevron $chevron".takeIf { card.overlaps(chevron) },
                "$case: card $card leaves the screen".takeIf {
                    card.left < 0.dp || card.right > screenWidth || card.top < 0.dp || card.bottom > screenHeight
                },
                "$case: card $card is narrower than its minimum".takeIf {
                    everyOverlay && card.width < TurnCardMinWidth
                },
            )
    }

    // Every line of text in the card, laid out in full and shown in full: none ellipsised, none taller than its box,
    // none outside the card.
    private fun cutLines(
        case: String,
        card: DpRect,
    ): List<String> =
        rule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
                    hasAnyAncestor(hasTestTag(DashboardTags.TURN_CARD)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            .mapNotNull { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                val layout =
                    mutableListOf<TextLayoutResult>()
                        .also {
                            node.config
                                .getOrNull(SemanticsActions.GetTextLayoutResult)
                                ?.action
                                ?.invoke(it)
                        }.firstOrNull()
                val box =
                    with(node.layoutInfo.density) {
                        val origin = node.positionInRoot
                        DpRect(
                            left = origin.x.toDp(),
                            top = origin.y.toDp(),
                            right = (origin.x + node.size.width).toDp(),
                            bottom = (origin.y + node.size.height).toDp(),
                        )
                    }
                "$case: \"$text\" is cut short".takeIf {
                    layout == null ||
                        layout.didOverflowHeight ||
                        (0 until layout.lineCount).any(layout::isLineEllipsized) ||
                        !card.contains(box)
                }
            }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun DpRect.contains(other: DpRect): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

    private companion object {
        // The card's longest line ("Enter the roundabout") with the turn after next beside it: the least room any
        // text on the card gets.
        val LONGEST_TURN_STATE: HomeUiState =
            DashboardFixtures.routeTurnThenState.let { state ->
                state.copy(
                    activeRoute =
                        state.activeRoute?.copy(
                            turnCard =
                                TurnCardFixtures.turnThen.copy(
                                    maneuver = TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE),
                                ),
                        ),
                )
            }
    }
}
