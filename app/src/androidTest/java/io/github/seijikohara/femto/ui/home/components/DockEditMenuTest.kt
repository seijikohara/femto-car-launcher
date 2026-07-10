package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

// DockEditMenu is shared by the horizontal dock's nav buttons and the vertical
// rail's nav buttons / status indicators (DashboardDock, DockStatusCluster);
// these tests pin its orientation-aware move labels directly, without needing
// a long-press gesture to open it first.
class DockEditMenuTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun vertical_menu_shows_move_up_and_move_down_labels() {
        rule.setContent {
            FemtoTheme {
                DockEditMenu(
                    expanded = true,
                    onDismiss = {},
                    canMoveLeft = true,
                    canMoveRight = true,
                    canHide = true,
                    onMoveLeft = {},
                    onMoveRight = {},
                    onHide = {},
                    onResetDock = {},
                    vertical = true,
                )
            }
        }
        rule.onNodeWithText("Move up").assertIsDisplayed()
        rule.onNodeWithText("Move down").assertIsDisplayed()
        // The horizontal labels must not leak into the vertical rail's menu — a
        // left/right arrow and label would point the wrong way on a rail dock.
        rule.onNodeWithText("Move left").assertDoesNotExist()
        rule.onNodeWithText("Move right").assertDoesNotExist()
    }

    @Test
    fun horizontal_menu_keeps_move_left_and_move_right_labels() {
        rule.setContent {
            FemtoTheme {
                // vertical defaults to false: the horizontal dock bar's existing
                // behavior must stay byte-identical.
                DockEditMenu(
                    expanded = true,
                    onDismiss = {},
                    canMoveLeft = true,
                    canMoveRight = true,
                    canHide = true,
                    onMoveLeft = {},
                    onMoveRight = {},
                    onHide = {},
                    onResetDock = {},
                )
            }
        }
        rule.onNodeWithText("Move left").assertIsDisplayed()
        rule.onNodeWithText("Move right").assertIsDisplayed()
        rule.onNodeWithText("Move up").assertDoesNotExist()
        rule.onNodeWithText("Move down").assertDoesNotExist()
    }
}
