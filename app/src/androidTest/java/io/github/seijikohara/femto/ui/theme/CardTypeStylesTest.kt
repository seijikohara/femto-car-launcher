package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pin the script-independence of the card text slots: a one-line Text must
 * occupy the same LAYOUT height (what the parent measures — the thing that
 * decides whether a vertically-centred block shifts) whichever script, and
 * therefore font face, renders it. The style alone cannot guarantee this:
 * Android's fallback line spacing grows a line rendered through a fallback
 * face (CJK over a Latin primary) past any LineHeightStyle — measured
 * on-device — so the [singleLineBox] slot clamp is the mechanism under
 * test, applied exactly as the music card's MetaLine applies it.
 */
class CardTypeStylesTest {
    @get:Rule
    val rule = createComposeRule()

    private fun layoutHeightsFor(useTitleStyle: Boolean): Pair<Int, Int> {
        var latinHeight = -1
        var cjkHeight = -1
        rule.setContent {
            FemtoTheme {
                val style =
                    if (useTitleStyle) {
                        MaterialTheme.typography.cardTitle()
                    } else {
                        MaterialTheme.typography.cardMeta()
                    }
                Column {
                    Text(
                        "Strobe",
                        style = style,
                        maxLines = 1,
                        // onSizeChanged sits FIRST so it reports the slot's
                        // outer (parent-visible) size, after the clamp.
                        modifier = Modifier.onSizeChanged { latinHeight = it.height }.singleLineBox(style),
                    )
                    Text(
                        "ストロボ・夜想曲",
                        style = style,
                        maxLines = 1,
                        modifier = Modifier.onSizeChanged { cjkHeight = it.height }.singleLineBox(style),
                    )
                }
            }
        }
        rule.waitForIdle()
        assertTrue(latinHeight > 0 && cjkHeight > 0, "both texts must have been measured")
        return latinHeight to cjkHeight
    }

    @Test
    fun card_title_line_box_is_script_independent() {
        val (latin, cjk) = layoutHeightsFor(useTitleStyle = true)
        assertEquals(latin, cjk)
    }

    @Test
    fun card_meta_line_box_is_script_independent() {
        val (latin, cjk) = layoutHeightsFor(useTitleStyle = false)
        assertEquals(latin, cjk)
    }
}
