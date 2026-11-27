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
import io.github.seijikohara.femto.ui.home.components.TurnCardForm
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
 * destination, in light and dark: in full at its narrowest, and in the compact form a slot too narrow for the full
 * card gets. Over a theme backdrop standing in for the map, so the glass and its shadow read. The `voice` captures
 * add the speaker of the card's control for the spoken prompts, on and off. Recorded on CI like every golden.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w400dp-h360dp-xhdpi")
class TurnCardScreenshotTest {
    @Test
    fun turn_card_light() = capture("turn-card-light", darkTheme = false)

    @Test
    fun turn_card_dark() = capture("turn-card-dark", darkTheme = true)

    @Test
    fun turn_card_compact_light() = capture("turn-card-compact-light", darkTheme = false, compact = true)

    @Test
    fun turn_card_compact_dark() = capture("turn-card-compact-dark", darkTheme = true, compact = true)

    // The card with its control for the spoken prompts: the speaker sounding with the prompts on, crossed out with
    // them off, in each form.
    @Test
    fun turn_card_voice_on_light() = capture("turn-card-voice-on-light", darkTheme = false, voice = true)

    @Test
    fun turn_card_voice_on_dark() = capture("turn-card-voice-on-dark", darkTheme = true, voice = true)

    @Test
    fun turn_card_voice_off_light() = capture("turn-card-voice-off-light", darkTheme = false, voice = false)

    @Test
    fun turn_card_voice_off_dark() = capture("turn-card-voice-off-dark", darkTheme = true, voice = false)

    @Test
    fun turn_card_compact_voice_on_light() =
        capture("turn-card-compact-voice-on-light", darkTheme = false, compact = true, voice = true)

    @Test
    fun turn_card_compact_voice_on_dark() =
        capture("turn-card-compact-voice-on-dark", darkTheme = true, compact = true, voice = true)

    @Test
    fun turn_card_compact_voice_off_light() =
        capture("turn-card-compact-voice-off-light", darkTheme = false, compact = true, voice = false)

    @Test
    fun turn_card_compact_voice_off_dark() =
        capture("turn-card-compact-voice-off-dark", darkTheme = true, compact = true, voice = false)

    private fun capture(
        name: String,
        darkTheme: Boolean,
        compact: Boolean = false,
        voice: Boolean? = null,
    ) = captureRoboImage(filePath = "src/test/screenshots/$name.png", roborazziOptions = ScreenshotCompareOptions) {
        FemtoTheme(darkTheme = darkTheme) {
            RobolectricHaze { Cards(compact, voice) }
        }
    }

    // The full cards at their narrowest; the compact ones in a slot narrower than that, wide enough for the turn after
    // next.
    @Composable
    private fun Cards(
        compact: Boolean,
        voice: Boolean?,
    ) = Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        listOf(TurnCardFixtures.turn, TurnCardFixtures.turnThen, TurnCardFixtures.arrive).forEach { fixture ->
            val state = voice?.let { TurnCardFixtures.withVoice(fixture, it) } ?: fixture
            TurnCard(
                state = state,
                speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                form =
                    when {
                        !compact -> TurnCardForm.FULL
                        state.then != null -> TurnCardForm.COMPACT_THEN
                        else -> TurnCardForm.COMPACT
                    },
                modifier = Modifier.width(if (compact) CompactSlotWidth else TurnCardMinWidth),
            )
        }
    }
}

// A slot narrower than the full card's minimum, and wide enough for the compact card to keep the turn after next.
private val CompactSlotWidth = 280.dp
