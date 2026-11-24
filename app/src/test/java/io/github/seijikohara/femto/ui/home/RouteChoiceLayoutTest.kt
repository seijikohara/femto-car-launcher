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
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import io.github.seijikohara.femto.testfixtures.selfMarkerChevronBounds
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
 * at its widest content (three routes, one over an hour): never over the compass, the map control column, the clock,
 * the speed readout, the cards or the vehicle's chevron at the default marker position, inside the screen, with every
 * tile whole inside the card and a full tap target, and no line of a tile cut short. Each geometry is laid out at its
 * own size inside one window larger than all of them, as in VideoWindowLayoutTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w2000dp-h1920dp-mdpi")
class RouteChoiceLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private var geometry by mutableStateOf(DashboardGeometries.all.first())
    private var driverSide by mutableStateOf(DriverSide.RIGHT)

    // The map placement the dashboard hands the map, which puts the chevron.
    private var mapConfig = MapConfig()

    @Test
    fun `the card clears every dashboard overlay and the chevron, and holds its tiles whole, at every geometry`() {
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
                        mapSurface = { _, config ->
                            mapConfig = config
                            Box(Modifier.fillMaxSize())
                        },
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
        val chevron = selfMarkerChevronBounds(mapConfig, geometry.widthDp.dp, geometry.heightDp.dp)
        val tiles = (1..3).map { rule.onNodeWithText("Route $it").getUnclippedBoundsInRoot() }
        val overlaps =
            listOf(
                DashboardTags.COMPASS,
                DashboardTags.MAP_CONTROLS,
                DashboardTags.CLOCK,
                DashboardTags.SPEED,
                DashboardTags.CARDS,
            ).filter { card.overlaps(bounds(it)) }
                .map { "$case: card $card covers $it ${bounds(it)}" }
        val tileFailures =
            tiles.flatMapIndexed { index, tile ->
                listOfNotNull(
                    "$case: tile ${index + 1} $tile is not whole inside the card $card".takeIf { !card.contains(tile) },
                    "$case: tile ${index + 1} $tile is under the tap-target floor".takeIf {
                        tile.width < FemtoDimens.MinTouchTarget || tile.height < FemtoDimens.MinTouchTarget
                    },
                )
            }
        return overlaps + tileFailures + cutLines(case, card) +
            listOfNotNull(
                "$case: card $card covers the chevron $chevron".takeIf { card.overlaps(chevron) },
                "$case: card $card leaves the screen".takeIf {
                    card.left < 0.dp || card.right > geometry.widthDp.dp || card.top < 0.dp
                },
            )
    }

    // Every line of text in the card, laid out in full and shown in full: none ellipsised, none taller than its box,
    // none outside the card. A line may wrap; nothing is cut. Width overflow is not asked: for plain text the layout
    // reports its paragraph at the full width offered, wider than the text it draws.
    private fun cutLines(
        case: String,
        card: DpRect,
    ): List<String> =
        rule
            .onAllNodesInCard()
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
                // Unclipped, so a line the card cuts off still shows outside it.
                val box =
                    with(rule.density) {
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

    private fun ComposeContentTestRule.onAllNodesInCard() =
        onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
                hasAnyAncestor(hasTestTag(DashboardTags.ROUTE_CHOICE)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun DpRect.contains(other: DpRect): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom
}
