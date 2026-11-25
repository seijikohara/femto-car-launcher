@file:OptIn(ExperimentalRoborazziApi::class)

package io.github.seijikohara.femto.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * The "Reset to defaults" checklist over the System category at the reference head-unit geometry, where its rows
 * scroll inside the dialog. A dialog draws in a window of its own, which only the whole-screen capture takes in, so
 * the dialog is opened by a tap and captured with the screen behind it. Recorded on CI like [SettingsScreenshotTest]'s
 * goldens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class SettingsResetDialogScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = DashboardGeometries.HEAD_UNIT_853X512)
    fun settings_reset_dialog_head_unit() {
        rule.setContent { SettingsForCapture(initialCategory = SettingsCategoryId.SYSTEM) }
        rule.onNodeWithText("Reset to defaults").performClick()
        captureScreenRoboImage(
            filePath = "src/test/screenshots/settings-reset-dialog-head-unit-853x512.png",
            roborazziOptions = ScreenshotCompareOptions,
        )
    }
}
