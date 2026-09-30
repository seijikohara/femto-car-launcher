package io.github.seijikohara.femto.ui.settings.components

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The update dot on the Settings category list reaches the badged row, read
 * the way an accessibility service reads it: "Select Updates" followed by
 * "Update available". Settings has no screenshot goldens, so nothing else
 * would notice the dot going missing, or landing on another row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsCategoryListTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val selectUpdates =
        context.getString(R.string.settings_category_select, context.getString(R.string.settings_section_updates))
    private val updateAvailable = context.getString(R.string.update_available)

    private fun setList(badged: Set<SettingsCategoryId>) {
        rule.setContent {
            FemtoTheme {
                SettingsCategoryList(selectedId = SettingsCategoryId.APPEARANCE, onSelect = {}, badged = badged)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a badged Updates row announces the update after its own label`() {
        setList(badged = setOf(SettingsCategoryId.UPDATES))

        rule.onNode(hasContentDescription(selectUpdates) and hasContentDescription(updateAvailable)).assertExists()
    }

    @Test
    fun `no row announces an update without the badge`() {
        setList(badged = emptySet())

        rule.onNodeWithContentDescription(updateAvailable).assertDoesNotExist()
    }
}
