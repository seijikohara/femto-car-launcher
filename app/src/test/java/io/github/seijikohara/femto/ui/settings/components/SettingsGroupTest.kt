package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * A settings group numbers only the rows that show, so the first and last
 * showing rows always carry the group's outer corners however many rows around
 * them a setting hides.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsGroupTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `every row of a fully shown group gets its own place`() {
        assertEquals(
            listOf(SegmentPosition(0, 3), SegmentPosition(1, 3), SegmentPosition(2, 3)),
            segmentPositions(listOf(true, true, true)),
        )
    }

    @Test
    fun `hidden rows get no place and leave no gap in the numbering`() {
        assertEquals(
            listOf(null, SegmentPosition(0, 2), null, SegmentPosition(1, 2), null),
            segmentPositions(listOf(false, true, false, true, false)),
        )
    }

    @Test
    fun `a group with nothing showing has no places`() {
        assertEquals(listOf(null, null), segmentPositions(listOf(false, false)))
    }

    @Test
    fun `a hidden row is not composed and the rows around it take the group's ends`() {
        val shapes = mutableMapOf<String, Shape>()
        val expected = mutableMapOf<String, Shape>()
        rule.setContent {
            FemtoTheme {
                expected[FIRST] = ListItemDefaults.segmentedShapes(index = 0, count = 2).shape
                expected[LAST] = ListItemDefaults.segmentedShapes(index = 1, count = 2).shape
                SettingsGroup {
                    item { shapes[FIRST] = it.shape }
                    animatedItem(visible = false) { Text(HIDDEN) }
                    item { shapes[LAST] = it.shape }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(HIDDEN).assertDoesNotExist()
        assertEquals(expected, shapes)
    }

    private companion object {
        const val FIRST = "first"
        const val LAST = "last"
        const val HIDDEN = "Hidden row"
    }
}
