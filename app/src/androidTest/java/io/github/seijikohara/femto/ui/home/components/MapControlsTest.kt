package io.github.seijikohara.femto.ui.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class MapControlsTest {
    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Rail(
        showLocate: Boolean,
        onCompassTap: () -> Unit = {},
        onLocate: () -> Unit = {},
        onZoom: (Int) -> Unit = {},
    ) = FemtoTheme {
        MapControlRail(
            bearingDeg = { 42f },
            onCompassTap = onCompassTap,
            showLocate = showLocate,
            following = true,
            onLocate = onLocate,
            onZoomIn = { onZoom(1) },
            onZoomOut = { onZoom(-1) },
            hazeState = rememberHazeState(),
            glassConfig = GlassConfig(),
        )
    }

    @Test
    fun zoom_buttons_report_their_deltas() {
        val deltas = mutableListOf<Int>()
        rule.setContent { Rail(showLocate = true, onZoom = { deltas += it }) }
        rule.onNodeWithContentDescription("Zoom in").performClick()
        rule.onNodeWithContentDescription("Zoom out").performClick()
        assertEquals(listOf(1, -1), deltas)
    }

    @Test
    fun locate_button_dispatches_on_tap() {
        var located = 0
        rule.setContent { Rail(showLocate = true, onLocate = { located++ }) }
        rule.onNodeWithContentDescription("Return to current position").performClick()
        assertEquals(1, located)
    }

    @Test
    fun no_locate_button_without_a_fix() {
        rule.setContent { Rail(showLocate = false) }
        rule.onNodeWithContentDescription("Return to current position").assertDoesNotExist()
        rule.onNodeWithContentDescription("Zoom in").assertExists()
    }

    @Test
    fun compass_tap_invokes_the_orientation_toggle() {
        var toggled = 0
        rule.setContent { Rail(showLocate = true, onCompassTap = { toggled++ }) }
        rule.onNodeWithContentDescription("Toggle north-up orientation").performClick()
        assertEquals(1, toggled)
    }
}
