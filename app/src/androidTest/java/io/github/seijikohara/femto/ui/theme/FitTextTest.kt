package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * FitText contract: the font shrinks within `minFontSize .. style.fontSize` to
 * fit the available width, and never drops below the floor — it ellipsizes at the
 * floor instead of shrinking past it. Uses [calendarWeekday] (20sp, floor 18sp)
 * as the exemplar style.
 */
class FitTextTest {
    @get:Rule
    val rule = createComposeRule()

    // Resolved on-screen font size (sp) FitText settled on for [text] in [widthDp].
    private fun resolvedSpFor(
        text: String,
        widthDp: Int,
    ): Float {
        var resolvedSp = -1f
        rule.setContent {
            FemtoTheme {
                Box(modifier = Modifier.width(widthDp.dp)) {
                    FitText(
                        text = text,
                        style = MaterialTheme.typography.calendarWeekday(),
                        onTextLayout = { resolvedSp = it.layoutInput.style.fontSize.value },
                    )
                }
            }
        }
        rule.waitForIdle()
        return resolvedSp
    }

    @Test
    fun keepsTheDesignSizeWhenItFits() {
        // A short weekday in an ample box renders at the full 20sp design size.
        assertTrue(resolvedSpFor("Mon", widthDp = 300) >= 20f)
    }

    @Test
    fun shrinksToFitButNeverBelowTheFloor() {
        val narrow = resolvedSpFor("Wednesday", widthDp = 70)
        assertTrue(narrow < 20f, "expected shrink below the 20sp design size, got ${narrow}sp")
        assertTrue(
            narrow >= FemtoDimens.MinBodyTextSize.value,
            "auto-size dropped below the ${FemtoDimens.MinBodyTextSize} floor: ${narrow}sp",
        )
    }
}
