package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.QueueEntry
import io.github.seijikohara.femto.data.music.RepeatMode
import io.github.seijikohara.femto.data.music.SPECTRUM_BAND_COUNT
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.ui.home.components.NowPlayingPanel
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

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

    // A short, wide bar-style head unit: the height that previously clipped the
    // shuffle / repeat toggles below the fold. Regression cover for the
    // landscape inline-toggle reflow that keeps the controls inside the panel.
    @Test
    @Config(qualifiers = "w1280dp-h360dp-mdpi")
    fun nowplaying_bar_full_capabilities() = capture("bar-1280x360-full", FULL)

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun nowplaying_head_unit_spectrum() = capture("head-unit-853x512-spectrum", FULL, mockSpectrum())

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun nowplaying_phone_portrait_spectrum() = capture("phone-portrait-412x915-spectrum", FULL, mockSpectrum())

    // Both meta toggles off: the minimal, metadata-only player (no album line,
    // no cover art) that the album/art visibility settings produce.
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun nowplaying_head_unit_minimal() = capture("head-unit-853x512-minimal", FULL, showAlbum = false, showArt = false)

    // Dark variant: the whole player is glass over the album art's own colours,
    // and every other panel family already has one — this was the last all-light
    // set in the suite.
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun nowplaying_head_unit_dark() = capture("head-unit-853x512-dark", FULL, darkTheme = true)

    private fun capture(
        name: String,
        state: NowPlaying,
        spectrum: StateFlow<FloatArray?>? = null,
        showAlbum: Boolean = true,
        showArt: Boolean = true,
        darkTheme: Boolean = false,
    ) {
        captureRoboImage(
            filePath = "src/test/screenshots/nowplaying-$name.png",
            roborazziOptions = ScreenshotCompareOptions,
        ) {
            FemtoTheme(darkTheme = darkTheme) {
                val panel: @Composable () -> Unit = {
                    NowPlayingPanel(
                        nowPlaying = state,
                        onCommand = {},
                        onLaunchSource = {},
                        onClose = {},
                        modifier = Modifier.fillMaxSize(),
                        spectrum = spectrum,
                        showAlbum = showAlbum,
                        showArt = showArt,
                    )
                }
                if (darkTheme) {
                    // The glass panel is translucent: a dark capture needs a theme
                    // backdrop behind it or it renders milky over Robolectric's white
                    // window. The light captures read fine without one, and adding it
                    // there would rewrite eight goldens for no UI change (same
                    // treatment as PanelScreenshotTest).
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { panel() }
                } else {
                    panel()
                }
            }
        }
    }

    private companion object {
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

        // A fixed band ramp standing in for a live capture, so the golden shows
        // the spectrum strip behind the transport controls.
        private fun mockSpectrum(): StateFlow<FloatArray?> =
            MutableStateFlow(FloatArray(SPECTRUM_BAND_COUNT) { band -> 0.2f + 0.7f * (0.5f + 0.5f * sin(band * 0.8f)) })
    }
}
