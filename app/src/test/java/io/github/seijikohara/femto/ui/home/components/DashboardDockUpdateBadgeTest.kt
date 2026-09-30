package io.github.seijikohara.femto.ui.home.components

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock's update dot reaches the Settings button, the way an accessibility
 * service reads it: the button's own label plus "Update available". The
 * dashboard goldens never show an update, so nothing else would notice the
 * dot going missing — or landing on another button.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class DashboardDockUpdateBadgeTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settingsLabel = context.getString(R.string.nav_settings)
    private val updateAvailableLabel = context.getString(R.string.update_available)

    private fun setDock(updateBadge: Boolean) {
        rule.setContent {
            FemtoTheme {
                DashboardDock(systemStatus = fakeSystemStatus(), onAction = {}, updateBadge = updateBadge)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the Settings button announces the update when badged`() {
        setDock(updateBadge = true)

        rule
            .onNode(hasContentDescription(settingsLabel) and hasContentDescription(updateAvailableLabel))
            .assertExists()
    }

    @Test
    fun `no button announces an update without the badge`() {
        setDock(updateBadge = false)

        rule.onNodeWithContentDescription(updateAvailableLabel).assertDoesNotExist()
    }
}
