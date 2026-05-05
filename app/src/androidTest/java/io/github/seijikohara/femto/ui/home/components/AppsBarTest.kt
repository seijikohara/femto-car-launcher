package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AppsBarTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_six_tiles_and_dispatches_drawer() {
        var drawerTaps = 0
        rule.setContent {
            FemtoTheme {
                AppsBar(
                    onOpenDrawer = { drawerTaps++ },
                    onShortcut = {},
                )
            }
        }
        rule.onAllNodesWithContentDescription("Apps shortcut", substring = true).assertCountEquals(5)
        rule.onNodeWithContentDescription("Open all apps").performClick()
        assertEquals(1, drawerTaps)
    }
}
