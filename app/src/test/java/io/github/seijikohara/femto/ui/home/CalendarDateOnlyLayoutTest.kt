package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
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
import kotlin.math.abs
import kotlin.test.assertEquals

/**
 * The date-only calendar card (Settings → Panels → Date only) across the
 * dashboard geometry matrix ([DashboardGeometries]). Beside the weather card it
 * keeps the agenda card's bounds, tappable over its whole height. Without the
 * weather card it wraps its head, so the card column ends sooner; the head keeps
 * its place inside the card, a landscape card keeps its top, and in portrait the
 * speed overlay and the self-marker reserve (MapConfig.bottomSafeFraction)
 * follow the shorter band down. Each geometry is laid out at its own size inside
 * one window larger than all of them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w2000dp-h1920dp-mdpi")
class CalendarDateOnlyLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private var geometry by mutableStateOf(DashboardGeometries.all.first())
    private var panels by mutableStateOf(PanelVisibility())
    private var dateOnly by mutableStateOf(false)

    // The placement inputs the map last composed with: bottomSafeFraction is the
    // self-marker's reserve above the bottom overlays.
    private var mapConfig: MapConfig? = null

    @Test
    fun `beside the weather card the date-only card keeps the agenda card's bounds`() {
        setDashboard()
        val failures =
            DashboardGeometries.all.flatMap { candidate ->
                val agenda = layoutOf(candidate, PanelVisibility(), calendarDateOnly = false)
                val headOnly = layoutOf(candidate, PanelVisibility(), calendarDateOnly = true)
                listOfNotNull(
                    "${candidate.id}: card ${headOnly.calendar} moved from ${agenda.calendar}".takeUnless {
                        headOnly.calendar.near(agenda.calendar)
                    },
                    "${candidate.id}: head ${headOnly.head} moved from ${agenda.head}".takeUnless {
                        headOnly.head.near(agenda.head)
                    },
                    "${candidate.id}: tap area ${headOnly.calendar} is shorter than the weather card ${headOnly.weather}"
                        .takeUnless { headOnly.calendar.height.near(checkNotNull(headOnly.weather).height) },
                )
            }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun `without the weather card the date-only card wraps its head`() =
        assertWrapsHead(PanelVisibility(weather = false))

    @Test
    fun `as the only card the date-only card wraps its head`() =
        assertWrapsHead(PanelVisibility(weather = false, music = false))

    private fun assertWrapsHead(wrappedPanels: PanelVisibility) {
        setDashboard()
        val cases =
            DashboardGeometries.all.map { candidate ->
                WrapCase(
                    geometry = candidate,
                    agenda = layoutOf(candidate, wrappedPanels, calendarDateOnly = false),
                    wrapped = layoutOf(candidate, wrappedPanels, calendarDateOnly = true),
                )
            }
        // How far the marker reserve reaches above the speed overlay where no cap
        // trims it: the most any agenda layout shows (a capped one shows less).
        val clearance = cases.maxOf { it.agenda.markerClearance }
        assertEquals(emptyList(), cases.flatMap { it.failures(clearance) })
    }

    private data class WrapCase(
        val geometry: DashboardGeometry,
        val agenda: DashboardLayout,
        val wrapped: DashboardLayout,
    ) {
        fun failures(clearance: Dp): List<String> {
            val id = geometry.id
            val portrait = geometry.heightDp > geometry.widthDp
            return listOfNotNull(
                "$id: card ${wrapped.calendar} did not shrink from ${agenda.calendar}".takeUnless {
                    wrapped.calendar.height < agenda.calendar.height
                },
                "$id: card ${wrapped.calendar} is under the tap-target floor".takeUnless {
                    wrapped.calendar.height >= FemtoDimens.MinTouchTarget
                },
                "$id: head offset ${wrapped.headOffset} differs from ${agenda.headOffset}".takeUnless {
                    wrapped.headOffset.near(agenda.headOffset)
                },
                "$id: landscape card top ${wrapped.calendar.top} moved from ${agenda.calendar.top}".takeUnless {
                    portrait || wrapped.calendar.top.near(agenda.calendar.top)
                },
                "$id: portrait band ${wrapped.cards} did not shrink from ${agenda.cards}".takeUnless {
                    !portrait || wrapped.cards.top > agenda.cards.top
                },
                "$id: speed-to-band gap ${wrapped.speedGap} differs from ${agenda.speedGap}".takeUnless {
                    !portrait || wrapped.speedGap.near(agenda.speedGap)
                },
                "$id: marker reserve ${wrapped.markerReserve} grew from ${agenda.markerReserve}".takeUnless {
                    wrapped.markerReserve <= agenda.markerReserve + Tolerance
                },
                // A reserve still sized for the uncut band reaches far past the
                // speed overlay, which follows the band down.
                "$id: marker reserve reaches ${wrapped.markerClearance} above the speed overlay, past $clearance"
                    .takeUnless { wrapped.markerClearance <= clearance + Tolerance },
            )
        }
    }

    private fun setDashboard() =
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
                        panels = panels,
                        glassConfig = GlassConfig(),
                        onAction = {},
                        modifier = Modifier.fillMaxSize(),
                        calendarDateOnly = dateOnly,
                        clock = DashboardFixtures.fixedClock,
                        mapSurface = { _, config ->
                            SideEffect { mapConfig = config }
                            Box(Modifier.fillMaxSize())
                        },
                    )
                }
            }
        }

    // The rendered bounds for one configuration. The calendar card is read
    // through its expand click (the fixture's snapshot is granted), which spans
    // the card; the head through its month line.
    private fun layoutOf(
        candidate: DashboardGeometry,
        candidatePanels: PanelVisibility,
        calendarDateOnly: Boolean,
    ): DashboardLayout {
        geometry = candidate
        panels = candidatePanels
        dateOnly = calendarDateOnly
        rule.waitForIdle()
        return DashboardLayout(
            screenHeight = candidate.heightDp.dp,
            calendar = rule.onNodeWithContentDescription(CALENDAR_EXPAND).getUnclippedBoundsInRoot(),
            head = rule.onNodeWithText(HeadMonth, useUnmergedTree = true).getUnclippedBoundsInRoot(),
            weather =
                if (candidatePanels.weather) {
                    rule.onNodeWithContentDescription(WEATHER_EXPAND).getUnclippedBoundsInRoot()
                } else {
                    null
                },
            speed = rule.onNodeWithTag(DashboardTags.SPEED, useUnmergedTree = true).getUnclippedBoundsInRoot(),
            cards = rule.onNodeWithTag(DashboardTags.CARDS, useUnmergedTree = true).getUnclippedBoundsInRoot(),
            bottomSafeFraction = checkNotNull(mapConfig).bottomSafeFraction,
        )
    }

    private data class DashboardLayout(
        val screenHeight: Dp,
        val calendar: DpRect,
        val head: DpRect,
        val weather: DpRect?,
        val speed: DpRect,
        val cards: DpRect,
        val bottomSafeFraction: Float,
    ) {
        val headOffset: Dp get() = head.top - calendar.top
        val speedGap: Dp get() = cards.top - speed.bottom
        val markerReserve: Dp get() = screenHeight * bottomSafeFraction

        // How far the reserve reaches above the speed overlay's top edge.
        val markerClearance: Dp get() = markerReserve - (screenHeight - speed.top)
    }

    private companion object {
        // English resources under Robolectric.
        const val CALENDAR_EXPAND = "Open full-screen calendar"
        const val WEATHER_EXPAND = "Open full-screen weather"

        // The head's month line: the fixture's label, uppercased as the head draws it.
        val HeadMonth = fakeCalendarSnapshot().monthLabel.uppercase()

        // Sub-dp tolerance absorbs the px rounding of a dp layout at mdpi.
        val Tolerance = 0.5.dp

        fun Dp.near(other: Dp): Boolean = abs(value - other.value) < Tolerance.value

        fun DpRect.near(other: DpRect): Boolean =
            left.near(other.left) && top.near(other.top) && right.near(other.right) && bottom.near(other.bottom)
    }
}
