package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class RouteChoiceCardTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setCard() {
        val choice = checkNotNull(DashboardFixtures.routeChoiceState.activeRoute?.choice)
        rule.setContent {
            FemtoTheme {
                RouteChoiceCard(choice = choice, speedUnit = SpeedUnit.KILOMETERS_PER_HOUR, onSelect = {}, onClose = {})
            }
        }
    }

    // "Done" alone is narrower than the floor; the button must not be.
    @Test
    fun `Done is a full tap target both ways`() {
        setCard()

        val done = rule.onNodeWithText("Done", useUnmergedTree = false).getUnclippedBoundsInRoot()

        assertTrue(
            done.width >= FemtoDimens.MinTouchTarget && done.height >= FemtoDimens.MinTouchTarget,
            "Done measures $done",
        )
    }
}
