package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * Thin rounded vertical bar painted in a calendar's own color, used on the
 * calendar card and panel to mark which calendar an event belongs to.
 * `CircleShape` over the narrow box renders as a capsule with fully round
 * caps at any height.
 *
 * The bar owns only its [FemtoDimens.CalendarBarWidth]; the height comes from
 * the row it leads ([CalendarColorBarRow]), sized to its content with
 * `Modifier.height(IntrinsicSize.Min)`, so the capsule spans exactly the
 * title's rendered lines (both lines when a long title wraps) — or the today
 * head's time and title — instead of floating as a fixed-height stub.
 *
 * The bar deliberately uses the raw provider color rather than a
 * `MaterialTheme` role: the whole point is to match the exact colors the user
 * assigned in their calendar app, so this is a sanctioned hardcoded-color case
 * (like the curated weather-glyph palette in `WeatherGlyphColors.kt`), not a
 * theme accent. [color] is an opaque ARGB int (the repository forces the alpha
 * byte; see `EventItem.color`).
 */
@Composable
internal fun CalendarColorBar(
    color: Int,
    modifier: Modifier = Modifier,
) = Box(
    modifier
        .width(FemtoDimens.CalendarBarWidth)
        .clip(CircleShape)
        .background(Color(color)),
)

/**
 * A row the [CalendarColorBar] leads when [showColorBar] — the event rows of
 * the card and the panel, and the card's today head. `IntrinsicSize.Min` sizes
 * the row to its [content], so the bar's `fillMaxHeight` spans exactly the
 * rendered lines — one line for a short title, both when it wraps, the head's
 * time and title together — instead of floating as a fixed-height stub beside
 * them. Without the bar the content starts at the row's edge; the gap is the
 * bar's [FemtoDimens.CalendarBarGap].
 */
@Composable
internal fun CalendarColorBarRow(
    showColorBar: Boolean,
    color: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) = Row(
    modifier = modifier.height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CalendarBarGap),
) {
    if (showColorBar) {
        CalendarColorBar(color = color, modifier = Modifier.fillMaxHeight())
    }
    content()
}
