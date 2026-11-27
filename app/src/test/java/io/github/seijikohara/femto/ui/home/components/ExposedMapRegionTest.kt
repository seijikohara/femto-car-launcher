package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where [ExposedMapRegion] centres its content: in the middle of what the two side reserves and the bottom reserve
 * leave of the map, with a reserve on the right, on the left, and on both sides at once (the cards on one side and
 * the dock on the other), and no lower than the middle of the upper half where the bottom reserve passes the middle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1200dp-h700dp-mdpi")
class ExposedMapRegionTest {
    @get:Rule
    val rule = createComposeRule()

    private var mapConfig by mutableStateOf(MapConfig())

    @Test
    fun `the content sits in the middle of the map the reserves leave`() {
        rule.setContent {
            Box(modifier = Modifier.size(MAP_WIDTH.dp, MAP_HEIGHT.dp)) {
                ExposedMapRegion(mapConfig = mapConfig) {
                    Box(modifier = Modifier.size(40.dp).testTag(CONTENT))
                }
            }
        }
        val cases =
            listOf(
                // left reserve, right reserve, bottom reserve -> the content's centre
                Reserves(left = 0f, right = 0f, bottom = 0f) to (500f to 250f),
                Reserves(left = 0f, right = 0.4f, bottom = 0.2f) to (300f to 200f),
                Reserves(left = 0.4f, right = 0f, bottom = 0.2f) to (700f to 200f),
                Reserves(left = 0.1f, right = 0.4f, bottom = 0f) to (350f to 250f),
                Reserves(left = 0.4f, right = 0.1f, bottom = 0f) to (650f to 250f),
                // Bottom overlays past the middle still leave the content the upper half.
                Reserves(left = 0f, right = 0f, bottom = 0.8f) to (500f to 125f),
            )
        cases.forEach { (reserves, centre) ->
            mapConfig =
                MapConfig(
                    leftSafeFraction = reserves.left,
                    rightSafeFraction = reserves.right,
                    bottomSafeFraction = reserves.bottom,
                )
            rule.waitForIdle()
            val bounds = rule.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot()
            assertEquals("$reserves: centre x", centre.first, ((bounds.left + bounds.right) / 2).value, TOLERANCE_DP)
            assertEquals("$reserves: centre y", centre.second, ((bounds.top + bounds.bottom) / 2).value, TOLERANCE_DP)
        }
    }

    private data class Reserves(
        val left: Float,
        val right: Float,
        val bottom: Float,
    )

    private companion object {
        const val CONTENT = "exposed-content"
        const val MAP_WIDTH = 1000
        const val MAP_HEIGHT = 500
        const val TOLERANCE_DP = 1f
    }
}
