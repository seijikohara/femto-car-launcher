package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.normalWeight
import io.github.seijikohara.femto.ui.theme.singleLineBox
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Fixed-width patterns. Every form uses a leading-zero hour so the 12-hour form
// stays the same advance count as the 24-hour form; paired with tabular figures
// this keeps the overlay width constant as the time changes. The NoSeconds forms
// back the "show seconds = off" setting.
private val ClockFormatter24 = DateTimeFormatter.ofPattern("HH:mm:ss")
private val ClockFormatter12 = DateTimeFormatter.ofPattern("hh:mm:ss")
private val ClockFormatter24NoSeconds = DateTimeFormatter.ofPattern("HH:mm")
private val ClockFormatter12NoSeconds = DateTimeFormatter.ofPattern("hh:mm")

/**
 * Glass overlay anchored to the map pane's top-right corner.
 *
 * Time only — date / weekday / month are owned by [CalendarCard] in the
 * right pane. Background follows the mockup's `.glass-bg` token
 * (translucent surface container + 1 dp outline) so the overlay reads as
 * frosted glass over the map tiles below.
 *
 * The overlay self-times with a local [produceState] loop so the recomposition
 * is scoped to this `Text` alone. The shared minute-resolution `ClockTick`
 * deliberately stays out of this path: a per-second tick there would re-query
 * the calendar every second. When [showSeconds] is off the loop ticks once per
 * minute instead of once per second, so a minute-resolution clock costs nothing.
 */
@Composable
internal fun ClockOverlay(
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    showSeconds: Boolean = true,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    motionTier: MotionTier = MotionTier.STANDARD,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val now by produceState(initialValue = LocalTime.now(clock), showSeconds) {
        while (true) {
            value = LocalTime.now(clock)
            val nowMs = clock.millis()
            // Align the next tick to the upcoming second boundary, or the upcoming
            // minute boundary when seconds are hidden (no needless 60x/min wake-ups).
            val delayMs = if (showSeconds) 1000L - nowMs % 1000 else 60_000L - nowMs % 60_000
            delay(delayMs)
        }
    }
    val formatter =
        when {
            is24Hour && showSeconds -> ClockFormatter24
            is24Hour -> ClockFormatter24NoSeconds
            showSeconds -> ClockFormatter12
            else -> ClockFormatter12NoSeconds
        }
    // Key the fade on the DISPLAYED string, not the raw LocalTime: it changes once
    // per second (or per minute when seconds are hidden), so the glass frame stays
    // put while only the time text dissolves on each tick — never a per-frame thrash.
    val timeText = now.format(formatter)
    Motion.ContentCrossfade(
        targetState = timeText,
        tier = motionTier,
        label = "clock",
        // Glass chrome + padding stay on the stable outer frame so only the time
        // text crossfades inside it, rather than dissolving the whole glass card.
        modifier =
            modifier
                .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
                .padding(
                    horizontal = FemtoDimens.OverlayPaddingHorizontal,
                    // The info cards' inner padding, not OverlayPaddingVertical:
                    // the clock tops the hero row beside the calendar / weather
                    // cards, and an equal inset keeps its numeral on their line.
                    vertical = FemtoDimens.CardPaddingCompact,
                ),
    ) { text ->
        // The clock is ambient (not the safety glance), so it shares the info
        // cards' exact hero treatment — bigNumber at Text4Xl, Normal weight —
        // rather than the speed value's heavier strong-tier heroNumeral. The
        // singleLineBox clamp plus the CardPaddingCompact inset above keep its
        // ink on the same line as the calendar day and weather temperature
        // (the shared hero-row contract; see CalendarCard.Head / WeatherCard.Head).
        val style = MaterialTheme.typography.bigNumber(
            size = FemtoDimens.Text4Xl,
            weight = MaterialTheme.typography.normalWeight,
        )
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.singleLineBox(style),
        )
    }
}

@PreviewLightDark
@Preview(name = "Clock overlay", widthDp = 240, heightDp = 80)
@Composable
private fun ClockOverlayPreview() {
    FemtoTheme {
        ClockOverlay()
    }
}
