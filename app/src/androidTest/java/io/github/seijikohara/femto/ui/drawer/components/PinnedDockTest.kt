package io.github.seijikohara.femto.ui.drawer.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.testfixtures.fakeAppEntry
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class PinnedDockTest {
    @get:Rule
    val rule = createComposeRule()

    private val maps = fakeAppEntry(packageName = "com.maps", className = ".Main", label = "Maps")
    private val music = fakeAppEntry(packageName = "com.music", className = ".Main", label = "Music")
    private val phone = fakeAppEntry(packageName = "com.phone", className = ".Main", label = "Phone")

    private fun setDock(onReorder: (List<String>) -> Unit) {
        rule.setContent {
            FemtoTheme {
                PinnedDock(
                    apps = listOf(maps, music, phone),
                    iconSize = DrawerIconSize.MEDIUM,
                    onLaunch = {},
                    onUnpin = {},
                    onReorder = onReorder,
                )
            }
        }
    }

    @Test
    fun move_right_commits_the_swapped_order() {
        var committed: List<String>? = null
        setDock(onReorder = { committed = it })
        // A still long-press opens the tile menu (the drag detector's no-travel
        // fallback), where Move right swaps with the next tile and commits.
        rule.onNodeWithContentDescription("Maps").performTouchInput { longClick() }
        rule.onNodeWithText("Move right").performClick()
        assertEquals(
            listOf("com.music/.Main", "com.maps/.Main", "com.phone/.Main"),
            committed,
        )
    }

    @Test
    fun move_left_commits_the_swapped_order() {
        var committed: List<String>? = null
        setDock(onReorder = { committed = it })
        rule.onNodeWithContentDescription("Phone").performTouchInput { longClick() }
        rule.onNodeWithText("Move left").performClick()
        assertEquals(
            listOf("com.maps/.Main", "com.phone/.Main", "com.music/.Main"),
            committed,
        )
    }

    @Test
    fun long_press_drag_right_commits_the_swapped_order() {
        var committed: List<String>? = null
        setDock(onReorder = { committed = it })
        // Synthesize the reorder gesture deterministically: press, hold past
        // the long-press timeout, travel right by more than half a slot
        // (96 dp tile + gutter), lift. The dock must commit the swap.
        rule.onNodeWithContentDescription("Maps").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) {
                moveBy(Offset(with(rule.density) { 14.dp.toPx() }, 0f))
                advanceEventTime(16)
            }
            up()
        }
        rule.waitForIdle()
        assertEquals(
            listOf("com.music/.Main", "com.maps/.Main", "com.phone/.Main"),
            committed,
        )
    }

    @Test
    fun the_first_tile_offers_no_move_left() {
        setDock(onReorder = {})
        rule.onNodeWithContentDescription("Maps").performTouchInput { longClick() }
        rule.onNodeWithText("Move right").assertExists()
        rule.onNodeWithText("Move left").assertDoesNotExist()
    }
}
