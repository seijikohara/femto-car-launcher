package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The settings slider row is controlled: the stored setting owns the value, and
 * the slider's own state only mirrors it. Settings has no screenshot goldens, so
 * nothing else pins that the thumb follows a setting changed from outside the
 * row (a reset to defaults), that it never runs ahead of the store, and that a
 * drag keeps going while the store takes each step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class SliderRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var stored by mutableIntStateOf(3)
    private val reported = mutableListOf<Int>()

    private fun setRow(onValueChange: (Int) -> Unit = { reported += it }) =
        rule.setContent {
            FemtoTheme {
                SliderRow(
                    title = "Zoom",
                    valueLabel = stored.toString(),
                    value = stored,
                    range = Range,
                    onValueChange = onValueChange,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                )
            }
        }

    private fun slider() = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    private fun at(value: Int) =
        hasProgressBarRangeInfo(
            ProgressBarRangeInfo(current = value.toFloat(), range = Range.first.toFloat()..Range.last.toFloat()),
        )

    @Test
    fun `the thumb starts at the stored setting`() {
        setRow()

        slider().assert(at(3))
    }

    @Test
    fun `the thumb follows a setting changed from outside the row`() {
        setRow()

        stored = 5

        slider().assert(at(5))
    }

    @Test
    fun `a requested value is reported as the nearest whole step`() {
        setRow()

        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(4.4f) }

        assertEquals(listOf(4), reported)
    }

    @Test
    fun `the thumb stays at the stored setting until the store takes a requested value`() {
        setRow()

        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(4.4f) }

        slider().assert(at(3))
    }

    @Test
    fun `a drag across the track keeps going while the store takes each step`() {
        setRow(onValueChange = { stored = it })

        slider().performTouchInput { swipeRight() }

        assertEquals(Range.last, stored)
    }
}

private val Range = 1..5
