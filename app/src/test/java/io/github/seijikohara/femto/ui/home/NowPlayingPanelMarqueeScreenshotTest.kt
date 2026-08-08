package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.testfixtures.fakeOverflowingNowPlaying
import io.github.seijikohara.femto.ui.home.components.NowPlayingPanel
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The full-screen player's scroll-vs-ellipsis gate, in both orientations.
 *
 * Parked, the title, artist and album scroll to full length; moving, they rest
 * as a static ellipsis. Portrait shows the second half of the gate as well: at
 * rest the title wraps to two lines, and the scroll collapses it to one.
 *
 * A separate class from [NowPlayingPanelScreenshotTest] on purpose. A parked
 * panel scrolls forever, so these captures need the rule's paused clock — which
 * also pins the frame at the scroll's start offset and makes the golden
 * reproducible — and a `createComposeRule` in the class gives every capture in it
 * an opaque window. The eight goldens next door keep their transparent corners
 * (the composable-content form) rather than being rewritten for a harness change.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class NowPlayingPanelMarqueeScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun panel_parked_scrolls() = capture("head-unit-853x512-parked", stationary = true)

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun panel_moving_ellipsizes() = capture("head-unit-853x512-moving", stationary = false)

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun panel_portrait_parked_scrolls() = capture("phone-portrait-412x915-parked", stationary = true)

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun panel_portrait_moving_ellipsizes() = capture("phone-portrait-412x915-moving", stationary = false)

    private fun capture(
        name: String,
        stationary: Boolean,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = fakeOverflowingNowPlaying(),
                    onCommand = {},
                    onLaunchSource = {},
                    onClose = {},
                    modifier = Modifier.fillMaxSize(),
                    stationary = stationary,
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/nowplaying-marquee-$name.png",
            roborazziOptions = ScreenshotCompareOptions,
        )
    }
}
