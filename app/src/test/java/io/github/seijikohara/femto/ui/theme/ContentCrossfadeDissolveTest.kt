package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins [Motion.ContentCrossfade]'s no-dip dissolve property, which no settled
 * frame can show: the stock `Crossfade` defect it guards against exists only
 * mid-transition, where both layers sit at partial alpha and the backdrop
 * bleeds through content that is opaque in both frames.
 *
 * Both states render the SAME opaque gray square over a red backdrop, so the
 * mid-transition golden must be pure gray — under the old `Crossfade` ~25% of
 * the red backdrop would tint it. Solid rectangles keep the render free of
 * font antialiasing, and the frozen test clock lands the capture on the same
 * frame every run, so the goldens are deterministic. The suite also guards the
 * settled frame (a child left compositing additively after the transition
 * would blow out against the backdrop) and the prune contract (the outgoing
 * state must leave the composition once the dissolve settles).
 *
 * Golden provenance: unlike the text-bearing screens (whose goldens must be
 * recorded on the CI OS — see DashboardScreenshotTest), these two goldens are
 * exempt from that rule and were recorded locally: solid rectangles carry no
 * font antialiasing, so every OS renders identical pixels. A CI record sweep
 * regenerating them is expected to be a no-op.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class ContentCrossfadeDissolveTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var state by mutableStateOf("first")

    private fun setDissolveContent() {
        composeTestRule.setContent {
            Box(modifier = Modifier.size(64.dp).background(Backdrop)) {
                Motion.ContentCrossfade(targetState = state, tier = MotionTier.STANDARD, label = "dissolve") { shown ->
                    Box(
                        modifier =
                            Modifier
                                .size(64.dp)
                                .background(SharedContent)
                                .testTag(shown),
                    )
                }
            }
        }
    }

    @Test
    fun `mid-transition keeps shared opaque content free of backdrop bleed`() {
        composeTestRule.mainClock.autoAdvance = false
        setDissolveContent()
        composeTestRule.mainClock.advanceTimeByFrame()

        // With the clock manual, nothing else flushes a test-thread snapshot
        // write to the recomposer; without the explicit notification the
        // transition never starts and the capture silently degrades into a
        // settled frame (gray for the wrong reason).
        state = "second"
        Snapshot.sendApplyNotifications()
        // One frame commits the recomposition and starts the transition; then
        // land mid-fade, where the old Crossfade let ~25% of the backdrop
        // through the shared pixels.
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(100)

        // Both layers must still be composed, or the capture silently degrades
        // into a second settled frame (pure gray is ALSO the settled result) —
        // e.g. if the STANDARD duration ever drops below the advance above.
        composeTestRule.onAllNodesWithTag("first").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("second").assertCountEquals(1)

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/dissolve-mid-transition.png",
            roborazziOptions = ScreenshotCompareOptions,
        )
    }

    @Test
    fun `settled content composites plainly with no additive residue`() {
        setDissolveContent()
        composeTestRule.waitForIdle()

        state = "second"
        composeTestRule.waitForIdle()

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/dissolve-settled.png",
            roborazziOptions = ScreenshotCompareOptions,
        )
    }

    @Test
    fun `settled dissolve prunes the outgoing content`() {
        setDissolveContent()
        composeTestRule.waitForIdle()

        state = "second"
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag("first").assertCountEquals(0)
        composeTestRule.onNodeWithTag("second").assertExists()
    }

    private companion object {
        val Backdrop = Color.Red
        val SharedContent = Color(0xFF808080)
    }
}
