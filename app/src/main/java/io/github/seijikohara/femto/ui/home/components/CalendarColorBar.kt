package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
 * the call site — the event rows size themselves with
 * `Modifier.height(IntrinsicSize.Min)` and pass `fillMaxHeight()`, so the
 * capsule spans exactly the title's rendered lines (both lines when a long
 * title wraps) instead of floating as a fixed-height stub beside them.
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
