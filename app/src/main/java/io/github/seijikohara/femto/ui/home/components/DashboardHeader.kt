package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.calendar.monthLabelOf
import io.github.seijikohara.femto.data.calendar.weekdayLabelOf
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.FitText
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.bigNumber
import io.github.seijikohara.femto.ui.theme.calendarWeekday
import io.github.seijikohara.femto.ui.theme.eyebrowTight
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

// Gap between the day numeral and its weekday / month block.
private val DateGroupGap = 16.dp

// Minimum gap between the time and the date group.
private val HeaderGap = 16.dp

/**
 * Glass header of the info-card cluster: the time at the start and today's date
 * at the end, on one hero band.
 *
 * The header belongs to the card column, not to any card. It stays whenever the
 * cluster does — hiding the calendar or the weather card never loses the clock —
 * and it spans the column's full width, where the seconds-bearing time and the
 * day numeral with its weekday / month block fit side by side even on the 800 dp
 * floor geometry (a card-width slot could not hold the time alone once seconds
 * are on). With every card hidden it holds the cluster's slot by itself. Moving
 * the clock off the map pane frees the map's whole top edge, and moving the date
 * out of the calendar card frees that card for the agenda alone, so hiding the
 * card is the "date and day only" view with no further setting.
 *
 * Both the time and the date come from [clock] alone — the date through the
 * calendar's own label helpers ([weekdayLabelOf] / [monthLabelOf]) so the wording
 * matches the agenda's — which means the date shows without calendar permission
 * and never waits on the calendar snapshot.
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
    clock: Clock = Clock.systemDefaultZone(),
    locale: Locale = Locale.getDefault(),
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
    val headerDate =
        remember(date, locale) {
            HeaderDate(date.dayOfMonth, weekdayLabelOf(date, locale), monthLabelOf(date, locale))
        }
    // The clock is ambient (not the safety glance), so it shares the info cards'
    // hero treatment — bigNumber at Text4Xl, Normal weight — rather than the speed
    // value's heavier strong-tier heroNumeral; the day numeral uses the same style
    // so both sit on one digit band.
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
        // Keyed on the date's displayed identity so it fades only at midnight (or
        // on a locale change), never on a time tick. The date yields to the time:
        // it takes whatever width the time leaves (weight, unfilled, so it still
        // hugs its content and SpaceBetween keeps it at the end) behind a fixed
        // gap, and its weekday / month shrink to fit when a seconds-bearing time
        // on the head-unit column leaves them short.
        Motion.ContentCrossfade(
            targetState = headerDate,
            tier = motionTier,
            label = "date",
            modifier = Modifier.weight(1f, fill = false).padding(start = HeaderGap),
        ) { shown ->
            DateGroup(shown, heroStyle)
        }
    }
}

// The date's displayed identity: the day numeral, weekday, and month label. All
// three (rather than the bare date) so the crossfade's outgoing frame renders a
// fully consistent old date while the incoming one renders the new.
private data class HeaderDate(
    val day: Int,
    val weekday: String,
    val month: String,
)

@Composable
private fun DateGroup(
    date: HeaderDate,
    heroStyle: TextStyle,
) = Row(horizontalArrangement = Arrangement.spacedBy(DateGroupGap)) {
    // Clamped like the time: platform font padding otherwise inflates the
    // measured box well past the nominal line box and drops the ink off the band.
    Text(
        text = "${date.day}",
        style = heroStyle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier.singleLineBox(heroStyle),
    )
    Column(
        // The weekday + month block shares the day numeral's line-box slot, so it
        // spans the same band as the numerals instead of floating above it
        // (top-aligned) or riding the numeral's baseline below its centre. The two
        // lines are sized (weekday one scale step down, month with tight leading)
        // to keep their combined ink inside the digit height.
        modifier = Modifier.singleLineBox(heroStyle),
    ) {
        FitText(
            text = date.weekday,
            // One step below the panel's weekday (TextLg), landing on the body
            // floor: glance metadata beside the numerals, sized so the two-line
            // block fits the digit band. May still relax to GlanceTextSize for a
            // long localized name at a large font scale
            // (AGENTS.md#automotive-overrides).
            style = MaterialTheme.typography.calendarWeekday(FemtoDimens.MinBodyTextSize),
            color = MaterialTheme.colorScheme.onSurface,
            minFontSize = FemtoDimens.GlanceTextSize,
        )
        FitText(
            text = date.month.uppercase(),
            // Tight leading, so the month packs directly under the weekday inside
            // the digit band instead of adding a padded line box below it. Shrinks
            // (to the agenda gutter's floor) before it ellipsizes, so "SEPTEMBER
            // 2026" survives beside a seconds-bearing time on the head unit.
            style = MaterialTheme.typography.eyebrowTight(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minFontSize = FemtoDimens.TextXs,
        )
    }
}

// The 5:3 head unit's card column inner width (853 x 512 dp) — the header's
// binding width; the floor geometry (800 x 480 dp) is 21 dp narrower.
@PreviewLightDark
@Preview(name = "Dashboard header", widthDp = 329, heightDp = 80)
@Composable
private fun DashboardHeaderPreview() {
    FemtoTheme {
        DashboardHeader(
            modifier = Modifier.fillMaxWidth(),
            clock = Clock.fixed(Instant.parse("2026-05-01T10:08:00Z"), ZoneOffset.UTC),
            locale = Locale.US,
        )
    }
}
