package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Fixed-width seconds patterns. Both use a leading-zero hour so the 12-hour
// form stays the same advance count as the 24-hour form; paired with tabular
// figures this keeps the overlay width constant as the time changes.
private val ClockFormatter24 = DateTimeFormatter.ofPattern("HH:mm:ss")
private val ClockFormatter12 = DateTimeFormatter.ofPattern("hh:mm:ss")

/**
 * Glass overlay anchored to the map pane's top-right corner.
 *
 * Time only — date / weekday / month are owned by [CalendarCard] in the
 * right pane. Background follows the mockup's `.glass-bg` token
 * (translucent surface container + 1 dp outline) so the overlay reads as
 * frosted glass over the map tiles below.
 *
 * The overlay self-times its seconds with a local [produceState] loop so the
 * per-second recomposition is scoped to this `Text` alone. The shared
 * minute-resolution `ClockTick` deliberately stays out of this path: a
 * per-second tick there would re-query the calendar every second.
 */
@Composable
internal fun ClockOverlay(
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
) {
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1000L - System.currentTimeMillis() % 1000) // align to the next second
        }
    }
    val formatter = if (is24Hour) ClockFormatter24 else ClockFormatter12
    val glassAlpha = if (isSystemInDarkTheme()) FemtoDimens.GlassBgAlphaDark else FemtoDimens.GlassBgAlphaLight
    Text(
        text = now.format(formatter),
        style =
            MaterialTheme.typography.displayMedium.copy(
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.04f).em,
                lineHeight = 40.sp,
                fontFeatureSettings = TabularFigures,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .clip(RoundedCornerShape(FemtoDimens.OverlayCorner))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = glassAlpha))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.GlassBorderAlpha),
                    shape = RoundedCornerShape(FemtoDimens.OverlayCorner),
                ).padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@PreviewLightDark
@Preview(name = "Clock overlay", widthDp = 240, heightDp = 80)
@Composable
private fun ClockOverlayPreview() {
    FemtoTheme {
        ClockOverlay()
    }
}
