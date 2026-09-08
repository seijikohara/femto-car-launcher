package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * [singleLineBox] centres its content on the style's NOMINAL line box. When an
 * ancestor crushes the slot below that box (a card row at its floor), the ink
 * must keep the top it has in a free slot and clip at the bottom — never
 * re-centre in the sliver and spill upward over the line above.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class SingleLineBoxTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a crushed slot keeps the ink's top where a free slot puts it`() {
        val crushedSlot = 10.dp
        rule.setContent {
            FemtoTheme {
                val style = MaterialTheme.typography.heroNumeral()
                Column {
                    Box(modifier = Modifier.testTag("free")) {
                        Text("18", style = style, modifier = Modifier.singleLineBox(style).testTag("freeText"))
                    }
                    Box(modifier = Modifier.height(crushedSlot).testTag("crushed")) {
                        Text("18", style = style, modifier = Modifier.singleLineBox(style).testTag("crushedText"))
                    }
                }
            }
        }

        val freeOffset =
            rule.onNodeWithTag("freeText").getBoundsInRoot().top - rule.onNodeWithTag("free").getBoundsInRoot().top
        val crushedOffset =
            rule.onNodeWithTag("crushedText").getBoundsInRoot().top -
                rule.onNodeWithTag("crushed").getBoundsInRoot().top
        assertEquals(freeOffset, crushedOffset)
        assertEquals(crushedSlot, rule.onNodeWithTag("crushed").getBoundsInRoot().height)
    }
}
