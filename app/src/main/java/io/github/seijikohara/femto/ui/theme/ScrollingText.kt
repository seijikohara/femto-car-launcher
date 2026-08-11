package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Scroll-to-reveal text — the SSOT for a line that outruns its slot and still has
 * to be readable in full: while [scrolling] it marquees end to end and keeps
 * going; at rest it wraps to [restingMaxLines] and ellipsizes.
 *
 * The companion to [FitText]. That one shrinks a label until it fits; this one
 * holds the design size and moves the text instead — the right choice where
 * shrinking would take the type below what a driver reads at a glance, which is
 * why the music title, artist and album lines use it.
 *
 * [scrolling] is the caller's motion gate, never a constant. A line that scrolls
 * forever is a distraction profile of its own
 * (`AGENTS.md#driving-lockout`), so callers run it while the vehicle is parked
 * and let it rest as a static ellipsis once it moves.
 *
 * **The scroll does not stop.** `basicMarquee` defaults to three passes and then
 * rests hard-clipped at offset 0 — a line cut off mid-word with no ellipsis to
 * explain it, which reads as a rendering bug rather than as "there is more text".
 * A driver who glances up after those three passes has missed them.
 */
@Composable
internal fun ScrollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    scrolling: Boolean = false,
    restingMaxLines: Int = 1,
) = Text(
    text = text,
    style = style,
    color = color,
    // A marquee is single-line by construction: it measures its content
    // unbounded and slides it through the slot.
    maxLines = if (scrolling) 1 else restingMaxLines,
    // Clip while scrolling — the scroll shows the whole string, so a trailing
    // ellipsis would claim text is missing when none is.
    overflow = if (scrolling) TextOverflow.Clip else TextOverflow.Ellipsis,
    modifier = if (scrolling) modifier.basicMarquee(iterations = Int.MAX_VALUE) else modifier,
)
