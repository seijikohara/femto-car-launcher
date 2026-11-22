package io.github.seijikohara.femto.ui.home.components

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Glass chrome registers its bounds as a keep-clear area on the host view, which
 * is how the system learns to move another app's picture-in-picture window off
 * the cards, the overlays and the dock (#390).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class GlassChromeKeepClearTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `glass chrome registers its bounds as a keep-clear area`() {
        lateinit var view: View
        rule.setContent {
            view = LocalView.current
            FemtoTheme {
                Box(
                    modifier =
                        Modifier
                            .offset(x = 40.dp, y = 30.dp)
                            .size(width = 200.dp, height = 100.dp)
                            .glassChrome(MaterialTheme.shapes.large, rememberHazeState(), GlassConfig()),
                )
            }
        }
        rule.waitForIdle()

        // mdpi: one dp is one pixel, so the rect is the box's own bounds.
        assertEquals(
            listOf(android.graphics.Rect(40, 30, 240, 130)),
            view.preferKeepClearRects,
        )
    }
}
