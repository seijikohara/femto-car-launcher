package io.github.seijikohara.femto.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A modal sheet's dialog window is held in a `remember` keyed on the ambient
 * density, so anything that moves `LocalDensity` at the sheet's call site
 * destroys the open sheet's window and builds a new one. The font-size and
 * display-size settings both move it — they are applied through the density —
 * which made adjusting either one reset the open settings sheet mid-drag.
 *
 * [ModalSheetHost] pins the sheets to the platform density, the one their
 * content runs at inside the dialog anyway. These tests hold that contract:
 * what the host provides must not depend on the scale settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModalSheetHostTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // One setContent per test (the rule allows no more), with the settings driven
    // through state so a test can compare the host across a live change — which is
    // also the real scenario: the user drags a slider with the sheet already open.
    private class Harness {
        var fontBaseSizeSp by mutableIntStateOf(16)
        var uiScale by mutableStateOf(UiScale.MEDIUM)
        val hosted = mutableListOf<Density>()
        var outside: Density? = null
    }

    private fun harness(): Harness {
        val harness = Harness()
        composeTestRule.setContent {
            FemtoTheme(fontBaseSizeSp = harness.fontBaseSizeSp, uiScale = harness.uiScale) {
                harness.outside = LocalDensity.current
                ModalSheetHost { harness.hosted += LocalDensity.current }
            }
        }
        composeTestRule.waitForIdle()
        assertTrue(harness.hosted.isNotEmpty(), "ModalSheetHost did not compose its content")
        return harness
    }

    @Test
    fun `the hosted density survives a font size change`() {
        val harness = harness()

        harness.fontBaseSizeSp = 20
        composeTestRule.waitForIdle()

        // Equality is the contract: Material 3 keys the sheet's dialog on this
        // value, so an unequal instance rebuilds the window mid-gesture.
        assertEquals(1, harness.hosted.distinct().size, "the host handed the sheet a new density: ${harness.hosted}")
    }

    @Test
    fun `the hosted density survives a display size change`() {
        val harness = harness()

        harness.uiScale = UiScale.LARGE
        composeTestRule.waitForIdle()

        assertEquals(1, harness.hosted.distinct().size, "the host handed the sheet a new density: ${harness.hosted}")
    }

    @Test
    fun `the theme still scales outside the host`() {
        // Anti-vacuous guard: if FemtoTheme stopped overriding the density at all,
        // both assertions above would pass for the wrong reason.
        val harness = harness()

        harness.fontBaseSizeSp = 20
        harness.uiScale = UiScale.LARGE
        composeTestRule.waitForIdle()

        val scaled = assertNotNull(harness.outside)
        val platform = harness.hosted.first()
        assertTrue(scaled.density > platform.density, "UI scale did not reach the theme")
        assertTrue(scaled.fontScale > platform.fontScale, "font size did not reach the theme")
    }
}
