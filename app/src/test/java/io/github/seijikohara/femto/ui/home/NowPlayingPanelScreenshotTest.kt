package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.QueueEntry
import io.github.seijikohara.femto.data.music.RepeatMode
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.home.components.NowPlayingPanel
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Now Playing panel goldens across the two orientations and the two capability
 * extremes. Same recording flow as DashboardScreenshotTest: goldens are
 * recorded on CI and committed from the artifact (macOS and Linux anti-alias
 * differently, so a local record would not match the CI runner).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class NowPlayingPanelScreenshotTest {
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun nowplaying_head_unit_full_capabilities() = capture("head-unit-853x512-full", FULL)

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun nowplaying_head_unit_no_capabilities() = capture("head-unit-853x512-bare", BARE)

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun nowplaying_phone_portrait_full_capabilities() = capture("phone-portrait-412x915-full", FULL)

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun nowplaying_phone_portrait_no_capabilities() = capture("phone-portrait-412x915-bare", BARE)

    private fun capture(
        name: String,
        state: NowPlaying,
    ) {
        captureRoboImage(filePath = "src/test/screenshots/nowplaying-$name.png", roborazziOptions = OPTIONS) {
            FemtoTheme {
                NowPlayingPanel(
                    nowPlaying = state,
                    onCommand = {},
                    onLaunchSource = {},
                    onClose = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private companion object {
        // A small tolerance absorbs sub-pixel antialiasing differences between the
        // golden-record host and the CI runner while still catching real layout
        // regressions (mirrors DashboardScreenshotTest).
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )

        val FULL =
            fakeNowPlaying(
                title = "An Extraordinarily Long Track Title That The Small Card Truncates",
                canSeek = true,
                canShuffle = true,
                canRepeat = true,
                canSkipToQueueItem = true,
                shuffleOn = true,
                repeatMode = RepeatMode.ALL,
                queue =
                    listOf(
                        QueueEntry(1L, "Next Track One", "The Wayfinders"),
                        QueueEntry(2L, "Next Track Two", null),
                        QueueEntry(3L, "A Third Upcoming Track With A Longer Name", "Night Routes"),
                    ),
            )

        val BARE = fakeNowPlaying()
    }
}
