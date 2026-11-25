package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.testfixtures.RobolectricHaze
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.testfixtures.TurnCardFixtures
import io.github.seijikohara.femto.ui.home.components.TurnCard
import io.github.seijikohara.femto.ui.home.components.TurnCardMinWidth
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The turn card in its three shapes (issue #389 phase 4), a turn, a turn with the turn after next, and the
 * destination, at its narrowest, in light and dark. Over a theme backdrop standing in for the map, so the glass and
 * its shadow read. Recorded on CI like every golden.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w400dp-h360dp-xhdpi")
class TurnCardScreenshotTest {
    @Test
    fun turn_card_light() = capture("turn-card-light", darkTheme = false)

    @Test
    fun turn_card_dark() = capture("turn-card-dark", darkTheme = true)

    private fun capture(
        name: String,
        darkTheme: Boolean,
    ) = captureRoboImage(filePath = "src/test/screenshots/$name.png", roborazziOptions = ScreenshotCompareOptions) {
        FemtoTheme(darkTheme = darkTheme) {
            RobolectricHaze { Cards() }
        }
    }

    @Composable
    private fun Cards() =
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(TurnCardFixtures.turn, TurnCardFixtures.turnThen, TurnCardFixtures.arrive).forEach { state ->
                TurnCard(
                    state = state,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    modifier = Modifier.width(TurnCardMinWidth),
                )
            }
        }
}
