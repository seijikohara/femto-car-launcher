package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.testfixtures.FixedDashboardClock
import io.github.seijikohara.femto.testfixtures.fakeHomeUiState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The card cluster's vertical arbitration on the 5:3 head unit: at the default
 * scale the column affords the header, the calendar / weather row and the full
 * music card; at the LARGE scale no form of the music card leaves a readable
 * row, so the row yields whole — absent from the tree, not crushed into a
 * sliver where the weather numeral used to spill over its eyebrow — and the
 * music card keeps its full form.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class DashboardCardClusterTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setDashboard(uiScale: UiScale) =
        rule.setContent {
            FemtoTheme(uiScale = uiScale) {
                DashboardScaffold(
                    uiState = fakeHomeUiState(),
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                    clock = FixedDashboardClock,
                    // No WebView under Robolectric: an empty map surface.
                    mapSurface = {},
                )
            }
        }

    @Test
    fun `default scale shows the row beside the full music card`() {
        setDashboard(UiScale.MEDIUM)

        rule.onNodeWithText("TODAY").assertIsDisplayed()
        rule.onNodeWithText("Team standup").assertIsDisplayed()
        // The full form's album line.
        rule.onNodeWithText("For Lack of a Better Name").assertIsDisplayed()
    }

    @Test
    fun `large scale yields the row whole and keeps the full music card`() {
        setDashboard(UiScale.LARGE)

        rule.onNodeWithText("TODAY").assertDoesNotExist()
        rule.onNodeWithText("Team standup").assertDoesNotExist()
        rule.onNodeWithText("Strobe").assertIsDisplayed()
        rule.onNodeWithText("For Lack of a Better Name").assertIsDisplayed()
    }
}
