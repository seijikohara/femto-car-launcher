package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The settings rows' interaction contract once they are segments: a short
 * choice is a row of connected toggle buttons that read as one radio group and
 * keep the automotive tap target, a longer choice still opens its dialog, and
 * a switch row stays a switch to accessibility services.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class SettingsRowsTest {
    @get:Rule
    val rule = createComposeRule()

    private val chosen = mutableListOf<String>()

    private fun setChoice(options: List<String>) =
        rule.setContent {
            FemtoTheme {
                ChoiceRow(
                    title = TITLE,
                    options = options.map { it to it },
                    selected = options.first(),
                    onSelect = { chosen += it },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                )
            }
        }

    @Test
    fun `each option of a short choice is a radio option, only the current one selected`() {
        setChoice(SHORT)

        SHORT.forEach { rule.onNodeWithText(it).assert(isRadioButton) }
        rule.onNodeWithText(SHORT[0]).assertIsSelected()
        rule.onNodeWithText(SHORT[1]).assertIsNotSelected()
        rule.onNodeWithText(SHORT[2]).assertIsNotSelected()
    }

    @Test
    fun `tapping another option of a short choice reports it`() {
        setChoice(SHORT)

        rule.onNodeWithText(SHORT[2]).performClick()

        assertEquals(listOf(SHORT[2]), chosen)
    }

    @Test
    fun `tapping the current option of a short choice changes nothing`() {
        setChoice(SHORT)

        rule.onNodeWithText(SHORT[0]).performClick()

        assertEquals(emptyList(), chosen)
    }

    @Test
    fun `every option of a short choice keeps the automotive tap target`() {
        setChoice(SHORT)

        SHORT.forEach { rule.onNodeWithText(it).assertHeightIsAtLeast(FemtoDimens.MinTouchTarget) }
    }

    @Test
    fun `an option label shared by two short choices belongs to its own row`() {
        rule.setContent {
            FemtoTheme {
                SettingsGroup {
                    listOf(TITLE, OTHER_TITLE).forEach { title ->
                        item { shapes ->
                            ChoiceRow(
                                title = title,
                                options = SHORT.map { it to it },
                                selected = SHORT[0],
                                onSelect = { chosen += "$title:$it" },
                                shapes = shapes,
                            )
                        }
                    }
                }
            }
        }

        rule.onNode(hasText(SHORT[1]) and hasParent(hasAnySibling(hasText(OTHER_TITLE)))).performClick()

        assertEquals(listOf("$OTHER_TITLE:${SHORT[1]}"), chosen)
    }

    @Test
    fun `a long choice shows only its current value until its dialog opens`() {
        setChoice(LONG)

        rule.onAllNodesWithText(LONG[1]).assertCountEquals(0)
        rule.onNodeWithText(TITLE).performClick()
        rule.onNodeWithText(LONG[1]).assert(isRadioButton).performClick()

        assertEquals(listOf(LONG[1]), chosen)
    }

    @Test
    fun `a switch row is one switch with its state`() {
        rule.setContent {
            FemtoTheme {
                SwitchRow(
                    title = TITLE,
                    checked = false,
                    onCheckedChange = { chosen += it.toString() },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                )
            }
        }

        rule
            .onNodeWithText(TITLE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .assertHeightIsAtLeast(FemtoDimens.MinTouchTarget)
            .performClick()

        assertEquals(listOf("true"), chosen)
    }

    private companion object {
        const val TITLE = "Choice"
        const val OTHER_TITLE = "Other choice"
        val SHORT = listOf("One", "Two", "Three")
        val LONG = listOf("One", "Two", "Three", "Four")
        val isRadioButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    }
}
