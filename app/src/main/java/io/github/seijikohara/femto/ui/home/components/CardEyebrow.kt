package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.seijikohara.femto.ui.theme.eyebrowTight

/**
 * The uppercase eyebrow a dashboard card head opens on — the calendar's
 * "TODAY", the weather's condition (or its stale-data "AS OF 05:32") — so the
 * calendar and weather cards beside each other share one head line: the same
 * style, colour, and single-line box above each card's hero numeral. The
 * leading is tightened to the label's own size ([eyebrowTight]): the eyebrow
 * sits directly on the hero, and on the 5:3 head unit the four dp the default
 * leading would add to each head are what fits the calendar's next entry. The
 * music card's source eyebrow keeps its own row for the source icon it carries
 * beside the name.
 */
@Composable
internal fun CardEyebrow(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text = text.uppercase(),
    modifier = modifier,
    style = MaterialTheme.typography.eyebrowTight(),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
