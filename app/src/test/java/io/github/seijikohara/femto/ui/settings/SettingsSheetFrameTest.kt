package io.github.seijikohara.femto.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How far the Settings sheet opens on the head-unit geometry. Opened by the
 * dashboard's update prompt, the sheet shows its bottom edge, where the
 * Updates pane's "Update to" row and its progress sit; opened from the dock,
 * the sheet keeps its partly expanded start. Two rows stand in for the
 * content: one at its top and one at its bottom.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class SettingsSheetFrameTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `an opening to start an update shows the bottom of the sheet`() {
        setSheet(openExpanded = true)

        rule.onNodeWithTag(BOTTOM_ROW).assertIsDisplayed()
    }

    @Test
    fun `the dock's opening shows the top of the sheet and keeps its bottom below the fold`() {
        setSheet(openExpanded = false)

        rule.onNodeWithTag(TOP_ROW).assertIsDisplayed()
        rule.onNodeWithTag(BOTTOM_ROW).assertIsNotDisplayed()
    }

    private fun setSheet(openExpanded: Boolean) =
        rule.setContent {
            FemtoTheme {
                SettingsSheetFrame(onDismiss = {}, fullscreen = false, openExpanded = openExpanded) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.align(Alignment.TopCenter).row(TOP_ROW))
                        Box(modifier = Modifier.align(Alignment.BottomCenter).row(BOTTOM_ROW))
                    }
                }
            }
        }

    private fun Modifier.row(tag: String) =
        fillMaxWidth()
            .height(FemtoDimens.MinTouchTarget)
            .testTag(tag)

    private companion object {
        const val TOP_ROW = "top row"
        const val BOTTOM_ROW = "bottom row"
    }
}
