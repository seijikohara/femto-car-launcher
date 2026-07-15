package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.calendar.DayCell
import io.github.seijikohara.femto.data.calendar.EventItem
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.calendarWeekday
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.eyebrow
import io.github.seijikohara.femto.ui.theme.sectionLabel
import java.time.LocalDate
import java.time.LocalTime

/**
 * Full-screen calendar panel: a multi-day agenda over the live map.
 *
 * Unlike the compact card — which only ever lists days that already carry an
 * event, so a glance never scrolls past blank days — the panel has room to
 * spend on rhythm: it walks [CalendarSnapshot.days] itself for up to
 * [CalendarPanelWindowDays] days (today, every day with an event, and the free
 * days between them) instead of reusing [CalendarSnapshot.visibleDays], so the
 * agenda reads as a real look-ahead rather than a handful of sparse rows
 * floating in an otherwise-empty sheet. Every event carries its end time and
 * location, both panel-only fields the compact card omits.
 *
 * A landscape panel (wider than tall — the reference head unit and beyond)
 * splits the agenda into two load-balanced columns so the days use the full
 * width instead of a single left-packed column; a portrait panel (the
 * phone-mount case) keeps one full-width column. Either way, each column is
 * capped to whatever [FitWholeRows] fits its height, dropping only whole
 * trailing days rather than clipping mid-event.
 */
