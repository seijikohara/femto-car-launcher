package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.calendar.DateLineForm
import io.github.seijikohara.femto.data.calendar.dateLineOf
import io.github.seijikohara.femto.data.clock.SystemZoneClock
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.FitText
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.atFitFloor
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.calendarWeekday
import io.github.seijikohara.femto.ui.theme.normalWeight
import io.github.seijikohara.femto.ui.theme.singleLineBox
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Fixed-width patterns. Every form uses a leading-zero hour so the 12-hour form
// stays the same advance count as the 24-hour form; paired with tabular figures
// this keeps the time's width constant as it changes. The NoSeconds forms back
// the "show seconds = off" setting.
private val ClockFormatter24 = DateTimeFormatter.ofPattern("HH:mm:ss")
private val ClockFormatter12 = DateTimeFormatter.ofPattern("hh:mm:ss")
private val ClockFormatter24NoSeconds = DateTimeFormatter.ofPattern("HH:mm")
private val ClockFormatter12NoSeconds = DateTimeFormatter.ofPattern("hh:mm")

// The gap between the time and the date. Internal for the header test, which
// sizes a band from the text engine's own widths to pin the wording choice.
internal val HeaderGap = 16.dp

// The floor the date line shrinks to before the band tries a shorter wording
// (see DateLine): the glance size a long localized date may relax to
// (AGENTS.md#automotive-overrides).
private val DateLineFloorSize = FemtoDimens.GlanceTextSize

/**
 * Glass header of the info-card cluster: the time at the start and today's date
 * at the end, on one band.
 *
 * The header belongs to the card column, not to any card. It stays whenever the
 * cluster does — hiding the calendar or the weather card never loses the clock —
 * and it spans the column's full width, where the seconds-bearing time and the
 * date fit side by side even on the 800 dp floor geometry (a card-width slot
 * could not hold the time alone once seconds are on). With every card hidden it
 * holds the cluster's slot by itself. Moving the clock off the map pane frees
 * the map's whole top edge, and moving the date out of the calendar card frees
 * that card for the agenda alone, so hiding the card is the "date and day only"
 * view with no further setting.
 *
 * The time is the band's one hero numeral; the date is a single localized line
 * beside it ("Friday, May 1") rather than a second numeral, so the band reads as
 * "the time, and the date" instead of two competing figures. As the band narrows
 * the line takes a shorter wording before it shrinks, and folds away whole when
 * even the weekday alone will not fit (see DateLine); the time always keeps the
 * band.
 *
 * Both the time and the date come from [clock] alone — the date through
 * [dateLineOf], in the locale's own field order — which means the date shows
 * without calendar permission and never waits on the calendar snapshot.
 *
 * The header self-times with a local [produceState] loop so the recomposition
 * is scoped to it. The shared minute-resolution `ClockTick` deliberately stays
 * out of this path: a per-second tick there would re-query the calendar every
 * second. When [showSeconds] is off the loop ticks once per minute instead of
 * once per second, so a minute-resolution clock costs nothing.
 */
