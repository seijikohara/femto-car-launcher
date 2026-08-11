package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import io.github.seijikohara.femto.testfixtures.fakeHomeUiState
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * The weather card's PRECIP / WIND readings must keep their unit at every
 * geometry the launcher ships on.
 *
 * Three metrics split the narrow card evenly — about 41 dp per cell on the
 * 853x512 reference — and a value laid out beside its unit in a `Row` is
 * measured first, leaving the unit only what is left over and clipping the rest
 * away. On a real forecast that printed "12.4 mm" as "12.4 m", a plausible-looking
 * wrong unit rather than visibly broken text. These cases pin the single-line
 * reading that replaced it.
 *
 * Same Robolectric harness as `DashboardScreenshotTest`, and the full dashboard
 * rather than the card alone on purpose: the cell width under test comes from the
 * real grid, so a card rendered at an invented width would prove nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class WeatherMetricFitTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun a_two_digit_amount_keeps_its_millimetres() = assertReadsInFull("12.4 mm", mm = 12.4)

    @Test
    @Config(qualifiers = "w800dp-h480dp-mdpi")
    fun the_narrowest_head_unit_keeps_its_millimetres() = assertReadsInFull("12.4 mm", mm = 12.4)

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun an_imperial_amount_keeps_its_inches() =
        assertReadsInFull("0.49 in", mm = 12.4, speedUnit = SpeedUnit.MILES_PER_HOUR)

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun a_raised_font_size_keeps_the_unit() = assertReadsInFull("12.4 mm", mm = 12.4, fontBaseSizeSp = 20)

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun the_wind_reading_keeps_its_unit() {
        // The other slot that carries a worded unit, and the one that has to stay
        // intact while the precipitation fix moves the shared Metric composable.
        render(mm = 12.4)

        assertNotClipped("3 m/s")
    }

    private fun assertReadsInFull(
        reading: String,
        mm: Double,
        speedUnit: SpeedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
        fontBaseSizeSp: Int = 16,
    ) {
        render(mm = mm, speedUnit = speedUnit, fontBaseSizeSp = fontBaseSizeSp)

        assertNotClipped(reading)
    }

    private fun assertNotClipped(reading: String) {
        // A miss here is the regression itself: the value and unit rendered as two
        // nodes, which is the shape that clips the unit out of the leftover width.
        val node =
            rule
                .onNodeWithText(reading, useUnmergedTree = true)
                .fetchSemanticsNode("\"$reading\" did not render as one reading")
        val layouts = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(layouts)
        val layout = assertNotNull(layouts.firstOrNull(), "\"$reading\" exposed no text layout")

        // Auto-size shrinks the reading to fit; overflow means even the floor was
        // too wide, so the tail — the unit — is cut off or ellipsized. Reported as
        // a width because `layoutInput` carries the style auto-size started from,
        // not the size it settled on.
        assertFalse(layout.hasVisualOverflow, "\"$reading\" is clipped inside ${node.size.width}px")
    }

    private fun render(
        mm: Double,
        speedUnit: SpeedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
        fontBaseSizeSp: Int = 16,
    ) {
        rule.setContent {
            FemtoTheme(fontBaseSizeSp = fontBaseSizeSp) {
                DashboardScaffold(
                    // No published probability: MET carries one only inside its Nordic
                    // domain, and the amount is what the rest of the world reads.
                    uiState =
                        fakeHomeUiState(
                            weather =
                                fakeWeatherSnapshot(
                                    precipitationProbabilityPercent = null,
                                    precipitationMm = mm,
                                ),
                        ),
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = speedUnit,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                    // Nothing behind the glass: the card geometry under test is
                    // unaffected by the map, and an empty surface keeps Robolectric
                    // away from the WebView shadow.
                    mapSurface = {},
                )
            }
        }
    }
}
