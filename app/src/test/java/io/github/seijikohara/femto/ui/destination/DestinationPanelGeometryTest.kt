package io.github.seijikohara.femto.ui.destination

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.DashboardGeometry
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * The dock's Navigation button opens the destination panel on every dashboard
 * geometry, and the panel's controls stay reachable at the automotive touch
 * floor from the 800x480 floor to the tall car portrait. The dashboard renders
 * in a box of the geometry's size inside a window large enough for the
 * biggest one, so one sandbox serves every parameter; `location = null` keeps
 * the map on its static fallback (as in PanelDismissTest).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w2000dp-h1920dp-mdpi")
internal class DestinationPanelGeometryTest(
    private val geometry: DashboardGeometry,
) {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<HomeAction>()

    private fun openPanel() {
        rule.setContent {
            FemtoTheme {
                Box(modifier = Modifier.requiredSize(geometry.widthDp.dp, geometry.heightDp.dp)) {
                    DashboardScaffold(
                        uiState = DashboardFixtures.state.copy(location = null),
                        is24Hour = true,
                        showClockSeconds = true,
                        speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                        mapConfig = MapConfig(),
                        panels = PanelVisibility(),
                        glassConfig = GlassConfig(),
                        onAction = { actions += it },
                        clock = DashboardFixtures.fixedClock,
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("Navigation").performClick()
    }

    @Test
    fun the_navigation_button_opens_the_panel_instead_of_the_maps_app() {
        openPanel()
        rule.onNodeWithText("DESTINATION").assertIsDisplayed()
        assertTrue(actions.none { it is HomeAction.OpenMaps })
    }

    @Test
    fun the_query_field_and_navigate_are_reachable_at_the_touch_floor() {
        openPanel()
        rule
            .onNodeWithTag(DESTINATION_QUERY_TEST_TAG)
            .scrolledIntoView()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(FemtoDimens.MinTouchTarget)
        rule
            .onNodeWithText("Navigate")
            .scrolledIntoView()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(FemtoDimens.MinTouchTarget)
    }

    @Test
    fun the_saved_places_section_shows() {
        openPanel()
        rule.onNodeWithText("SAVED PLACES").assertIsDisplayed()
    }

    // Only the landscape entry column scrolls (a short panel cannot fit it);
    // the portrait one sits at its full height, where performScrollTo has no
    // scrollable parent to drive.
    private fun SemanticsNodeInteraction.scrolledIntoView(): SemanticsNodeInteraction =
        apply {
            val scrollable =
                generateSequence(fetchSemanticsNode().parent) { it.parent }
                    .any { SemanticsActions.ScrollBy in it.config }
            if (scrollable) performScrollTo()
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun geometries(): List<Array<Any>> = DashboardGeometries.all.map { arrayOf<Any>(it) }
    }
}