@Composable
internal fun DashboardHeader(
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true,
    showSeconds: Boolean = true,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    motionTier: MotionTier = MotionTier.STANDARD,
    // SystemZoneClock, not Clock.systemDefaultZone(): the latter freezes the zone
    // it was built with, and this header outlives a timezone change (a car
    // crossing a border). Read per tick, the zone follows the system within one
    // tick — the same rule ClockRepository applies to the shared minute tick.
    clock: Clock = SystemZoneClock,
    // Read through LocalLocale rather than Locale.getDefault(): the latter is not
    // observable Compose state, so the date line would not recompose when the
    // user changes the system locale mid-session (the WeatherPanel precedent).
    locale: Locale = LocalLocale.current.platformLocale,
) {
    val now by produceState(initialValue = LocalDateTime.now(clock), showSeconds) {
        while (true) {
            value = LocalDateTime.now(clock)
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
    val timeText = now.format(formatter)
    val date = now.toLocalDate()
    // Every wording the date line can take, longest first; recomputed only when
    // the day or the locale changes.
    val dateLines = remember(date, locale) { DateLineForm.entries.map { form -> dateLineOf(date, locale, form) } }
    // The clock is ambient (not the safety glance), so it shares the info cards'
    // hero treatment — bigNumber at Text4Xl, Normal weight — rather than the speed
    // value's heavier strong-tier heroNumeral.
    val heroStyle =
        MaterialTheme.typography.bigNumber(
            size = FemtoDimens.Text4Xl,
            weight = MaterialTheme.typography.normalWeight,
        )
    Row(
        modifier =
            modifier
                .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
                // The glass overlays' own inset (the speed pill's), not the cards'
                // 14 dp: the band no longer has to line up with a card head beside
                // it, and every dp of header height comes straight out of the
                // calendar / weather row below on a 512 dp-tall head unit.
                .padding(
                    horizontal = FemtoDimens.OverlayPaddingHorizontal,
                    vertical = FemtoDimens.OverlayPaddingVertical,
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Keyed on the DISPLAYED string, not the raw time: it changes once per
        // second (or per minute when seconds are hidden), so the glass frame stays
        // put while only the time text dissolves on each tick — never a per-frame
        // thrash.
        Motion.ContentCrossfade(targetState = timeText, tier = motionTier, label = "clock") { text ->
            Text(
                text = text,
                style = heroStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.singleLineBox(heroStyle),
            )
        }
        // Keyed on the date's wordings so it fades only at midnight (or on a
        // locale change), never on a time tick. The date yields to the time: it
        // takes whatever width the time leaves (weight, unfilled, so it still hugs
        // its content and SpaceBetween keeps it at the end) behind a fixed gap.
        Motion.ContentCrossfade(
            targetState = dateLines,
            tier = motionTier,
            label = "date",
            modifier = Modifier.weight(1f, fill = false).padding(start = HeaderGap),
        ) { lines ->
            DateLine(lines)
        }
    }
}

// Today's date on one line at the band's end, in the longest wording that fits
// ([lines] runs longest first: "Friday, May 1", "Fri, May 1", "Friday"). A
// wording is preferred at its design size: the first that fits unshrunk wins,
// and only when none does is the first that fits at the floor size taken and
// shrunk to it (FitText) — a shorter wording at full size reads faster at a
// glance than a longer one made small. When even the weekday alone does not
// fit at the floor, the date folds away whole and the time keeps the band:
// never an ellipsis (a "Fri…" says nothing at a glance). The widths come from
// the text engine itself (TextMeasurer, the same layout the text gets), so the
// choice follows the real label widths in every locale rather than a dp guess,
// and it is made here, in composition, so a folded date is not merely unplaced
// but absent: nothing for TalkBack to read, nothing for a test to find.
@Composable
private fun DateLine(
    lines: List<String>,
    modifier: Modifier = Modifier,
) = BoxWithConstraints(modifier = modifier) {
    // One step below the panel's weekday (TextLg), landing on the body floor:
    // glance metadata beside the hero numeral.
    val style = MaterialTheme.typography.calendarWeekday(FemtoDimens.MinBodyTextSize)
    val measurer = rememberTextMeasurer()
    val floorStyle = style.atFitFloor(DateLineFloorSize)
    // Unbounded width (a preview) never folds.
    val fits = { text: String, at: TextStyle ->
        !constraints.hasBoundedWidth || measurer.measure(text, at).size.width <= constraints.maxWidth
    }
    val shown = lines.firstOrNull { fits(it, style) } ?: lines.firstOrNull { fits(it, floorStyle) }
    if (shown != null) {
        FitText(
            text = shown,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            minFontSize = DateLineFloorSize,
        )
    }
}

// The 5:3 head unit's card column inner width (853 x 512 dp) — the header's
// binding width; the floor geometry (800 x 480 dp) is 21 dp narrower. The
// second geometry is the LARGE display scale on that head unit, where the date
// takes a shorter wording beside the seconds-bearing time.
@PreviewLightDark
@PreviewTextStress
@Preview(name = "Dashboard header", widthDp = 329, heightDp = 80)
@Preview(name = "Dashboard header - LARGE column", widthDp = 249, heightDp = 80)
@Composable
private fun DashboardHeaderPreview() {
    FemtoTheme {
        DashboardHeader(
            modifier = Modifier.fillMaxWidth(),
            clock = Clock.fixed(Instant.parse("2026-05-01T10:08:00Z"), ZoneOffset.UTC),
        )
    }
}
