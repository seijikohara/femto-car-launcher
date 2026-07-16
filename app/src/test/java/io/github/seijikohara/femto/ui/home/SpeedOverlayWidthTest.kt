package io.github.seijikohara.femto.ui.home

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.ui.home.components.SpeedOverlay
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the speed overlay's width against its two historical reflow sources:
 * the hero numeral's digit count (8 -> 100 km/h) and the geocoded address
 * length. The digit test runs under a stressed user font setting (the heavier
 * weight step) deliberately — font settings scale text but not dp, which is
 * exactly how the old dp-based reserves came to breathe with the digit count
 * while looking fine at the defaults. The
 * window is head-unit sized so the overlay floats below its max-width cap;
 * on the default 320 dp test window every variant clamps to the window and
 * the assertions would be vacuous.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class SpeedOverlayWidthTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var speedMps by mutableStateOf(SINGLE_DIGIT_SPEED_MPS)
    private var address by mutableStateOf(SHORT_ADDRESS)

    private fun setOverlay(stressFontSettings: Boolean) {
        composeTestRule.setContent {
            FemtoTheme(
                fontBaseSizeSp = if (stressFontSettings) STRESS_BASE_SIZE_SP else DEFAULT_BASE_SIZE_SP,
                fontWeightStep = if (stressFontSettings) STRESS_WEIGHT_STEP else 0,
                fontLetterSpacingCentiEm = if (stressFontSettings) STRESS_LETTER_SPACING_CENTI_EM else 0,
            ) {
                Box(modifier = Modifier.testTag(OVERLAY_TAG)) {
                    // Fix timestamps are spaced far apart so the EMA settles on
                    // each new sample within one recomposition (dt >> tau).
                    val location =
                        remember(speedMps) {
                            Location("test").apply {
                                speed = speedMps
                                elapsedRealtimeNanos = (speedMps * 1e12).toLong()
                            }
                        }
                    SpeedOverlay(
                        location = location,
                        address = address,
                        tripState =
                            fakeTripState(
                                distanceMeters = 6_400.0,
                                avgSpeedMs = 11.0,
                                currentSpeedMs = speedMps.toDouble(),
                            ),
                        speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                        onReset = {},
                    )
                }
            }
        }
    }

    // Falsifiability guard: at the overlay's max-width cap every variant clamps
    // to the same value and both tests would pass vacuously even with the fix
    // reverted, so a measurement AT the cap means the stress no longer
    // discriminates and must fail loudly instead.
    private fun overlayWidth(): Dp =
        composeTestRule
            .onNodeWithTag(OVERLAY_TAG)
            .getUnclippedBoundsInRoot()
            .width
            .also { assertTrue(it < FemtoDimens.SpeedOverlayMaxWidth, "overlay clamped at its max-width cap ($it)") }

    @Test
    fun `hero digit count does not change the overlay width`() {
        setOverlay(stressFontSettings = true)
        composeTestRule.waitForIdle()
        // Anti-vacuous guards: the asserted widths must come from the two
        // different renders, not from a state change that never landed.
        composeTestRule.onNodeWithText("8").assertExists()
        val singleDigitWidth = overlayWidth()

        speedMps = THREE_DIGIT_SPEED_MPS
        Snapshot.sendApplyNotifications()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("100").assertExists()
        assertEquals(singleDigitWidth.value, overlayWidth().value, WIDTH_TOLERANCE_DP)
    }

    @Test
    fun `address length does not change the overlay width`() {
        setOverlay(stressFontSettings = false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(SHORT_ADDRESS.displayString()).assertExists()
        val shortAddressWidth = overlayWidth()

        address = LONG_ADDRESS
        Snapshot.sendApplyNotifications()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(LONG_ADDRESS.displayString()).assertExists()
        assertEquals(shortAddressWidth.value, overlayWidth().value, WIDTH_TOLERANCE_DP)
    }

    private companion object {
        const val OVERLAY_TAG = "speedOverlay"

        // A user font setting that grows text without moving dp — the exact
        // de-calibration class that broke the old dp reserves. Bounded to the
        // weight step alone: on the 853 dp reference fixture the overlay sits
        // ~14 dp under its max-width cap at defaults, so the full stress corner
        // (20 sp base + 0.10 em spacing) saturates the cap and trips the
        // falsifiability guard in overlayWidth(). The reserve MECHANISM's
        // falsification does not depend on the stress level (neutering the
        // sample fails this test at any setting).
        const val DEFAULT_BASE_SIZE_SP = 16
        const val STRESS_BASE_SIZE_SP = 16
        const val STRESS_WEIGHT_STEP = 2
        const val STRESS_LETTER_SPACING_CENTI_EM = 0

        // 8 km/h and 100 km/h — a 1-digit vs 3-digit hero under KILOMETERS_PER_HOUR.
        const val SINGLE_DIGIT_SPEED_MPS = 2.2222f
        const val THREE_DIGIT_SPEED_MPS = 27.7778f

        // Sub-pixel rounding headroom; a digit-count reflow moves the panel by
        // whole glyph advances (an order of magnitude more).
        const val WIDTH_TOLERANCE_DP = 0.5f

        val SHORT_ADDRESS = fakeAddress(locality = "Minato-ku")
        val LONG_ADDRESS =
            fakeAddress(
                locality = "Minato-ku",
                line = "1-2-3 Some Extraordinarily Long Avenue Name, A Very Long District Name, Prefecture Name",
            )
    }
}
