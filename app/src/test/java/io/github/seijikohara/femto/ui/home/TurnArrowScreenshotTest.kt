package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.ui.home.components.TurnArrowGrid
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * The turn card's arrows (issue #389 phase 4): every glyph at the card's two sizes, 64 dp and 28 dp, with its kind's
 * name under it, in light and dark. At twice the density, so the goldens show the strokes' ends and joins. Recorded
 * on CI like every golden.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w880dp-h480dp-xhdpi")
class TurnArrowScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun turn_arrows_light() = capture("turn-arrows-light", darkTheme = false)

    @Test
    fun turn_arrows_dark() = capture("turn-arrows-dark", darkTheme = true)

    // Arrows show physical directions: the same grid under a right-to-left direction draws the same pixels. The grid
    // itself is laid out with absolute arrangements, so only a mirrored arrow could tell the two captures apart.
    @Test
    fun `the arrows do not mirror in a right-to-left layout`() {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                FemtoTheme { TurnArrowGrid() }
            }
        }
        val leftToRight = rule.onRoot().captureToImage().asAndroidBitmap()

        direction = LayoutDirection.Rtl
        rule.waitForIdle()
        val rightToLeft = rule.onRoot().captureToImage().asAndroidBitmap()

        assertTrue(leftToRight.sameAs(rightToLeft), "the grid draws differently right to left")
    }

    private fun capture(
        name: String,
        darkTheme: Boolean,
    ) = captureRoboImage(filePath = "src/test/screenshots/$name.png", roborazziOptions = ScreenshotCompareOptions) {
        FemtoTheme(darkTheme = darkTheme) {
            // The grid's own backdrop carried to the window's edges, so the dark capture has no white band.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) { TurnArrowGrid() }
        }
    }
}
