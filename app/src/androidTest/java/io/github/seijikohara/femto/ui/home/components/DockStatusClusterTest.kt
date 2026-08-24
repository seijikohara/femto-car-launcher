package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

class DockStatusClusterTest {
    @get:Rule
    val rule = createComposeRule()

    // Before this fix, the long press lived only in a raw pointerInput
    // (detectTapGestures), so no semantics node exposed OnLongClick at all and
    // TalkBack could never reach the reorder/hide menu. The lookup goes through
    // the indicator's contentDescription in the MERGED tree on purpose: it
    // asserts the label and the action share ONE node (mergeDescendants), the
    // shape TalkBack actually presents — an action on an unlabeled wrapper with
    // the description on a separate child node would fail this lookup.
    @Test
    fun long_click_semantics_action_opens_the_edit_menu_on_a_status_indicator() {
        rule.setContent {
            FemtoTheme {
                StatusCluster(
                    status = fakeSystemStatus(wifiConnected = true),
                    vertical = false,
                    order = listOf(DockStatusId.WIFI),
                    onAction = {},
                )
            }
        }
        rule
            .onNodeWithContentDescription("Wi-Fi connected")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        // Reset dock has no canMoveLeft/canMoveRight guard, so it always
        // renders once expanded — proof the menu actually opened.
        rule.onNodeWithText("Reset dock").assertIsDisplayed()
    }
}
