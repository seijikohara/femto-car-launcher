package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.testfixtures.fakeOverflowingNowPlaying
import io.github.seijikohara.femto.ui.home.components.MusicCard
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Locks the compact music card's long-title behaviour: title / artist / album
 * scroll to full length while the vehicle is stationary, and stay a static
 * ellipsis while moving. A narrow width forces the long strings to overflow the
 * meta column so the two branches actually differ.
 *
 * Captured through a paused clock (see [capture]) because the scroll never ends.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class MusicCardScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val longTrack = fakeOverflowingNowPlaying()

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun music_card_long_title_parked_marquees() =
        capture("music-card-long-title-parked") {
            MusicCard(
                state = MusicCardState.Playing(longTrack),
                onCommand = {},
                onConnect = {},
                onLaunchSource = {},
                onExpand = {},
                onPlay = {},
                stationary = true,
                modifier = Modifier.width(320.dp),
            )
        }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun music_card_long_title_moving_ellipsizes() =
        capture("music-card-long-title-moving") {
            MusicCard(
                state = MusicCardState.Playing(longTrack),
                onCommand = {},
                onConnect = {},
                onLaunchSource = {},
                onExpand = {},
                onPlay = {},
                stationary = false,
                modifier = Modifier.width(320.dp),
            )
        }

    // The card is a translucent glass sheet with a drop shadow; in the real
    // dashboard it floats over the map. Render it over a theme backdrop with an
    // outer margin so the shadow reads as a soft float (not a hard band clipped
    // at the image edge) and the card's own padding is visible against the
    // frosted fill — otherwise the isolated card on white looks paddingless.
    //
    // The clock is paused before the content composes. A parked card's marquee
    // runs forever (ScrollingText), so the composable-content form of
    // captureRoboImage — which waits for the composition to go idle — never
    // returns; a paused clock also pins the frame at the scroll's start offset,
    // which is what makes the golden reproducible.
    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FemtoTheme {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(24.dp),
                ) { content() }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = ScreenshotCompareOptions,
        )
    }
}
