@file:OptIn(ExperimentalRoborazziApi::class)

package io.github.seijikohara.femto.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.seijikohara.femto.testfixtures.DashboardGeometries
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.testfixtures.SettingsForCapture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "Reset to defaults" checklist over the System category: on the reference head unit, where the category cells
 * share rows so every row shows, and on a phone in portrait. A dialog draws in a window of its own, which only the
 * whole-screen capture takes in, so the dialog is opened by a tap and captured with the screen behind it. Recorded on
 * CI like [SettingsScreenshotTest]'s goldens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class SettingsResetDialogScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_reset_dialog_head_unit() = capture("settings-reset-dialog-head-unit-853x512")

    @Test
    @Config(qualifiers = DashboardGeometries.PHONE_PORTRAIT_412X915)
    fun settings_reset_dialog_portrait() = capture("settings-reset-dialog-portrait-412x915")

    private fun capture(name: String) {
        rule.setContent { SettingsForCapture(initialCategory = SettingsCategoryId.SYSTEM) }
        // The row sits below the fold on the head unit.
        rule.onNodeWithText("Reset to defaults").performScrollTo().performClick()
        // Without the dialog the capture would record the screen alone; fail here instead.
        rule.onNodeWithText("Reset settings").assertIsDisplayed()
        captureScreenRoboImage(filePath = "src/test/screenshots/$name.png", roborazziOptions = ScreenshotCompareOptions)
    }
}
