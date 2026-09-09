package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.theme.eyebrowTight

/**
 * The uppercase eyebrow a dashboard card head opens on — the calendar's
 * "TODAY", the weather's condition (or its stale-data "AS OF 05:32") — so the
 * calendar and weather cards beside each other share one head line: the same
 * style and the same single-line box above each card's hero numeral. The
 * leading is tightened to the label's own size ([eyebrowTight]): the eyebrow
 * sits directly on the hero, and on the 5:3 head unit the four dp the default
 * leading would add to each head are what fits the calendar's next entry. The
 * music card's source eyebrow keeps its own row for the source icon it carries
 * beside the name.
 *
 * A [pill] eyebrow is set in the primary tint on a faint primary wash — the
 * accent the agenda's today gutter carried before the today head replaced it.
 * The calendar's "TODAY" takes it, so the card keeps one mark that says "now"
 * at a glance (with no accent at all the agenda read as a list of equals),
 * while the weather's condition beside it stays a plain word. The wash is
 * drawn, not laid out: it reaches [PillOvershoot] past the label's line box
 * above and below (see [pillWash]), so the pill reads as a capsule while the
 * eyebrow keeps the 12 dp box the plain form has — the head is one height
 * either way, the two heroes below stay on one digit band, and the calendar's
 * next entry the head unit fits under a 12 dp eyebrow still fits.
 */
@Composable
internal fun CardEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    pill: Boolean = false,
) = Text(
    text = text.uppercase(),
    modifier =
        modifier.then(
            if (pill) {
                Modifier.pillWash(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = PILL_WASH_ALPHA),
                )
            } else {
                Modifier
            },
        ),
    style = MaterialTheme.typography.eyebrowTight(),
    color = if (pill) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

// The pill behind a label: [shape] in [color], as wide as the label plus its
// horizontal inset (layout padding, so the pill's edge — not the label — sits
// on the column's start, flush with the hero numeral below) and taller than the
// label's line box by PillOvershoot above and below. The overshoot is drawn
// rather than padded so the label's measured height stays the plain eyebrow's;
// two dp of the card's top inset above and two of the hero's line box below
// (headroom the digits never reach) carry it.
private fun Modifier.pillWash(
    shape: Shape,
    color: Color,
): Modifier =
    drawBehind {
        val overshoot = PillOvershoot.toPx()
        translate(top = -overshoot) {
            drawOutline(
                shape.createOutline(Size(size.width, size.height + overshoot * 2), layoutDirection, this),
                color,
            )
        }
    }.padding(horizontal = PillInset)

// The pill's wash over the glass, its inset beside the label, and how far the
// wash reaches past the label's line box above and below.
private const val PILL_WASH_ALPHA = 0.12f
private val PillInset = 6.dp
private val PillOvershoot = 2.dp
