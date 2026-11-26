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
import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import io.github.seijikohara.femto.testfixtures.TurnCardFixtures
import io.github.seijikohara.femto.testfixtures.fakeVideoUiState
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
import io.github.seijikohara.femto.ui.video.VideoUiState
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
 * more. At every Display size, with the video window off and on, wherever it shows it ends a gap above the speed
 * readout, stays inside the screen and clear of the chevron and the video window, which it hides where the two do not
 * both fit; two tables pin the form and the slot each geometry and size gets, and whether the window shows. Each geometry is laid out at its own size inside one window larger than all of them, as
 * in RouteChoiceLayoutTest; the Display size scales the dashboard inside it.
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
    private var videoOn by mutableStateOf(false)
    private var uiState by mutableStateOf(LONGEST_TURN_STATE)

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

    // The card's cap is the room above the speed readout, less the gap every card keeps to its neighbours, and with the
    // video window on, the card keeps clear of it too.
    @Test
    fun `at every display size the card shows only where it ends above the speed readout and clear of the video`() {
        setDashboard()

        assertEquals(
            emptyList(),
            listOf(false, true).flatMap { on ->
                videoOn = on
                UiScale.entries.flatMap { scale ->
                    uiScale = scale
                    each(DashboardGeometries.all) { case -> shownFailures(case) }
                }
            },
        )
    }

    // The window sits below the clock on the head unit, so the card takes the top row beside the clock, where the
    // room above the window leaves it the compact form (65 dp: the turn after next still fits).
    @Test
    fun `with the video window on the head unit the compact card takes the top row, clear of the window`() {
        videoOn = true
        setDashboard()

        assertEquals(
            emptyList(),
            each(DashboardGeometries.all.filter { it.id == "head-unit-853x512" }) { case ->
                listOfNotNull(
                    "$case: shows ${placementLabel()}".takeIf {
                        placementLabel() !in listOf("compact top +window", "compact+then top +window")
                    },
                ) +
                    videoFailures(case) + coverFailures(case) +
                    listOfNotNull("$case: the window is hidden".takeIf { !videoShown() })
            },
        )
    }

    // The window's presence follows the card's stable sizes: at a red light the distance moving between 94 m and 104 m,
    // or the turn after next coming and going, must not make it come and go. Where the card hides it, it stays hidden;
    // where both fit, it stays.
    @Test
    fun `the window does not come and go with the distance or the turn after next`() {
        videoOn = true
        setDashboard()

        assertEquals(
            mapOf("mainstream-1280x720" to listOf(false), "head-unit-853x512" to listOf(true)),
            listOf("mainstream-1280x720", "head-unit-853x512").associateWith { id ->
                geometry = DashboardGeometries.all.single { it.id == id }
                FLICKER_STATES
                    .map { state ->
                        uiState = state
                        rule.waitForIdle()
                        videoShown()
                    }.distinct()
            },
        )
    }

    // Where the two do not both fit, the card takes its place as if the window were not there, and the window is not
    // drawn; it comes back as soon as the card goes, whatever sent the card away.
    @Test
    fun `the hidden video window comes back when the card goes`() {
        videoOn = true
        setDashboard()
        geometry = DashboardGeometries.all.single { it.id == "mainstream-1280x720" }

        assertEquals(
            CARD_GONE.keys.map { it to true },
            CARD_GONE.map { (reason, gone) ->
                uiState = LONGEST_TURN_STATE
                rule.waitForIdle()
                val hiddenWithTheCard = shownForm() != null && !videoShown()
                uiState = gone(LONGEST_TURN_STATE)
                rule.waitForIdle()
                reason to (hiddenWithTheCard && shownForm() == null && videoShown())
            },
        )
    }

    // A later layout change that gains or loses room shows here.
    @Test
    fun `each geometry and display size gets its form and slot`() {
        setDashboard()

        assertEquals(PLACEMENTS_VIDEO_OFF, placements())
    }

    @Test
    fun `each geometry and display size gets its form and slot with the video window on`() {
        videoOn = true
        setDashboard()

        assertEquals(PLACEMENTS_VIDEO_ON, placements())
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

    // The full card measures its line of text at the 16 sp floor: where even that would be cut, it falls back to the
    // compact card rather than cut it.
    @Test
    fun `a larger font cuts no text on any geometry`() {
        fontScale = LARGER_FONT_SCALE
        setDashboard()

        assertEquals(
            emptyList(),
            each(DashboardGeometries.all) { case -> shownFailures(case) },
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
                            driverSide = driverSide,
                            clock = DashboardFixtures.fixedClock,
                            mapSurface = { _, config ->
                                mapConfig = config
                                Box(Modifier.fillMaxSize())
                            },
                            video = if (videoOn) fakeVideoUiState() else VideoUiState.Off,
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

    // The form and the slot each geometry gets at each display size, by geometry, in UiScale order; both driver sides
    // must agree, or the left side's differing label is added after a slash.
    private fun placements(): Map<String, List<String>> =
        DashboardGeometries.all.associate { candidate ->
            geometry = candidate
            candidate.id to
                UiScale.entries.map { scale ->
                    uiScale = scale
                    DriverSide.entries
                        .map { side ->
                            driverSide = side
                            rule.waitForIdle()
                            placementLabel()
                        }.distinct()
                        .joinToString(" / ")
                }
        }

    // "full", "compact" or "compact+then", and "top" for the top row beside the clock or "below" for under it; "none"
    // where the card shows nothing. With the video window on, "+window" where the window shows.
    private fun placementLabel(): String =
        when (shownForm()) {
            null -> "none"
            TurnCardForm.FULL -> "full"
            TurnCardForm.COMPACT_THEN -> "compact+then"
            TurnCardForm.COMPACT -> "compact"
        }.let { form -> if (form == "none") form else "$form ${slotLabel()}" }
            .let { label -> if (videoOn && videoShown()) "$label +window" else label }

    private fun slotLabel(): String = if (card().top < bounds(DashboardTags.CLOCK).bottom) "top" else "below"

    // Where the card shows: what it must keep clear of, and its text whole.
    private fun shownFailures(case: String): List<String> =
        if (shownForm() == null) emptyList() else roomFailures(case) + videoFailures(case) + cutLines(case)

    // With the video window on, the card and the window must not meet: the window is either clear of the card or not
    // drawn at all, and then out of the semantics tree.
    private fun videoFailures(case: String): List<String> =
        if (!videoOn || !videoShown()) {
            emptyList()
        } else {
            listOfNotNull(
                "$case: card ${card()} covers the video window ${bounds(DashboardTags.VIDEO_WINDOW)}".takeIf {
                    card().overlaps(bounds(DashboardTags.VIDEO_WINDOW))
                },
            )
        }

    private fun videoShown(): Boolean =
        rule
            .onAllNodes(hasTestTag(DashboardTags.VIDEO_WINDOW), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    // The form the card shows, read off what it draws: the full form's line of text, and the turn after next's
    // caption. Null where the dashboard places no card: the card's own node carries the tag, and the dashboard
    // composes it only where a form fits.
    private fun shownForm(): TurnCardForm? =
        when {
            rule
                .onAllNodes(hasTestTag(DashboardTags.TURN_CARD), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty() -> null

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
        ).plus(if (videoOn) listOf(DashboardTags.VIDEO_WINDOW) else emptyList())
            .filter { card().overlaps(bounds(it)) }
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

        // The form and the slot each geometry gets at the default font, for Display sizes SMALL, COMPACT, MEDIUM,
        // COMFORTABLE and LARGE.
        val PLACEMENTS_VIDEO_OFF: Map<String, List<String>> =
            mapOf(
                "floor-800x480" to listOf("full top", "full below", "full below", "full below", "none"),
                "head-unit-853x512" to listOf("full top", "full top", "full below", "full below", "compact below"),
                "budget-1024x600" to listOf("full top", "full top", "full top", "full below", "full below"),
                "mainstream-1280x720" to listOf("full top", "full top", "full top", "full top", "full top"),
                "ultrawide-1920x720" to listOf("full top", "full top", "full top", "full top", "full top"),
                "flagship-1920x1080" to listOf("full top", "full top", "full top", "full top", "full top"),
                "premium-2000x1200" to listOf("full top", "full top", "full top", "full top", "full top"),
                "phone-landscape-915x412" to listOf("full top", "full top", "full below", "none", "none"),
                "phone-portrait-412x915" to
                    listOf("full below", "full below", "full below", "compact+then below", "none"),
                "tablet-800x1280" to listOf("full top", "full top", "full top", "full top", "full below"),
                "car-portrait-1024x1365" to listOf("full top", "full top", "full top", "full top", "full top"),
                "car-portrait-tall-1200x1920" to listOf("full top", "full top", "full top", "full top", "full top"),
            )

        // With the video window on, the card takes the same form and slot as without it: where the two do not both
        // fit, the window is not drawn while the card shows. Only where both fit, in the top row above a window below
        // the clock, is the card's place its own; and where no card fits, the window shows as ever.
        val PLACEMENTS_VIDEO_ON: Map<String, List<String>> =
            PLACEMENTS_VIDEO_OFF.mapValues { (_, row) -> row.map { if (it == "none") "none +window" else it } } +
                mapOf(
                    "head-unit-853x512" to
                        listOf("full top", "full top", "compact+then top +window", "full below", "compact below"),
                    "phone-portrait-412x915" to
                        listOf("full below", "compact top +window", "full below", "compact+then below", "none +window"),
                )

        // The same turn at 94 m and at 104 m, with and without the turn after next.
        val FLICKER_STATES: List<HomeUiState> =
            listOf(94.0, 104.0).flatMap { meters ->
                listOf(TurnCardFixtures.turnThen, TurnCardFixtures.turn).map { turn ->
                    LONGEST_TURN_STATE.let { state ->
                        state.copy(activeRoute = state.activeRoute?.copy(turnCard = turn.copy(distanceMeters = meters)))
                    }
                }
            }

        // Each way the card goes while the route stays on the map or leaves it: the window must come back.
        val CARD_GONE: Map<String, (HomeUiState) -> HomeUiState> =
            mapOf(
                "the route is cleared" to { state -> state.copy(activeRoute = null) },
                "the vehicle is off the route" to { state ->
                    state.copy(activeRoute = state.activeRoute?.copy(turnCard = null))
                },
                "a reroute runs" to { state ->
                    state.copy(
                        activeRoute = state.activeRoute?.copy(turnCard = null, reroute = RerouteStatus.REROUTING),
                    )
                },
                "the choice card opens" to { state ->
                    state.copy(
                        activeRoute =
                            state.activeRoute?.copy(
                                turnCard = null,
                                choice = DashboardFixtures.routeChoiceState.activeRoute?.choice,
                            ),
                    )
                },
                "the setting is switched off" to { state ->
                    state.copy(activeRoute = state.activeRoute?.copy(turnCard = null))
                },
            )
    }
}
