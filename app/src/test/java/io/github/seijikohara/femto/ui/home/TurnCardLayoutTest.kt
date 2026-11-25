package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
import io.github.seijikohara.femto.ui.home.components.TurnCardForm
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
 * Where the turn card (issue #389 phase 4) sits across the dashboard geometry matrix ([DashboardGeometries]), on both
 * driver sides, with its longest line and the turn after next showing. At the default Display size it shows in full
 * everywhere: never over the compass, the map control column, the clock, the speed readout, the cards or the vehicle's
 * chevron at the default marker position, inside the screen, no narrower than its minimum, and no line cut short.
 * FitText cuts a line short only once even the 16 sp floor would overflow, so a line not cut short is one at 16 sp or
 * more. At every Display size, wherever it shows, it ends a gap above the speed readout, inside the screen and clear of
 * the chevron; a table pins which form each geometry and size gets. Each geometry is laid out at its own size inside
 * one window larger than all of them, as in RouteChoiceLayoutTest; the Display size scales the dashboard inside it.
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
    private var fontScale by mutableFloatStateOf(1f)

    // The map placement the dashboard hands the map, which puts the chevron.
    private var mapConfig = MapConfig()

    @Test
    fun `at the default display size the card shows in full and clears every overlay and the chevron`() {
        setDashboard()

        assertEquals(
            emptyList(),
            each(DashboardGeometries.all) { case ->
                listOfNotNull("$case: shows ${shownForm()}".takeIf { shownForm() != TurnCardForm.FULL }) +
                    coverFailures(case) +
                    cutLines(case) +
                    listOfNotNull(
                        "$case: card ${card()} is narrower than its minimum".takeIf {
                            card().width < TurnCardMinWidth
                        },
                    )
            },
        )
    }

    // The card's cap is the room above the speed readout, less the gap every card keeps to its neighbours.
    @Test
    fun `at every display size the card shows only where it ends above the speed readout`() {
        setDashboard()

        assertEquals(
            emptyList(),
            UiScale.entries.flatMap { scale ->
                uiScale = scale
                each(DashboardGeometries.all) { case ->
                    if (shownForm() == null) {
                        emptyList()
                    } else {
                        roomFailures(case) + cutLines(case)
                    }
                }
            },
        )
    }

    // A later layout change that gains or loses room shows here.
    @Test
    fun `each geometry and display size gets its form`() {
        setDashboard()

        val forms =
            UiScale.entries.flatMap { scale ->
                uiScale = scale
                DashboardGeometries.all.flatMap { candidate ->
                    DriverSide.entries.map { side ->
                        geometry = candidate
                        driverSide = side
                        rule.waitForIdle()
                        "${candidate.id} ${scale.name} ${side.name}" to shownForm()
                    }
                }
            }
        assertEquals(
            forms.map { (case, _) -> case to expectedForm(case) },
            forms,
        )
    }

    // A larger system font grows the full card past the room the phone in landscape leaves it.
    @Test
    fun `a larger font on a phone in landscape falls back to the compact card and covers nothing`() {
        fontScale = LARGER_FONT_SCALE
        setDashboard()

        assertEquals(
            emptyList(),
            each(DashboardGeometries.all.filter { it.id == "phone-landscape-915x412" }) { case ->
                listOfNotNull(
                    "$case: shows ${shownForm()}".takeIf {
                        shownForm() != TurnCardForm.COMPACT_THEN && shownForm() != TurnCardForm.COMPACT
                    },
                ) + coverFailures(case) + roomFailures(case) + cutLines(case)
            },
        )
    }

    private fun setDashboard() =
        rule.setContent {
            // The geometry's box and the font scale sit outside the theme, so the display size scales the dashboard
            // inside the box, not the box, and the font scale reaches the theme as the device's would.
            Box(modifier = Modifier.size(geometry.widthDp.dp, geometry.heightDp.dp)) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
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
        }

    // [failures] for each geometry of [geometries] on each driver side, at the current display size.
    private fun each(
        geometries: List<DashboardGeometry>,
        failures: (case: String) -> List<String>,
    ): List<String> =
        geometries.flatMap { candidate ->
            DriverSide.entries.flatMap { side ->
                geometry = candidate
                driverSide = side
                rule.waitForIdle()
                failures("${candidate.id} ${uiScale.name} ${side.name}")
            }
        }

    // In the dashboard's own dp, which the display size scales: a node's bounds come in the density it was laid out
    // with.
    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun card(): DpRect = bounds(DashboardTags.TURN_CARD)

    private fun screenWidth() = geometry.widthDp.dp / uiScale.factor

    private fun screenHeight() = geometry.heightDp.dp / uiScale.factor

    private fun chevron(): DpRect = selfMarkerChevronBounds(mapConfig, screenWidth(), screenHeight())

    // The form the card shows, read off what it draws: its one-sentence node, the full form's line of text, and the
    // turn after next's caption. Null where the card shows nothing.
    private fun shownForm(): TurnCardForm? =
        when {
            countInCard(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)) == 0 -> null
            countInCard(hasText(LONGEST_TEXT)) > 0 -> TurnCardForm.FULL
            countInCard(hasText("Then")) > 0 -> TurnCardForm.COMPACT_THEN
            else -> TurnCardForm.COMPACT
        }

    private fun countInCard(matcher: SemanticsMatcher): Int =
        rule
            .onAllNodes(matcher and hasAnyAncestor(hasTestTag(DashboardTags.TURN_CARD)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size

    private fun coverFailures(case: String): List<String> =
        listOf(
            DashboardTags.COMPASS,
            DashboardTags.MAP_CONTROLS,
            DashboardTags.CLOCK,
            DashboardTags.SPEED,
            DashboardTags.CARDS,
        ).filter { card().overlaps(bounds(it)) }
            .map { "$case: card ${card()} covers $it ${bounds(it)}" } +
            listOfNotNull("$case: card ${card()} covers the chevron ${chevron()}".takeIf { card().overlaps(chevron()) })

    // The gap is the dashboard's outer margin, the compass's inset from the top edge.
    private fun roomFailures(case: String): List<String> {
        val card = card()
        val speedTop = bounds(DashboardTags.SPEED).top
        val gap = bounds(DashboardTags.COMPASS).top
        return listOfNotNull(
            "$case: card $card ends below the speed readout's top $speedTop less the gap $gap".takeIf {
                card.bottom > speedTop - gap
            },
            "$case: card $card leaves the screen".takeIf {
                card.left < 0.dp || card.right > screenWidth() || card.top < 0.dp || card.bottom > screenHeight()
            },
            "$case: card $card covers the chevron ${chevron()}".takeIf { card.overlaps(chevron()) },
        )
    }

    // Every line of text in the card, laid out in full and shown in full: none ellipsised, none wider or taller than
    // its box, none outside the card.
    private fun cutLines(case: String): List<String> {
        val card = card()
        return rule
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
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun DpRect.contains(other: DpRect): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

    private companion object {
        // The card's longest line, with the turn after next beside it: the least room any text on the card gets.
        const val LONGEST_TEXT = "Enter the roundabout"

        // A larger step of Android's own font-size setting.
        const val LARGER_FONT_SCALE = 1.3f

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

        // The forms that differ from the full one at the default font, by geometry and display size (both driver
        // sides alike). Where the speed readout leaves less room than the full card's 88 dp, or the slot is narrower
        // than TurnCardMinWidth, the card falls back to the compact one: 64 dp tall with the turn after next, 56 dp
        // without it (the head unit at Large leaves 63.75 dp). Where it leaves less than that, the card is left out.
        val FORMS_OTHER_THAN_FULL: Map<String, TurnCardForm?> =
            mapOf(
                "head-unit-853x512 LARGE" to TurnCardForm.COMPACT,
                "phone-portrait-412x915 COMFORTABLE" to TurnCardForm.COMPACT_THEN,
                "phone-portrait-412x915 LARGE" to null,
                "floor-800x480 LARGE" to null,
                "phone-landscape-915x412 COMFORTABLE" to null,
                "phone-landscape-915x412 LARGE" to null,
            )

        fun expectedForm(case: String): TurnCardForm? =
            case.substringBeforeLast(' ').let { key ->
                if (key in FORMS_OTHER_THAN_FULL) FORMS_OTHER_THAN_FULL[key] else TurnCardForm.FULL
            }
    }
}