@Composable
internal fun CalendarPanel(
    snapshot: CalendarSnapshot,
    is24Hour: Boolean,
    onOpenExternal: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    motionTier: MotionTier = MotionTier.STANDARD,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = MaximizePanel(
    title = stringResource(R.string.calendar_title),
    onClose = onClose,
    onOpenExternal = onOpenExternal,
    openExternalLabel = stringResource(R.string.calendar_open_app),
    modifier = modifier,
    hazeState = hazeState,
    glassConfig = glassConfig,
) {
    val panelDays = remember(snapshot) { snapshot.days.take(CALENDAR_PANEL_WINDOW_DAYS) }
    // Landscape reads wider than tall (the reference head unit is 853x512);
    // portrait (the phone-mount case, 412x915) keeps a single full-width
    // column instead of splitting an already-narrow column in two.
    if (maxHeight > maxWidth) {
        AgendaColumn(
            days = panelDays,
            today = snapshot.today,
            is24Hour = is24Hour,
            showColorDots = snapshot.multipleCalendarsVisible,
            motionTier = motionTier,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        val (left, right) = remember(panelDays) { panelDays.splitAgendaColumns() }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
        ) {
            AgendaColumn(
                left,
                snapshot.today,
                is24Hour,
                snapshot.multipleCalendarsVisible,
                motionTier,
                Modifier.weight(1f).fillMaxHeight(),
            )
            AgendaColumn(
                right,
                snapshot.today,
                is24Hour,
                snapshot.multipleCalendarsVisible,
                motionTier,
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

// Two weeks is enough room to catch every upcoming event without turning a
// quiet month into a scroll of blank "No events" rows; FitWholeRows caps the
// rendered count further to whatever the panel's actual height admits.
private const val CALENDAR_PANEL_WINDOW_DAYS = 14

// Chronological split: earlier days fill the left column, later days the
// right, so reading the left column top-to-bottom then the right stays in
// date order — the scannability an agenda lives on (an interleaving
// load-balance would break that). The cut still balances height: days fill the
// left until its running weight (event count as a cheap proxy for row height)
// passes half the total, so a run of busy early days does not tower over a
// light right column. At least one day always goes right.
private fun List<DayCell>.splitAgendaColumns(): Pair<List<DayCell>, List<DayCell>> {
    val total = sumOf { it.events.size.coerceAtLeast(1) }
    val left = mutableListOf<DayCell>()
    val right = mutableListOf<DayCell>()
    var leftWeight = 0
    forEach { day ->
        if (leftWeight * 2 < total && left.size < size - 1) {
            left += day
            leftWeight += day.events.size.coerceAtLeast(1)
        } else {
            right += day
        }
    }
    return left to right
}

// One agenda column: every day is a single FitWholeRows child (its leading
// divider travels with it), so a day dropped for lack of height never leaves
// an orphaned rule behind. mandatoryCount = 1 keeps the column's nearest day
// visible even on a panel too short for it to fully fit.
@Composable
private fun AgendaColumn(
    days: List<DayCell>,
    today: LocalDate,
    is24Hour: Boolean,
    showColorDots: Boolean,
    motionTier: MotionTier,
    modifier: Modifier = Modifier,
) = FitWholeRows(
    modifier = modifier,
    verticalGap = FemtoDimens.CardSectionGap,
    mandatoryCount = 1,
) {
    days.forEachIndexed { index, day ->
        AgendaDay(
            day = day,
            today = today,
            isFirst = index == 0,
            is24Hour = is24Hour,
            showColorDots = showColorDots,
            motionTier = motionTier,
        )
    }
}

// One day of the agenda: a date gutter + the day's events (untruncated title,
// time range, optional location). Today's gutter is tinted primary. A
// hairline divider precedes every day but the column's first, so day groups
// read as distinct beats instead of one continuous run of events.
@Composable
private fun AgendaDay(
    day: DayCell,
    today: LocalDate,
    isFirst: Boolean,
    is24Hour: Boolean,
    showColorDots: Boolean,
    motionTier: MotionTier,
    modifier: Modifier = Modifier,
) = Motion.ContentCrossfade(
    // A day's block dissolves when its events change on a refresh (DayCell
    // equality is structural). isFirst is positional, so the leading divider
    // stays consistent across the swap; isToday derives from the faded day so the
    // outgoing frame stays internally consistent. FitWholeRows still measures one
    // child per day (the Crossfade's single root), so its fit-to-height count is
    // unaffected.
    targetState = day,
    tier = motionTier,
    label = "agendaDay",
    modifier = modifier,
) { current ->
    val isToday = current.date == today
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!isFirst) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FemtoDimens.DividerAlpha))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val accent = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            val weekdayColor =
                if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier.width(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = current.weekdayLetter.take(3).uppercase(),
                    style = MaterialTheme.typography.sectionLabel(12),
                    color = weekdayColor,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "${current.date.dayOfMonth}",
                    style = MaterialTheme.typography.calendarWeekday(),
                    color = accent,
                    maxLines = 1,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (current.events.isEmpty()) {
                    Text(
                        text = stringResource(R.string.calendar_no_events),
                        style = MaterialTheme.typography.cardMeta(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else {
                    current.events.forEach { event ->
                        AgendaEvent(event = event, is24Hour = is24Hour, showColorDots = showColorDots)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaEvent(
    event: EventItem,
    is24Hour: Boolean,
    showColorDots: Boolean,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    // Indent the non-title lines past the dot gutter so every line shares the
    // title's left edge; the dot leads the title row, centered on the title line.
    val gutterIndent =
        if (showColorDots) {
            Modifier.padding(start = FemtoDimens.CalendarDotGutter)
        } else {
            Modifier
        }
    Text(
        text = eventTimeRange(event, is24Hour),
        modifier = gutterIndent,
        style = MaterialTheme.typography.eyebrow(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CalendarDotGap),
        // Match the card: center the dot on the title line rather than the whole
        // stacked lines, so it marks the event at its title.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showColorDots) {
            CalendarColorDot(color = event.color)
        }
        Text(
            text = event.title,
            modifier = Modifier.weight(1f),
            // titleLarge (24sp) is above the body floor; the panel has room to wrap
            // the whole title rather than ellipsize like the compact card.
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    event.location?.takeUnless { it.isBlank() }?.let { location ->
        Text(
            text = location,
            modifier = gutterIndent,
            style = MaterialTheme.typography.cardMeta(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// "10:30 – 11:00", "10:30" (no end), or "All day".
@Composable
private fun eventTimeRange(
    event: EventItem,
    is24Hour: Boolean,
): String {
    val formatter = clockTimeFormatter(is24Hour)
    val start = event.time ?: return stringResource(R.string.calendar_all_day)
    return event.endTime?.let { "${start.format(formatter)} – ${it.format(formatter)}" }
        ?: start.format(formatter)
}

@PreviewLightDark
@PreviewTextStress
@androidx.compose.ui.tooling.preview.Preview(name = "Calendar panel · head unit", widthDp = 805, heightDp = 400)
@androidx.compose.ui.tooling.preview.Preview(name = "Calendar panel · portrait", widthDp = 364, heightDp = 700)
@Composable
private fun CalendarPanelPreview() {
    FemtoTheme {
        CalendarPanel(
            snapshot =
                CalendarSnapshot(
                    today = LocalDate.of(2026, 5, 1),
                    weekday = "Friday",
                    monthLabel = "May 2026",
                    days =
                        listOf(
                            DayCell(
                                LocalDate.of(2026, 5, 1),
                                "Fri",
                                listOf(
                                    EventItem(
                                        LocalTime.of(10, 30),
                                        "Team standup",
                                        endTime = LocalTime.of(11, 0),
                                        location = "Room 4",
                                    ),
                                    EventItem(null, "Company holiday"),
                                ),
                            ),
                            DayCell(LocalDate.of(2026, 5, 2), "Sat", emptyList()),
                            DayCell(
                                LocalDate.of(2026, 5, 3),
                                "Sun",
                                listOf(
                                    EventItem(
                                        LocalTime.of(9, 0),
                                        "Brunch with the extended family",
                                        endTime = LocalTime.of(11, 0),
                                    ),
                                ),
                            ),
                            DayCell(LocalDate.of(2026, 5, 4), "Mon", emptyList()),
                        ),
                    hasCalendarAccess = true,
                ),
            is24Hour = true,
            onOpenExternal = {},
            onClose = {},
        )
    }
}
