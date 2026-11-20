package io.github.seijikohara.femto.ui.settings.components

import android.content.Context
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The update dot on the Settings category list and rail reaches the badged
 * entry, read the way an accessibility service reads it: "Select Updates"
 * followed by "Update available". The screenshot goldens cannot tell the dot
 * is announced, or on which entry. The rail items also keep the automotive tap
 * target and stay reachable on a head-unit-height screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
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

    @Test
    fun `a badged Updates rail item announces the update after its own label`() {
        setRail(badged = setOf(SettingsCategoryId.UPDATES))

        rule.onNode(hasContentDescription(selectUpdates) and hasContentDescription(updateAvailable)).assertExists()
    }

    @Test
    fun `every rail item keeps the automotive tap target and selects its category`() {
        val selected = mutableListOf<SettingsCategoryId>()
        setRail(badged = emptySet(), onSelect = { selected += it })

        SettingsCategoryId.entries.forEach { id ->
            rule
                .onNodeWithContentDescription(selectDescription(id))
                .performScrollTo()
                .assertHeightIsAtLeast(FemtoDimens.MinTouchTarget)
                .performClick()
        }

        assertEquals(SettingsCategoryId.entries.toList(), selected)
    }

    private fun setRail(
        badged: Set<SettingsCategoryId>,
        onSelect: (SettingsCategoryId) -> Unit = {},
    ) {
        rule.setContent {
            FemtoTheme {
                SettingsCategoryRail(selectedId = SettingsCategoryId.APPEARANCE, onSelect = onSelect, badged = badged)
            }
        }
        rule.waitForIdle()
    }

    private fun selectDescription(id: SettingsCategoryId) =
        context.getString(R.string.settings_category_select, context.getString(id.titleRes))
}
